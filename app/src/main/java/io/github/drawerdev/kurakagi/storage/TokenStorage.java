package io.github.drawerdev.kurakagi.storage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Storage and SD Card import/export manager for Kurakagi accounts.
 * Supports plain JSON and master-password protected AES-CBC-PKCS5Padding encryption.
 * Fully compatible with Android 2.1 (API 7).
 */
public class TokenStorage {

    /** v1 (legacy, read-only): magic | salt | iv | AES-128-CBC ciphertext. PBKDF2-HMAC-SHA1, 10,000 rounds. No MAC. */
    // Left over from the prototype's name; kept as is so existing backups still read.
    private static final byte[] MAGIC_HEADER = asciiBytes("RETROAUTH_ENC1");
    /**
     * v2 (written by this version): magic | iterations (4 bytes, big endian) | salt | iv | ciphertext | mac.
     * PBKDF2-HMAC-SHA256 gives a 32-byte master key. Two subkeys are split from it with HMAC-SHA256:
     * an AES-256-CBC key and an HMAC-SHA256 key. The MAC covers everything before it (encrypt-then-MAC)
     * and is checked before decrypting. Only primitives present since Android 2.1 are used.
     */
    private static final byte[] MAGIC_HEADER_V2 = asciiBytes("KURAKAGI__ENC2");
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 16;
    private static final int KEY_BYTES = 16; // v1: AES-128
    private static final int PBKDF2_ITERATIONS = 10000; // v1
    private static final int MAC_BYTES = 32;
    /** Default v2 rounds. Stored in the file, so it can be raised later without breaking old backups. */
    public static final int DEFAULT_ITERATIONS_V2 = 60000;
    private static final int MIN_ITERATIONS_V2 = 10000;
    private static final int MAX_ITERATIONS_V2 = 5000000;

    private static byte[] asciiBytes(String s) {
        byte[] out = new byte[s.length()];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) s.charAt(i);
        }
        return out;
    }

    private final List<Account> accounts;
    private final Object lock = new Object();

    public TokenStorage() {
        this.accounts = new ArrayList<Account>();
    }

    public void add(Account account) {
        if (account == null) return;
        synchronized (lock) {
            accounts.add(account);
        }
    }

    public boolean update(Account account) {
        if (account == null || account.getId() == null) return false;
        synchronized (lock) {
            for (int i = 0; i < accounts.size(); i++) {
                Account existing = accounts.get(i);
                if (account.getId().equals(existing.getId())) {
                    accounts.set(i, account);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean remove(String id) {
        if (id == null || id.length() == 0) return false;
        synchronized (lock) {
            for (int i = 0; i < accounts.size(); i++) {
                Account existing = accounts.get(i);
                if (id.equals(existing.getId())) {
                    accounts.remove(i);
                    return true;
                }
            }
        }
        return false;
    }

    public Account get(String id) {
        if (id == null || id.length() == 0) return null;
        synchronized (lock) {
            for (int i = 0; i < accounts.size(); i++) {
                Account existing = accounts.get(i);
                if (id.equals(existing.getId())) {
                    return existing;
                }
            }
        }
        return null;
    }

    public List<Account> getAll() {
        synchronized (lock) {
            return new ArrayList<Account>(accounts);
        }
    }

    public int size() {
        synchronized (lock) {
            return accounts.size();
        }
    }

    public void clear() {
        synchronized (lock) {
            accounts.clear();
        }
    }

    // --- JSON Serialization ---

    /**
     * Serializes a list of Accounts into a JSON array string.
     */
    public static String exportToJson(List<Account> accountList) throws JSONException {
        if (accountList == null) {
            return "[]";
        }
        JSONArray array = new JSONArray();
        for (int i = 0; i < accountList.size(); i++) {
            Account account = accountList.get(i);
            if (account != null) {
                array.put(account.toJson());
            }
        }
        return array.toString();
    }

    /**
     * Deserializes a list of Accounts from a JSON array string.
     */
    public static List<Account> importFromJson(String jsonString) throws JSONException {
        if (jsonString == null || jsonString.length() == 0) {
            return Collections.emptyList();
        }
        JSONArray array = new JSONArray(jsonString);
        List<Account> result = new ArrayList<Account>();
        int len = array.length();
        for (int i = 0; i < len; i++) {
            JSONObject obj = array.getJSONObject(i);
            Account account = Account.fromJson(obj);
            if (account != null) {
                result.add(account);
            }
        }
        return result;
    }

    // --- Plain File I/O ---

    public static void exportToFile(File file, List<Account> accountList) throws IOException, JSONException {
        String json = exportToJson(accountList);
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
            writer.write(json);
            writer.flush();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    public static List<Account> importFromFile(File file) throws IOException, JSONException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            StringBuffer sb = new StringBuffer();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return importFromJson(sb.toString().trim());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    // --- Master Password PBKDF2 / AES-CBC Encryption ---

    /**
     * Encrypts accounts with the master password (format v2, default rounds).
     */
    public static byte[] exportEncrypted(List<Account> accountList, char[] masterPassword)
            throws GeneralSecurityException, JSONException {
        return exportEncrypted(accountList, masterPassword, DEFAULT_ITERATIONS_V2);
    }

    /**
     * Encrypts accounts with the master password (format v2): PBKDF2-HMAC-SHA256,
     * AES-256-CBC, then HMAC-SHA256 over the header and ciphertext.
     */
    public static byte[] exportEncrypted(List<Account> accountList, char[] masterPassword, int iterations)
            throws GeneralSecurityException, JSONException {
        if (masterPassword == null || masterPassword.length == 0) {
            throw new IllegalArgumentException("Master password must not be empty");
        }
        if (iterations < MIN_ITERATIONS_V2 || iterations > MAX_ITERATIONS_V2) {
            throw new IllegalArgumentException("Iterations out of range: " + iterations);
        }

        String json = exportToJson(accountList);
        byte[] plaintext;
        try {
            plaintext = json.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 not supported", e);
        }

        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);

        byte[] master = deriveKeyPBKDF2("HmacSHA256", masterPassword, salt, iterations, 32);
        byte[] encKey = hmacSha256(master, asciiBytes("kurakagi-backup-enc"));
        byte[] macKey = hmacSha256(master, asciiBytes("kurakagi-backup-mac"));
        Arrays.fill(master, (byte) 0);

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encKey, "AES"), new IvParameterSpec(iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            Arrays.fill(plaintext, (byte) 0);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                out.write(MAGIC_HEADER_V2);
                out.write(intToBytes(iterations));
                out.write(salt);
                out.write(iv);
                out.write(ciphertext);
            } catch (IOException e) {
                throw new RuntimeException("ByteArrayOutputStream write failed", e);
            }
            byte[] body = out.toByteArray();
            byte[] mac = hmacSha256(macKey, body);

            byte[] result = new byte[body.length + MAC_BYTES];
            System.arraycopy(body, 0, result, 0, body.length);
            System.arraycopy(mac, 0, result, body.length, MAC_BYTES);
            return result;
        } finally {
            Arrays.fill(encKey, (byte) 0);
            Arrays.fill(macKey, (byte) 0);
        }
    }

    /** True if the bytes start with a Kurakagi encrypted-backup header (v1 or v2). */
    public static boolean isEncryptedBackup(byte[] data) {
        return startsWith(data, MAGIC_HEADER_V2) || startsWith(data, MAGIC_HEADER);
    }

    /** True if the bytes are a legacy v1 backup (no tamper check). */
    public static boolean isLegacyEncryptedBackup(byte[] data) {
        return startsWith(data, MAGIC_HEADER);
    }

    /**
     * Decrypts an encrypted backup (v2, or legacy v1) with the master password.
     * A wrong password and a modified file both throw GeneralSecurityException.
     */
    public static List<Account> importEncrypted(byte[] encryptedData, char[] masterPassword)
            throws GeneralSecurityException, JSONException {
        if (startsWith(encryptedData, MAGIC_HEADER_V2)) {
            return importEncryptedV2(encryptedData, masterPassword);
        }
        return importEncryptedV1(encryptedData, masterPassword);
    }

    private static List<Account> importEncryptedV2(byte[] data, char[] masterPassword)
            throws GeneralSecurityException, JSONException {
        if (masterPassword == null || masterPassword.length == 0) {
            throw new IllegalArgumentException("Master password must not be empty");
        }
        int headerLen = MAGIC_HEADER_V2.length + 4 + SALT_BYTES + IV_BYTES;
        // at least one AES block of ciphertext
        if (data.length < headerLen + 16 + MAC_BYTES) {
            throw new GeneralSecurityException("Invalid backup format: file is truncated");
        }
        int pos = MAGIC_HEADER_V2.length;
        int iterations = ((data[pos] & 0xFF) << 24) | ((data[pos + 1] & 0xFF) << 16)
                | ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
        if (iterations < MIN_ITERATIONS_V2 || iterations > MAX_ITERATIONS_V2) {
            throw new GeneralSecurityException("Invalid backup format: bad iteration count");
        }
        pos += 4;
        byte[] salt = new byte[SALT_BYTES];
        System.arraycopy(data, pos, salt, 0, SALT_BYTES);
        pos += SALT_BYTES;
        byte[] iv = new byte[IV_BYTES];
        System.arraycopy(data, pos, iv, 0, IV_BYTES);

        int bodyLen = data.length - MAC_BYTES;
        byte[] body = new byte[bodyLen];
        System.arraycopy(data, 0, body, 0, bodyLen);
        byte[] storedMac = new byte[MAC_BYTES];
        System.arraycopy(data, bodyLen, storedMac, 0, MAC_BYTES);

        byte[] master = deriveKeyPBKDF2("HmacSHA256", masterPassword, salt, iterations, 32);
        byte[] encKey = hmacSha256(master, asciiBytes("kurakagi-backup-enc"));
        byte[] macKey = hmacSha256(master, asciiBytes("kurakagi-backup-mac"));
        Arrays.fill(master, (byte) 0);
        try {
            byte[] expectedMac = hmacSha256(macKey, body);
            if (!constantTimeEquals(expectedMac, storedMac)) {
                throw new GeneralSecurityException("Wrong password, or the backup file was modified");
            }
            int cipherLen = bodyLen - headerLen;
            byte[] ciphertext = new byte[cipherLen];
            System.arraycopy(body, headerLen, ciphertext, 0, cipherLen);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encKey, "AES"), new IvParameterSpec(iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            String json;
            try {
                json = new String(plaintext, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException("UTF-8 not supported", e);
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
            return importFromJson(json);
        } finally {
            Arrays.fill(encKey, (byte) 0);
            Arrays.fill(macKey, (byte) 0);
        }
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] intToBytes(int v) {
        return new byte[] { (byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v };
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data == null || data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    /**
     * Legacy v1 reader (AES-128-CBC, PBKDF2-HMAC-SHA1 10,000 rounds, no MAC).
     * Kept only so that backups made by 1.0.0 can still be restored.
     */
    private static List<Account> importEncryptedV1(byte[] encryptedData, char[] masterPassword)
            throws GeneralSecurityException, JSONException {
        if (encryptedData == null || encryptedData.length == 0) {
            throw new IllegalArgumentException("Encrypted data must not be empty");
        }
        if (masterPassword == null || masterPassword.length == 0) {
            throw new IllegalArgumentException("Master password must not be empty");
        }

        int headerLen = MAGIC_HEADER.length;
        int minLen = headerLen + SALT_BYTES + IV_BYTES;
        if (encryptedData.length < minLen) {
            throw new GeneralSecurityException("Invalid backup format: file is truncated");
        }

        // Validate magic header
        for (int i = 0; i < headerLen; i++) {
            if (encryptedData[i] != MAGIC_HEADER[i]) {
                throw new GeneralSecurityException("Invalid backup format: magic header mismatch");
            }
        }

        byte[] salt = new byte[SALT_BYTES];
        System.arraycopy(encryptedData, headerLen, salt, 0, SALT_BYTES);

        byte[] iv = new byte[IV_BYTES];
        System.arraycopy(encryptedData, headerLen + SALT_BYTES, iv, 0, IV_BYTES);

        int cipherOffset = headerLen + SALT_BYTES + IV_BYTES;
        int cipherLen = encryptedData.length - cipherOffset;
        byte[] ciphertext = new byte[cipherLen];
        System.arraycopy(encryptedData, cipherOffset, ciphertext, 0, cipherLen);

        byte[] keyBytes = deriveKeyPBKDF2(masterPassword, salt, PBKDF2_ITERATIONS, KEY_BYTES);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

        byte[] plaintext = cipher.doFinal(ciphertext);

        String json;
        try {
            json = new String(plaintext, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 not supported", e);
        }

        return importFromJson(json);
    }

    public static void exportEncryptedToFile(File file, List<Account> accountList, char[] masterPassword)
            throws IOException, GeneralSecurityException, JSONException {
        byte[] encrypted = exportEncrypted(accountList, masterPassword);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(encrypted);
            fos.flush();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    public static List<Account> importEncryptedFromFile(File file, char[] masterPassword)
            throws IOException, GeneralSecurityException, JSONException {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int read;
            while ((read = fis.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            return importEncrypted(baos.toByteArray(), masterPassword);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    /**
     * PBKDF2 with HMAC-SHA1 (RFC 2898). Used by the legacy v1 format.
     */
    public static byte[] deriveKeyPBKDF2(char[] password, byte[] salt, int iterations, int keyByteLength)
            throws GeneralSecurityException {
        return deriveKeyPBKDF2("HmacSHA1", password, salt, iterations, keyByteLength);
    }

    /**
     * Pure Java PBKDF2 (RFC 2898) over the given HMAC ("HmacSHA1" or "HmacSHA256").
     * Written by hand because SecretKeyFactory PBKDF2 is missing on old Android.
     */
    public static byte[] deriveKeyPBKDF2(String macAlgorithm, char[] password, byte[] salt, int iterations,
            int keyByteLength) throws GeneralSecurityException {
        byte[] passwordBytes;
        try {
            passwordBytes = new String(password).getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 not supported", e);
        }

        Mac mac = Mac.getInstance(macAlgorithm);
        SecretKeySpec pKey = new SecretKeySpec(passwordBytes, macAlgorithm);
        mac.init(pKey);
        Arrays.fill(passwordBytes, (byte) 0);

        int hLen = mac.getMacLength(); // 20 for SHA-1, 32 for SHA-256
        int blocks = (keyByteLength + hLen - 1) / hLen;
        byte[] derivedKey = new byte[keyByteLength];

        byte[] u = new byte[hLen];
        byte[] t = new byte[hLen];
        byte[] saltAndIndex = new byte[salt.length + 4];
        System.arraycopy(salt, 0, saltAndIndex, 0, salt.length);

        int destPos = 0;
        for (int block = 1; block <= blocks; block++) {
            saltAndIndex[salt.length] = (byte) ((block >>> 24) & 0xFF);
            saltAndIndex[salt.length + 1] = (byte) ((block >>> 16) & 0xFF);
            saltAndIndex[salt.length + 2] = (byte) ((block >>> 8) & 0xFF);
            saltAndIndex[salt.length + 3] = (byte) (block & 0xFF);

            mac.reset();
            u = mac.doFinal(saltAndIndex);
            System.arraycopy(u, 0, t, 0, hLen);

            for (int iter = 1; iter < iterations; iter++) {
                mac.reset();
                u = mac.doFinal(u);
                for (int k = 0; k < hLen; k++) {
                    t[k] ^= u[k];
                }
            }

            int copyLen = (destPos + hLen > keyByteLength) ? (keyByteLength - destPos) : hLen;
            System.arraycopy(t, 0, derivedKey, destPos, copyLen);
            destPos += copyLen;
        }

        return derivedKey;
    }
}
