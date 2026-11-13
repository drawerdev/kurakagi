package io.github.drawerdev.kurakagi.core;

import io.github.drawerdev.kurakagi.storage.Account;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * Parser and serializer for otpauth:// URIs (RFC 6238 / Key Uri Format).
 * Fully compatible with Android 2.1 (API 7).
 */
public final class OtpUriParser {

    private static final String SCHEME = "otpauth://";
    private static final String TYPE_TOTP = "totp";
    private static final String TYPE_HOTP = "hotp";

    private OtpUriParser() {
        // Utility class
    }

    /**
     * Parses an otpauth:// URI into an Account object.
     *
     * @param uriString URI string (e.g., otpauth://totp/Example:alice@google.com?secret=JBSWY3DPEHPK3PXP&issuer=Example)
     * @return Account object populated with parsed fields
     * @throws IllegalArgumentException if the URI is malformed or missing secret
     */
    public static Account parse(String uriString) {
        if (uriString == null || uriString.length() == 0) {
            throw new IllegalArgumentException("URI string cannot be null or empty");
        }

        String trimmed = uriString.trim();
        String lower = trimmed.toLowerCase();
        if (!lower.startsWith(SCHEME)) {
            throw new IllegalArgumentException("Invalid URI scheme, expected otpauth://");
        }

        int afterScheme = SCHEME.length();
        int slashIdx = trimmed.indexOf('/', afterScheme);
        int questionIdx = trimmed.indexOf('?', afterScheme);

        if (slashIdx < 0 && questionIdx < 0) {
            throw new IllegalArgumentException("Malformed otpauth URI: missing path separator");
        }

        String type;
        String pathAndQuery;
        if (slashIdx >= 0 && (questionIdx < 0 || slashIdx < questionIdx)) {
            type = trimmed.substring(afterScheme, slashIdx).toLowerCase();
            pathAndQuery = trimmed.substring(slashIdx + 1);
        } else {
            type = trimmed.substring(afterScheme, questionIdx).toLowerCase();
            pathAndQuery = trimmed.substring(questionIdx);
        }

        if (!TYPE_TOTP.equals(type) && !TYPE_HOTP.equals(type)) {
            throw new IllegalArgumentException("Unsupported OTP type: " + type);
        }

        String rawPath;
        String rawQuery;
        int qIdx = pathAndQuery.indexOf('?');
        if (qIdx >= 0) {
            rawPath = pathAndQuery.substring(0, qIdx);
            rawQuery = pathAndQuery.substring(qIdx + 1);
        } else {
            rawPath = pathAndQuery;
            rawQuery = "";
        }

        // Clean rawPath
        while (rawPath.startsWith("/")) {
            rawPath = rawPath.substring(1);
        }

        String decodedPath = urlDecode(rawPath);
        String pathIssuer = null;
        String pathLabel = decodedPath;

        int colonIdx = decodedPath.indexOf(':');
        if (colonIdx >= 0) {
            pathIssuer = decodedPath.substring(0, colonIdx).trim();
            pathLabel = decodedPath.substring(colonIdx + 1).trim();
        }

        String secret = null;
        String queryIssuer = null;
        String algorithm = "HmacSHA1";
        int digits = 6;
        int period = 30;

        if (rawQuery != null && rawQuery.length() > 0) {
            String[] pairs = splitString(rawQuery, '&');
            for (int i = 0; i < pairs.length; i++) {
                String pair = pairs[i];
                if (pair == null || pair.length() == 0) {
                    continue;
                }
                int eqIdx = pair.indexOf('=');
                if (eqIdx >= 0) {
                    String key = urlDecode(pair.substring(0, eqIdx)).trim().toLowerCase();
                    String val = urlDecode(pair.substring(eqIdx + 1)).trim();

                    if ("secret".equals(key)) {
                        secret = val;
                    } else if ("issuer".equals(key)) {
                        queryIssuer = val;
                    } else if ("algorithm".equals(key)) {
                        algorithm = TotpCore.normalizeAlgorithm(val);
                    } else if ("digits".equals(key)) {
                        try {
                            digits = Integer.parseInt(val);
                        } catch (NumberFormatException ignore) {
                        }
                    } else if ("period".equals(key)) {
                        try {
                            period = Integer.parseInt(val);
                        } catch (NumberFormatException ignore) {
                        }
                    }
                }
            }
        }

        if (secret == null || secret.length() == 0) {
            throw new IllegalArgumentException("Missing required parameter: secret");
        }

        // Issuer prioritization: query param takes precedence if present, otherwise path prefix
        String issuer = (queryIssuer != null && queryIssuer.length() > 0) ? queryIssuer : pathIssuer;
        if (issuer == null) {
            issuer = "";
        }

        // Validate base32 secret
        Base32.decode(secret);

        Account account = new Account();
        account.setLabel(pathLabel);
        account.setIssuer(issuer);
        account.setSecret(secret.toUpperCase());
        account.setAlgorithm(algorithm);
        account.setDigits(digits <= 0 ? 6 : digits);
        account.setPeriodSeconds(period <= 0 ? 30 : period);

        return account;
    }

    /**
     * Builds an otpauth://totp/ URI from an Account object.
     *
     * @param account Account object
     * @return Formatted otpauth URI
     */
    public static String toUri(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("Account must not be null");
        }

        StringBuffer sb = new StringBuffer(SCHEME);
        sb.append(TYPE_TOTP).append('/');

        String issuer = account.getIssuer();
        String label = account.getLabel();

        StringBuffer pathBuf = new StringBuffer();
        if (issuer != null && issuer.length() > 0) {
            pathBuf.append(issuer).append(':');
        }
        if (label != null && label.length() > 0) {
            pathBuf.append(label);
        }

        sb.append(urlEncode(pathBuf.toString()).replace("+", "%20"));
        sb.append("?secret=").append(urlEncode(account.getSecret() != null ? account.getSecret() : ""));

        if (issuer != null && issuer.length() > 0) {
            sb.append("&issuer=").append(urlEncode(issuer).replace("+", "%20"));
        }

        String algo = account.getAlgorithm();
        if (algo != null && algo.length() > 0 && !algo.equalsIgnoreCase("HmacSHA1")) {
            if (algo.indexOf("512") >= 0) {
                sb.append("&algorithm=SHA512");
            } else if (algo.indexOf("256") >= 0) {
                sb.append("&algorithm=SHA256");
            }
        }

        if (account.getDigits() != 6 && account.getDigits() > 0) {
            sb.append("&digits=").append(account.getDigits());
        }

        if (account.getPeriodSeconds() != 30 && account.getPeriodSeconds() > 0) {
            sb.append("&period=").append(account.getPeriodSeconds());
        }

        return sb.toString();
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 encoding not supported", e);
        }
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 encoding not supported", e);
        }
    }

    private static String[] splitString(String str, char delimiter) {
        java.util.ArrayList list = new java.util.ArrayList();
        int start = 0;
        int len = str.length();
        for (int i = 0; i < len; i++) {
            if (str.charAt(i) == delimiter) {
                list.add(str.substring(start, i));
                start = i + 1;
            }
        }
        if (start <= len) {
            list.add(str.substring(start));
        }
        String[] result = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = (String) list.get(i);
        }
        return result;
    }
}
