package io.github.drawerdev.kurakagi.core;

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Pure Java RFC 6238 (TOTP) and RFC 4226 (HOTP) implementation.
 * Fully compatible with Android 2.1 (API 7) Dalvik VM.
 */
public final class TotpCore {

    public static final String ALGO_HMAC_SHA1 = "HmacSHA1";
    public static final String ALGO_HMAC_SHA256 = "HmacSHA256";
    public static final String ALGO_HMAC_SHA512 = "HmacSHA512";

    public static final int DEFAULT_TIME_STEP_SECONDS = 30;
    public static final int DEFAULT_DIGITS = 6;

    private static final int[] DIGIT_MODULI = new int[] {
        1,          // 0
        10,         // 1
        100,        // 2
        1000,       // 3
        10000,      // 4
        100000,     // 5
        1000000,    // 6
        10000000,   // 7
        100000000   // 8
    };

    private TotpCore() {
        // Private constructor for utility class
    }

    /**
     * Normalizes algorithm name to standard Java Cryptography Mac algorithm name.
     */
    public static String normalizeAlgorithm(String algorithm) {
        if (algorithm == null || algorithm.length() == 0) {
            return ALGO_HMAC_SHA1;
        }
        String upper = algorithm.toUpperCase();
        if (upper.indexOf("512") >= 0) {
            return ALGO_HMAC_SHA512;
        }
        if (upper.indexOf("256") >= 0) {
            return ALGO_HMAC_SHA256;
        }
        return ALGO_HMAC_SHA1;
    }

    /**
     * Generates an RFC 4226 HOTP code.
     *
     * @param secretKey HMAC secret key bytes
     * @param counter Counter value
     * @param digits Desired number of digits (typically 6 or 8)
     * @param algorithm HMAC algorithm (HmacSHA1, HmacSHA256, HmacSHA512)
     * @return Zero-padded OTP string
     */
    public static String generateHOTP(byte[] secretKey, long counter, int digits, String algorithm)
            throws GeneralSecurityException {
        if (secretKey == null || secretKey.length == 0) {
            throw new IllegalArgumentException("Secret key must not be null or empty");
        }
        if (digits < 1 || digits > 8) {
            throw new IllegalArgumentException("Digits must be between 1 and 8: " + digits);
        }

        String normalizedAlgo = normalizeAlgorithm(algorithm);

        // Counter to 8-byte big-endian representation
        byte[] text = new byte[8];
        long val = counter;
        for (int i = 7; i >= 0; i--) {
            text[i] = (byte) (val & 0xFF);
            val >>>= 8;
        }

        SecretKeySpec keySpec = new SecretKeySpec(secretKey, normalizedAlgo);
        Mac mac = Mac.getInstance(normalizedAlgo);
        mac.init(keySpec);
        byte[] hash = mac.doFinal(text);

        // Dynamic truncation (RFC 4226 section 5.4)
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                   | ((hash[offset + 1] & 0xFF) << 16)
                   | ((hash[offset + 2] & 0xFF) << 8)
                   | (hash[offset + 3] & 0xFF);

        int otp = binary % DIGIT_MODULI[digits];
        String result = Integer.toString(otp);
        while (result.length() < digits) {
            result = "0" + result;
        }
        return result;
    }

    /**
     * Generates an RFC 6238 TOTP code for the given epoch time.
     *
     * @param secretKey HMAC secret key bytes
     * @param timeMs Unix epoch time in milliseconds
     * @param timeStepSeconds Time window step in seconds (usually 30)
     * @param digits Number of digits (usually 6 or 8)
     * @param algorithm HMAC algorithm (e.g., "HmacSHA1", "HmacSHA256", "HmacSHA512")
     * @return Zero-padded TOTP string
     */
    public static String generateTOTP(byte[] secretKey, long timeMs, int timeStepSeconds, int digits, String algorithm)
            throws GeneralSecurityException {
        long step = timeStepSeconds <= 0 ? DEFAULT_TIME_STEP_SECONDS : timeStepSeconds;
        long timeSec = timeMs / 1000L;
        // Handle negative timestamps properly if any
        long counter = timeSec >= 0 ? timeSec / step : (timeSec - (step - 1)) / step;
        return generateHOTP(secretKey, counter, digits, algorithm);
    }

    /**
     * Generates a triplet of TOTP codes for [T-1, T, T+1] windows.
     * Useful for tolerant verification and clock skew handling.
     *
     * @return Array containing [previousOtp, currentOtp, nextOtp]
     */
    public static String[] generateTotpTriplet(byte[] secretKey, long timeMs, int timeStepSeconds, int digits, String algorithm)
            throws GeneralSecurityException {
        long step = timeStepSeconds <= 0 ? DEFAULT_TIME_STEP_SECONDS : timeStepSeconds;
        long timeSec = timeMs / 1000L;
        long counter = timeSec >= 0 ? timeSec / step : (timeSec - (step - 1)) / step;

        String prev = generateHOTP(secretKey, counter - 1, digits, algorithm);
        String curr = generateHOTP(secretKey, counter, digits, algorithm);
        String next = generateHOTP(secretKey, counter + 1, digits, algorithm);

        return new String[] { prev, curr, next };
    }

    /**
     * Returns remaining seconds in the current TOTP window.
     */
    public static int getRemainingSeconds(long timeMs, int timeStepSeconds) {
        int step = timeStepSeconds <= 0 ? DEFAULT_TIME_STEP_SECONDS : timeStepSeconds;
        long timeSec = timeMs / 1000L;
        int elapsed = (int) (timeSec % step);
        if (elapsed < 0) {
            elapsed += step;
        }
        return step - elapsed;
    }

    /**
     * Returns the progress within the current window as a fraction (0.0 to 1.0).
     */
    public static float getProgressRatio(long timeMs, int timeStepSeconds) {
        int step = timeStepSeconds <= 0 ? DEFAULT_TIME_STEP_SECONDS : timeStepSeconds;
        int remaining = getRemainingSeconds(timeMs, step);
        return (float) (step - remaining) / (float) step;
    }
}
