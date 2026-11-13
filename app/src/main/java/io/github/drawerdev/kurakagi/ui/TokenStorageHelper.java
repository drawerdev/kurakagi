package io.github.drawerdev.kurakagi.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import io.github.drawerdev.kurakagi.storage.Account;
import io.github.drawerdev.kurakagi.storage.TokenStorage;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Android Context, SharedPreferences, SD カードと TokenStorage を連携させる UI ヘルパー。
 * Android 2.1 (API 7) 互換。
 */
public final class TokenStorageHelper {

    private static final String PREF_NAME = "kurakagi_tokens_pref";
    private static final String KEY_ACCOUNTS_JSON = "accounts_json";
    private static final String BACKUP_FOLDER_NAME = "Kurakagi";

    private TokenStorageHelper() {
    }

    public static synchronized List<Account> loadAccounts(Context context) {
        if (context == null) return new ArrayList<Account>();
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_ACCOUNTS_JSON, "[]");
        try {
            return TokenStorage.importFromJson(json);
        } catch (JSONException e) {
            return new ArrayList<Account>();
        }
    }

    public static synchronized void saveAccounts(Context context, List<Account> accounts) {
        if (context == null) return;
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        try {
            String json = TokenStorage.exportToJson(accounts);
            prefs.edit().putString(KEY_ACCOUNTS_JSON, json).commit();
        } catch (JSONException ignored) {
        }
    }

    public static synchronized void addAccount(Context context, Account account) {
        if (context == null || account == null) return;
        List<Account> accounts = loadAccounts(context);
        accounts.add(account);
        saveAccounts(context, accounts);
    }

    public static synchronized boolean deleteAccount(Context context, String accountId) {
        if (context == null || accountId == null || accountId.length() == 0) return false;
        List<Account> accounts = loadAccounts(context);
        boolean removed = false;
        for (int i = 0; i < accounts.size(); i++) {
            if (accountId.equals(accounts.get(i).getId())) {
                accounts.remove(i);
                removed = true;
                break;
            }
        }
        if (removed) {
            saveAccounts(context, accounts);
        }
        return removed;
    }

    public static synchronized Account findAccountById(Context context, String accountId) {
        if (context == null || accountId == null || accountId.length() == 0) return null;
        List<Account> accounts = loadAccounts(context);
        for (int i = 0; i < accounts.size(); i++) {
            Account acc = accounts.get(i);
            if (accountId.equals(acc.getId())) {
                return acc;
            }
        }
        return null;
    }

    public static File getBackupDirectory() {
        File sdcard = Environment.getExternalStorageDirectory();
        if (sdcard == null) return null;
        File dir = new File(sdcard, BACKUP_FOLDER_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** Largest backup file we will read. Real backups are a few KB. */
    public static final int MAX_BACKUP_BYTES = 2 * 1024 * 1024;

    /** Suggested file name for a new backup. */
    public static String newBackupFileName() {
        return "kurakagi_backup_" + System.currentTimeMillis() + ".enc";
    }

    /**
     * Builds an encrypted backup of every account. A password is required:
     * this app never writes secrets to shared storage in plain text.
     */
    public static byte[] buildEncryptedBackup(Context context, char[] masterPassword) throws Exception {
        if (masterPassword == null || masterPassword.length == 0) {
            throw new IllegalArgumentException("A password is required for backups");
        }
        return TokenStorage.exportEncrypted(loadAccounts(context), masterPassword);
    }

    /** Android 4.3 and older: writes the (already encrypted) backup into /sdcard/Kurakagi/. */
    public static File writeBackupToSdCard(byte[] encrypted) throws IOException {
        File dir = getBackupDirectory();
        if (dir == null || !dir.exists()) {
            throw new IOException("SD card is not available or mounted");
        }
        File target = new File(dir, newBackupFileName());
        OutputStream out = new FileOutputStream(target);
        try {
            out.write(encrypted);
            out.flush();
        } finally {
            out.close();
        }
        return target;
    }

    /** Reads a whole stream, refusing anything larger than MAX_BACKUP_BYTES. */
    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        int total = 0;
        try {
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > MAX_BACKUP_BYTES) {
                    throw new IOException("File is too large to be a Kurakagi backup");
                }
                out.write(buf, 0, n);
            }
        } finally {
            in.close();
        }
        return out.toByteArray();
    }

    /** True if the backup needs a password (encrypted). Plain JSON from 1.0.0 does not. */
    public static boolean needsPassword(byte[] data) {
        return TokenStorage.isEncryptedBackup(data);
    }

    /**
     * Restores accounts from backup bytes and merges them (skipping IDs already present).
     * Encrypted backups need the password; plain JSON files written by 1.0.0 are still accepted.
     */
    public static synchronized int importBackupBytes(Context context, byte[] data, char[] masterPassword)
            throws Exception {
        if (data == null || data.length == 0) {
            throw new IOException("Backup file is empty");
        }
        List<Account> imported;
        if (TokenStorage.isEncryptedBackup(data)) {
            imported = TokenStorage.importEncrypted(data, masterPassword);
        } else {
            imported = TokenStorage.importFromJson(new String(data, "UTF-8").trim());
        }

        if (imported != null && imported.size() > 0) {
            List<Account> current = loadAccounts(context);
            // Merge without duplicates by ID
            for (int i = 0; i < imported.size(); i++) {
                Account imp = imported.get(i);
                boolean exists = false;
                for (int j = 0; j < current.size(); j++) {
                    if (imp.getId().equals(current.get(j).getId())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    current.add(imp);
                }
            }
            saveAccounts(context, current);
            return imported.size();
        }
        return 0;
    }
}
