package io.github.drawerdev.kurakagi;

import io.github.drawerdev.kurakagi.core.Base32;
import io.github.drawerdev.kurakagi.core.OtpUriParser;
import io.github.drawerdev.kurakagi.core.TotpCore;
import io.github.drawerdev.kurakagi.net.SntpClient;
import io.github.drawerdev.kurakagi.qr.QrEncoder;
import io.github.drawerdev.kurakagi.qr.QrMatrix;
import io.github.drawerdev.kurakagi.qr.QrYuvDecoder;
import io.github.drawerdev.kurakagi.storage.Account;
import io.github.drawerdev.kurakagi.storage.TokenStorage;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

/**
 * Self-contained test runner executable via standard java command.
 * Validates all core cryptography, RFC compliance, storage, QR, and network logic.
 */
public class CoreTestRunner {

    private static int testsRun = 0;
    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   Kurakagi Core Engine Unit Tests (API 7 Pure Java)   ");
        System.out.println("=================================================");

        testTotpHotpRfcVectors();
        testTotpTriplet();
        testBase32RfcVectorsAndSanitization();
        testOtpUriParserAndRoundTrip();
        testAccountAndTokenStoragePlainJson();
        testTokenStorageEncryption();
        testBackupFormatV2Security();
        testQrEncoder();
        testQrYuvDecoder();
        testSntpPacketParsing();

        System.out.println("=================================================");
        System.out.println("Tests Run:    " + testsRun);
        System.out.println("Tests Passed: " + testsPassed);
        System.out.println("Tests Failed: " + testsFailed);
        System.out.println("=================================================");

        if (testsFailed > 0) {
            System.err.println("SOME TESTS FAILED!");
            System.exit(1);
        } else {
            System.out.println("ALL TESTS PASSED SUCCESSFULLY! (100% OK)");
        }
    }

    private static void assertEquals(String testName, Object expected, Object actual) {
        testsRun++;
        if (expected == null && actual == null) {
            testsPassed++;
            System.out.println("[PASS] " + testName);
            return;
        }
        if (expected != null && expected.equals(actual)) {
            testsPassed++;
            System.out.println("[PASS] " + testName);
        } else {
            testsFailed++;
            System.err.println("[FAIL] " + testName + " | Expected: [" + expected + "], Got: [" + actual + "]");
        }
    }

    private static void assertTrue(String testName, boolean condition) {
        testsRun++;
        if (condition) {
            testsPassed++;
            System.out.println("[PASS] " + testName);
        } else {
            testsFailed++;
            System.err.println("[FAIL] " + testName + " | Condition was false!");
        }
    }

    private static void testTotpHotpRfcVectors() {
        System.out.println("\n--- 1. Testing RFC 4226 & RFC 6238 (HOTP & TOTP) ---");
        try {
            // RFC 4226 Appendix D vectors (Secret: "12345678901234567890", 20 bytes)
            byte[] hotpSecret = "12345678901234567890".getBytes("UTF-8");
            String[] expectedHotp = {
                "755224", "287082", "359152", "969429", "338314",
                "254676", "287922", "162583", "399871", "520489"
            };

            for (int i = 0; i < expectedHotp.length; i++) {
                String otp = TotpCore.generateHOTP(hotpSecret, i, 6, "HmacSHA1");
                assertEquals("RFC 4226 HOTP count=" + i, expectedHotp[i], otp);
            }

            // RFC 6238 Appendix B vectors (8 digits, step=30)
            byte[] seedSha1 = "12345678901234567890".getBytes("UTF-8");
            byte[] seedSha256 = "12345678901234567890123456789012".getBytes("UTF-8");
            byte[] seedSha512 = "1234567890123456789012345678901234567890123456789012345678901234".getBytes("UTF-8");

            long[] times = { 59L, 1111111109L, 1234567890L, 2000000000L, 20000000000L };

            String[] expectedSha1 = {
                "94287082", "07081804", "89005924", "69279037", "65353130"
            };
            String[] expectedSha256 = {
                "46119246", "68084774", "91819424", "90698825", "77737706"
            };
            String[] expectedSha512 = {
                "90693936", "25091201", "93441116", "38618901", "47863826"
            };

            for (int i = 0; i < times.length; i++) {
                long timeMs = times[i] * 1000L;
                String c1 = TotpCore.generateTOTP(seedSha1, timeMs, 30, 8, "HmacSHA1");
                assertEquals("RFC 6238 SHA1 time=" + times[i], expectedSha1[i], c1);

                String c256 = TotpCore.generateTOTP(seedSha256, timeMs, 30, 8, "HmacSHA256");
                assertEquals("RFC 6238 SHA256 time=" + times[i], expectedSha256[i], c256);

                String c512 = TotpCore.generateTOTP(seedSha512, timeMs, 30, 8, "HmacSHA512");
                assertEquals("RFC 6238 SHA512 time=" + times[i], expectedSha512[i], c512);
            }

        } catch (Exception e) {
            testsFailed++;
            System.err.println("[FAIL] TOTP exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testTotpTriplet() {
        System.out.println("\n--- 2. Testing TOTP Triplet (T-1, T, T+1) ---");
        try {
            byte[] secret = "12345678901234567890".getBytes("UTF-8");
            long timeMs = 1234567890L * 1000L; // step = 30 -> counter = 41152263
            String[] triplet = TotpCore.generateTotpTriplet(secret, timeMs, 30, 8, "HmacSHA1");

            long counter = 1234567890L / 30L;
            String prev = TotpCore.generateHOTP(secret, counter - 1, 8, "HmacSHA1");
            String curr = TotpCore.generateHOTP(secret, counter, 8, "HmacSHA1");
            String next = TotpCore.generateHOTP(secret, counter + 1, 8, "HmacSHA1");

            assertEquals("Triplet length", 3, triplet.length);
            assertEquals("Triplet T-1", prev, triplet[0]);
            assertEquals("Triplet T", curr, triplet[1]);
            assertEquals("Triplet T+1", next, triplet[2]);
            assertEquals("Current matches RFC vector", "89005924", triplet[1]);

            // Test remaining seconds
            int remaining = TotpCore.getRemainingSeconds(1234567890L * 1000L, 30);
            assertEquals("Remaining seconds for 1234567890", 30, remaining); // 1234567890 % 30 == 0 -> 30 remaining
        } catch (Exception e) {
            testsFailed++;
            System.err.println("[FAIL] Triplet exception: " + e.getMessage());
        }
    }

    private static void testBase32RfcVectorsAndSanitization() {
        System.out.println("\n--- 3. Testing RFC 4648 Base32 Encode / Decode ---");
        try {
            String[] inputs = { "", "f", "fo", "foo", "foob", "foobar" };
            String[] expectedEncoded = {
                "",
                "MY======",
                "MZXQ====",
                "MZXW6===",
                "MZXW6YQ=",
                "MZXW6YTBOI======"
            };

            for (int i = 0; i < inputs.length; i++) {
                byte[] raw = inputs[i].getBytes("UTF-8");
                String enc = Base32.encode(raw);
                assertEquals("Base32 encode [" + inputs[i] + "]", expectedEncoded[i], enc);

                byte[] dec = Base32.decode(expectedEncoded[i]);
                assertEquals("Base32 decode [" + expectedEncoded[i] + "]", inputs[i], new String(dec, "UTF-8"));
            }

            // Test Sanitization: lowercase, hyphens, spaces, missing padding
            String dirty = " m z x w - 6 y t b - o i = = ";
            byte[] sanitizedDec = Base32.decode(dirty);
            assertEquals("Base32 sanitization test", "foobar", new String(sanitizedDec, "UTF-8"));

            String unpadded = "MZXW6YTBOI";
            byte[] unpaddedDec = Base32.decode(unpadded);
            assertEquals("Base32 unpadded decode", "foobar", new String(unpaddedDec, "UTF-8"));

            // Test illegal character validation
            boolean threwIllegal = false;
            try {
                Base32.decode("MZXW689"); // 8 and 9 are illegal Base32 chars
            } catch (IllegalArgumentException e) {
                threwIllegal = true;
            }
            assertTrue("Base32 rejects illegal characters", threwIllegal);

        } catch (Exception e) {
            testsFailed++;
            System.err.println("[FAIL] Base32 exception: " + e.getMessage());
        }
    }

    private static void testOtpUriParserAndRoundTrip() {
        System.out.println("\n--- 4. Testing OtpUriParser & toUri ---");
        try {
            String uri = "otpauth://totp/GitHub:alice%40retroauth.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&algorithm=SHA256&digits=8&period=60";
            Account account = OtpUriParser.parse(uri);

            assertEquals("Parsed issuer", "GitHub", account.getIssuer());
            assertEquals("Parsed label", "alice@retroauth.com", account.getLabel());
            assertEquals("Parsed secret", "JBSWY3DPEHPK3PXP", account.getSecret());
            assertEquals("Parsed algorithm", "HmacSHA256", account.getAlgorithm());
            assertEquals("Parsed digits", 8, account.getDigits());
            assertEquals("Parsed period", 60, account.getPeriodSeconds());

            // toUri round-trip
            String generatedUri = OtpUriParser.toUri(account);
            Account roundTrip = OtpUriParser.parse(generatedUri);

            assertEquals("Roundtrip issuer", account.getIssuer(), roundTrip.getIssuer());
            assertEquals("Roundtrip label", account.getLabel(), roundTrip.getLabel());
            assertEquals("Roundtrip secret", account.getSecret(), roundTrip.getSecret());
            assertEquals("Roundtrip algorithm", account.getAlgorithm(), roundTrip.getAlgorithm());
            assertEquals("Roundtrip digits", account.getDigits(), roundTrip.getDigits());
            assertEquals("Roundtrip period", account.getPeriodSeconds(), roundTrip.getPeriodSeconds());

        } catch (Exception e) {
            testsFailed++;
            System.err.println("[FAIL] OtpUriParser exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testAccountAndTokenStoragePlainJson() {
        System.out.println("\n--- 5. Testing Account & TokenStorage Plain JSON ---");
        try {
            List list = new ArrayList();
            Account a1 = new Account("alice@gmail.com", "Google", "JBSWY3DPEHPK3PXP");
            Account a2 = new Account("bob@corp.com", "CorpAuth", "MZXW6YTBOI");
            a2.setDigits(8);
            a2.setPeriodSeconds(60);
            a2.setAlgorithm("HmacSHA256");

            list.add(a1);
            list.add(a2);

            String json = TokenStorage.exportToJson(list);
            assertTrue("JSON not empty", json != null && json.length() > 0);

            List imported = TokenStorage.importFromJson(json);
            assertEquals("Imported accounts size", 2, imported.size());

            Account imp1 = (Account) imported.get(0);
            assertEquals("Account 1 issuer", "Google", imp1.getIssuer());
            assertEquals("Account 1 label", "alice@gmail.com", imp1.getLabel());
            assertEquals("Account 1 secret", "JBSWY3DPEHPK3PXP", imp1.getSecret());

            Account imp2 = (Account) imported.get(1);
            assertEquals("Account 2 issuer", "CorpAuth", imp2.getIssuer());
            assertEquals("Account 2 digits", 8, imp2.getDigits());
            assertEquals("Account 2 period", 60, imp2.getPeriodSeconds());
            assertEquals("Account 2 algorithm", "HmacSHA256", imp2.getAlgorithm());

            // Test File export and import
            File tempFile = File.createTempFile("retroauth_test_", ".json");
            tempFile.deleteOnExit();

            TokenStorage.exportToFile(tempFile, list);
            List fromFile = TokenStorage.importFromFile(tempFile);
            assertEquals("Import from file size", 2, fromFile.size());

        } catch (Exception e) {
            testsFailed++;
            System.err.println("[FAIL] JSON storage exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testBackupFormatV2Security() {
        System.out.println("\n--- 6b. Testing backup format v2 (encrypt-then-MAC) ---");
        try {
            List list = new ArrayList();
            list.add(new Account("v2@test.com", "Vault", "JBSWY3DPEHPK3PXP"));
            char[] password = "correct horse battery staple".toCharArray();

            byte[] enc = TokenStorage.exportEncrypted(list, password);
            String head = new String(enc, 0, 14, "US-ASCII");
            assertEquals("v2 magic header", "KURAKAGI__ENC2", head);
            int iter = ((enc[14] & 0xFF) << 24) | ((enc[15] & 0xFF) << 16) | ((enc[16] & 0xFF) << 8) | (enc[17] & 0xFF);
            assertEquals("v2 stores iteration count", TokenStorage.DEFAULT_ITERATIONS_V2, iter);
            assertTrue("isEncryptedBackup(v2)", TokenStorage.isEncryptedBackup(enc));
            assertTrue("v2 is not legacy", !TokenStorage.isLegacyEncryptedBackup(enc));
            assertTrue("Plain JSON is not an encrypted backup",
                    !TokenStorage.isEncryptedBackup("[{\"a\":1}]".getBytes("UTF-8")));

            // Same input twice gives different output (random salt and IV)
            byte[] enc2 = TokenStorage.exportEncrypted(list, password);
            assertTrue("Random salt/IV per export", !java.util.Arrays.equals(enc, enc2));

            // Ciphertext does not contain the secret in clear
            String raw = new String(enc, "ISO-8859-1");
            assertTrue("Secret not visible in file", raw.indexOf("JBSWY3DPEHPK3PXP") < 0);

            // Flip one bit in every region: header iterations, salt, IV, ciphertext, MAC
            int[] positions = { 17, 20, 40, 60, enc.length - 1 };
            for (int i = 0; i < positions.length; i++) {
                byte[] t = enc.clone();
                t[positions[i]] ^= 0x01;
                boolean rejected = false;
                try {
                    TokenStorage.importEncrypted(t, password);
                } catch (GeneralSecurityException e) {
                    rejected = true;
                }
                assertTrue("Tampered byte " + positions[i] + " rejected", rejected);
            }

            // Truncated file rejected
            byte[] cut = new byte[enc.length - 5];
            System.arraycopy(enc, 0, cut, 0, cut.length);
            boolean cutRejected = false;
            try {
                TokenStorage.importEncrypted(cut, password);
            } catch (GeneralSecurityException e) {
                cutRejected = true;
            }
            assertTrue("Truncated v2 rejected", cutRejected);

            // Legacy v1 files (made by 1.0.0) can still be restored
            byte[] v1 = buildLegacyV1(list, password);
            assertTrue("isLegacyEncryptedBackup(v1)", TokenStorage.isLegacyEncryptedBackup(v1));
            List fromV1 = TokenStorage.importEncrypted(v1, password);
            assertEquals("Legacy v1 restore count", 1, fromV1.size());

            // PBKDF2-HMAC-SHA256 against RFC 7914 section 11 test vector (P="passwd", S="salt", c=1)
            byte[] dk = TokenStorage.deriveKeyPBKDF2("HmacSHA256", "passwd".toCharArray(),
                    "salt".getBytes("US-ASCII"), 1, 16);
            assertEquals("PBKDF2-HMAC-SHA256 RFC 7914 vector", "55ac046e56e3089fec1691c22544b605", toHex(dk));
        } catch (Exception e) {
            testsFailed++;
            System.err.println("[FAIL] v2 exception: " + e);
            e.printStackTrace();
        }
    }

    private static byte[] buildLegacyV1(List list, char[] password) throws Exception {
        byte[] salt = new byte[16];
        byte[] iv = new byte[16];
        new java.security.SecureRandom().nextBytes(salt);
        new java.security.SecureRandom().nextBytes(iv);
        byte[] key = TokenStorage.deriveKeyPBKDF2(password, salt, 10000, 16);
        javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"),
                new javax.crypto.spec.IvParameterSpec(iv));
        byte[] ct = c.doFinal(TokenStorage.exportToJson(list).getBytes("UTF-8"));
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write("RETROAUTH_ENC1".getBytes("US-ASCII"));
        out.write(salt);
        out.write(iv);
        out.write(ct);
        return out.toByteArray();
    }

    private static String toHex(byte[] b) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < b.length; i++) {
            String h = Integer.toHexString(b[i] & 0xFF);
            if (h.length() == 1) sb.append('0');
            sb.append(h);
        }
        return sb.toString();
    }

    private static void testTokenStorageEncryption() {
        System.out.println("\n--- 6. Testing TokenStorage AES-CBC / PBKDF2 Encryption ---");
        try {
            List list = new ArrayList();
            list.add(new Account("sec1@test.com", "Vault", "JBSWY3DPEHPK3PXP"));
            list.add(new Account("sec2@test.com", "Vault", "MZXW6YTBOI"));

            char[] password = "RetroAuthSecretPassword2026!".toCharArray();
            byte[] encrypted = TokenStorage.exportEncrypted(list, password);

            assertTrue("Encrypted payload created", encrypted != null && encrypted.length > 46);

            // Import with correct password
            List decrypted = TokenStorage.importEncrypted(encrypted, password);
            assertEquals("Decrypted account count", 2, decrypted.size());
            assertEquals("Decrypted account 1 label", "sec1@test.com", ((Account) decrypted.get(0)).getLabel());

            // Test file encrypted export & import
            File tempEnc = File.createTempFile("retroauth_enc_", ".dat");
            tempEnc.deleteOnExit();
            TokenStorage.exportEncryptedToFile(tempEnc, list, password);
            List fromEncFile = TokenStorage.importEncryptedFromFile(tempEnc, password);
            assertEquals("Encrypted file import count", 2, fromEncFile.size());

            // Test wrong password failure
            boolean wrongPasswordFailed = false;
            try {
                TokenStorage.importEncrypted(encrypted, "WrongPassword123!".toCharArray());
            } catch (GeneralSecurityException e) {
                wrongPasswordFailed = true;
            }
            assertTrue("Wrong password rejected with GeneralSecurityException", wrongPasswordFailed);

        } catch (Exception e) {
            testsFailed++;
            System.err.println("[FAIL] Encryption exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testQrEncoder() {
        System.out.println("\n--- 7. Testing QrEncoder ---");
        try {
            String content = "otpauth://totp/GitHub:alice?secret=JBSWY3DPEHPK3PXP&issuer=GitHub";
            QrMatrix qr = QrEncoder.encode(content, QrEncoder.ErrorCorrectionLevel.M);

            assertTrue("QR matrix not null", qr != null);
            assertTrue("QR matrix width > 0", qr.getWidth() > 0);
            assertTrue("QR matrix is square", qr.getWidth() == qr.getHeight());

            // QrEncoder returns the matrix WITHOUT a quiet zone (ZXing MARGIN=0);
            // the quiet zone is added at render time by QrMatrix.toPixelArray.
            int codeSize = qr.getWidth();

            // Each 7x7 finder pattern: black outer ring, white inner ring, black 3x3 core
            assertTrue("Top-left finder pattern (7x7)", isFinderPattern(qr, 0, 0));
            assertTrue("Top-right finder pattern (7x7)", isFinderPattern(qr, codeSize - 7, 0));
            assertTrue("Bottom-left finder pattern (7x7)", isFinderPattern(qr, 0, codeSize - 7));

            // Test pixel rendering
            int[] pixels = qr.toPixelArray(2, 2, 0xFF000000, 0xFFFFFFFF);
            assertTrue("Pixel array length matches dimensions", pixels.length == (qr.getWidth() + 4) * 2 * (qr.getHeight() + 4) * 2);

            // Round trip: render with a 4-module quiet zone, decode with ZXing, compare text
            int scale = 4;
            int margin = 4;
            int imgW = (qr.getWidth() + 2 * margin) * scale;
            int imgH = (qr.getHeight() + 2 * margin) * scale;
            int[] img = qr.toPixelArray(scale, margin, 0xFF000000, 0xFFFFFFFF);
            com.google.zxing.LuminanceSource source = new com.google.zxing.RGBLuminanceSource(imgW, imgH, img);
            com.google.zxing.BinaryBitmap bitmap = new com.google.zxing.BinaryBitmap(
                    new com.google.zxing.common.HybridBinarizer(source));
            com.google.zxing.Result decoded = new com.google.zxing.qrcode.QRCodeReader().decode(bitmap);
            assertEquals("ZXing round-trip decodes original URI", content, decoded.getText());

            System.out.println("Generated QR Code for OTP URI (Version auto-detected): " + codeSize + "x" + codeSize);
        } catch (Exception e) {
            testsFailed++;
            System.err.println("[FAIL] QrEncoder exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** True if the 7x7 block at (left, top) is a QR finder pattern. */
    private static boolean isFinderPattern(QrMatrix qr, int left, int top) {
        for (int dy = 0; dy < 7; dy++) {
            for (int dx = 0; dx < 7; dx++) {
                boolean outerRing = dx == 0 || dx == 6 || dy == 0 || dy == 6;
                boolean core = dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4;
                if (qr.get(left + dx, top + dy) != (outerRing || core)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void testQrYuvDecoder() {
        System.out.println("\n--- 8. Testing QrYuvDecoder (NV21 / YUV420SP) ---");
        try {
            int width = 160;
            int height = 120;
            byte[] mockNv21 = new byte[width * height * 3 / 2];

            // Fill with gradient luminance
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    mockNv21[y * width + x] = (byte) ((x + y) % 256);
                }
            }

            // Extract Y plane
            byte[] yPlane = QrYuvDecoder.extractYPlane(mockNv21, width, height);
            assertEquals("Y plane size", width * height, yPlane.length);

            // Crop center 60x60
            byte[] cropped = QrYuvDecoder.cropYPlane(yPlane, width, height, 50, 30, 60, 60);
            assertEquals("Cropped size", 60 * 60, cropped.length);

            // Binarization tests
            boolean[] otsu = QrYuvDecoder.binarizeOtsu(yPlane, width, height);
            assertEquals("Otsu binarized size", width * height, otsu.length);

            boolean[] adaptive = QrYuvDecoder.binarizeAdaptive(yPlane, width, height, 15, 10);
            assertEquals("Adaptive binarized size", width * height, adaptive.length);

            // Finder ratio check (1:1:3:1:1)
            int[] validRatio = { 10, 10, 30, 10, 10 };
            assertTrue("checkFinderRatio valid", QrYuvDecoder.checkFinderRatio(validRatio));

            int[] invalidRatio = { 10, 20, 10, 10, 10 };
            assertTrue("checkFinderRatio invalid", !QrYuvDecoder.checkFinderRatio(invalidRatio));

            QrYuvDecoder.QrScanResult result = QrYuvDecoder.processPreviewFrame(mockNv21, width, height);
            assertTrue("processPreviewFrame returned result", result != null);

        } catch (Exception e) {
            testsFailed++;
            System.err.println("[FAIL] QrYuvDecoder exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testSntpPacketParsing() {
        System.out.println("\n--- 9. Testing SntpClient Packet Structure & Calculation ---");
        try {
            byte[] buffer = new byte[48];
            buffer[0] = 0x24; // LI=0, VN=4, Mode=4 (Server)
            buffer[1] = 1;    // Stratum 1

            long t1Ms = 1700000000000L;
            long t2Ms = 1700000000050L;
            long t3Ms = 1700000000060L;
            long t4Ms = 1700000000120L;

            // Write timestamps
            SntpClient.writeTimeStamp(buffer, 24, t1Ms); // Originate (T1)
            SntpClient.writeTimeStamp(buffer, 32, t2Ms); // Receive (T2)
            SntpClient.writeTimeStamp(buffer, 40, t3Ms); // Transmit (T3)

            SntpClient.SntpResult res = SntpClient.parseResponse(buffer, t1Ms, t4Ms);
            assertTrue("SNTP result not null", res != null);

            // Clock offset = ((T2 - T1) + (T3 - T4)) / 2 = ((50) + (-60)) / 2 = -5 ms (±1ms due to fixed-point fraction rounding)
            // Round trip delay = (T4 - T1) - (T3 - T2) = 120 - 10 = 110 ms
            assertTrue("SNTP clock offset calculation within 1ms tolerance", Math.abs(res.clockOffset - (-5L)) <= 1);
            assertTrue("SNTP roundtrip calculation within 1ms tolerance", Math.abs(res.roundTripTime - 110L) <= 1);

        } catch (Exception e) {
            testsFailed++;
            System.err.println("[FAIL] SntpClient exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
