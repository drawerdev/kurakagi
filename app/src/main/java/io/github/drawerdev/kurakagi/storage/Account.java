package io.github.drawerdev.kurakagi.storage;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

/**
 * TOTP Account model representing an authenticator entry.
 * Fully compatible with Android 2.1 (API 7).
 */
public class Account {
    private String id;
    private String label;
    private String issuer;
    private String secret;
    private int digits;
    private int periodSeconds;
    private String algorithm;
    private long createdAt;

    public Account() {
        this.id = UUID.randomUUID().toString();
        this.digits = 6;
        this.periodSeconds = 30;
        this.algorithm = "HmacSHA1";
        this.createdAt = System.currentTimeMillis();
    }

    public Account(String label, String issuer, String secret) {
        this();
        this.label = label;
        this.issuer = issuer;
        this.secret = secret;
    }

    public Account(String id, String label, String issuer, String secret, int digits, int periodSeconds, String algorithm, long createdAt) {
        this.id = (id != null && id.length() > 0) ? id : UUID.randomUUID().toString();
        this.label = label;
        this.issuer = issuer;
        this.secret = secret;
        this.digits = digits <= 0 ? 6 : digits;
        this.periodSeconds = periodSeconds <= 0 ? 30 : periodSeconds;
        this.algorithm = (algorithm != null && algorithm.length() > 0) ? algorithm : "HmacSHA1";
        this.createdAt = createdAt <= 0 ? System.currentTimeMillis() : createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public int getDigits() {
        return digits;
    }

    public void setDigits(int digits) {
        this.digits = digits <= 0 ? 6 : digits;
    }

    public int getPeriodSeconds() {
        return periodSeconds;
    }

    public void setPeriodSeconds(int periodSeconds) {
        this.periodSeconds = periodSeconds <= 0 ? 30 : periodSeconds;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = (algorithm != null && algorithm.length() > 0) ? algorithm : "HmacSHA1";
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Serializes account to Android org.json.JSONObject.
     */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("label", label != null ? label : "");
        json.put("issuer", issuer != null ? issuer : "");
        json.put("secret", secret != null ? secret : "");
        json.put("digits", digits);
        json.put("periodSeconds", periodSeconds);
        json.put("algorithm", algorithm != null ? algorithm : "HmacSHA1");
        json.put("createdAt", createdAt);
        return json;
    }

    /**
     * Deserializes account from Android org.json.JSONObject.
     */
    public static Account fromJson(JSONObject json) throws JSONException {
        if (json == null) {
            return null;
        }
        Account account = new Account();
        if (json.has("id")) {
            account.setId(json.getString("id"));
        }
        if (json.has("label")) {
            account.setLabel(json.optString("label", ""));
        }
        if (json.has("issuer")) {
            account.setIssuer(json.optString("issuer", ""));
        }
        if (json.has("secret")) {
            account.setSecret(json.optString("secret", ""));
        }
        if (json.has("digits")) {
            account.setDigits(json.optInt("digits", 6));
        }
        if (json.has("periodSeconds")) {
            account.setPeriodSeconds(json.optInt("periodSeconds", 30));
        }
        if (json.has("algorithm")) {
            account.setAlgorithm(json.optString("algorithm", "HmacSHA1"));
        }
        if (json.has("createdAt")) {
            account.setCreatedAt(json.optLong("createdAt", System.currentTimeMillis()));
        }
        return account;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return id != null ? id.equals(account.id) : account.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Account{" +
                "id='" + id + '\'' +
                ", label='" + label + '\'' +
                ", issuer='" + issuer + '\'' +
                ", digits=" + digits +
                ", periodSeconds=" + periodSeconds +
                ", algorithm='" + algorithm + '\'' +
                '}';
    }
}
