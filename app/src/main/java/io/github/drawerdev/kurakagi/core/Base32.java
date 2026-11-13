package io.github.drawerdev.kurakagi.core;

/**
 * RFC 4648 Base32 encoder and decoder in Pure Java.
 * Fully compatible with Android 2.1 (API 7).
 */
public final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final char[] DIGITS = ALPHABET.toCharArray();
    private static final int[] LOOKUP = new int[128];

    static {
        for (int i = 0; i < LOOKUP.length; i++) {
            LOOKUP[i] = -1;
        }
        for (int i = 0; i < DIGITS.length; i++) {
            LOOKUP[DIGITS[i]] = i;
        }
    }

    private Base32() {
        // Utility class
    }

    /**
     * Encodes byte array into standard RFC 4648 Base32 string with padding.
     */
    public static String encode(byte[] data) {
        return encode(data, true);
    }

    /**
     * Encodes byte array into Base32 string with optional padding.
     */
    public static String encode(byte[] data, boolean pad) {
        if (data == null || data.length == 0) {
            return "";
        }

        StringBuffer sb = new StringBuffer((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;

        for (int i = 0; i < data.length; i++) {
            buffer = (buffer << 8) | (data[i] & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                bitsLeft -= 5;
                sb.append(DIGITS[index]);
            }
        }

        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            sb.append(DIGITS[index]);
        }

        if (pad) {
            while ((sb.length() % 8) != 0) {
                sb.append('=');
            }
        }

        return sb.toString();
    }

    /**
     * Decodes a Base32 string into a byte array.
     * Automatically strips whitespace, hyphens, converts to uppercase,
     * handles missing or present padding, and validates characters.
     */
    public static byte[] decode(String base32) {
        if (base32 == null || base32.length() == 0) {
            return new byte[0];
        }

        // Sanitize string
        StringBuffer clean = new StringBuffer(base32.length());
        for (int i = 0; i < base32.length(); i++) {
            char c = base32.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '-') {
                continue; // Ignore separators
            }
            if (c == '=') {
                continue; // Strip padding
            }
            if (c >= 'a' && c <= 'z') {
                c = (char) (c - 32); // Convert to uppercase
            }
            if (c >= 128 || LOOKUP[c] == -1) {
                throw new IllegalArgumentException("Illegal character in Base32 string: " + c);
            }
            clean.append(c);
        }

        int cleanLen = clean.length();
        if (cleanLen == 0) {
            return new byte[0];
        }

        // Calculate expected output bytes
        int outLen = (cleanLen * 5) / 8;
        byte[] result = new byte[outLen];
        int buffer = 0;
        int bitsLeft = 0;
        int outIndex = 0;

        for (int i = 0; i < cleanLen; i++) {
            char c = clean.charAt(i);
            int val = LOOKUP[c];
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[outIndex++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }

        return result;
    }
}
