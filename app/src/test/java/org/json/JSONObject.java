package org.json;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Standard JSONObject compatible with Android org.json.
 */
public class JSONObject {
    public static final Object NULL = new Object() {
        @Override
        public boolean equals(Object o) {
            return o == null || o == this;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public String toString() {
            return "null";
        }
    };

    private final Map<String, Object> map;

    public JSONObject() {
        this.map = new HashMap<String, Object>();
    }

    public JSONObject(String source) throws JSONException {
        this(new JSONTokener(source));
    }

    public JSONObject(JSONTokener x) throws JSONException {
        this();
        char c;
        String key;

        if (x.nextClean() != '{') {
            throw new JSONException("A JSONObject text must begin with '{'");
        }
        for (;;) {
            c = x.nextClean();
            switch (c) {
                case 0:
                    throw new JSONException("A JSONObject text must end with '}'");
                case '}':
                    return;
                default:
                    x.back();
                    key = x.nextValue().toString();
            }

            c = x.nextClean();
            if (c != ':') {
                throw new JSONException("Expected a ':' after a key");
            }
            this.put(key, x.nextValue());

            switch (x.nextClean()) {
                case ';':
                case ',':
                    if (x.nextClean() == '}') {
                        return;
                    }
                    x.back();
                    break;
                case '}':
                    return;
                default:
                    throw new JSONException("Expected a ',' or '}'");
            }
        }
    }

    public JSONObject put(String key, Object value) throws JSONException {
        if (key == null) {
            throw new JSONException("Null key.");
        }
        if (value != null) {
            this.map.put(key, value);
        } else {
            this.map.remove(key);
        }
        return this;
    }

    public JSONObject put(String key, boolean value) throws JSONException {
        return this.put(key, Boolean.valueOf(value));
    }

    public JSONObject put(String key, int value) throws JSONException {
        return this.put(key, Integer.valueOf(value));
    }

    public JSONObject put(String key, long value) throws JSONException {
        return this.put(key, Long.valueOf(value));
    }

    public JSONObject put(String key, double value) throws JSONException {
        return this.put(key, Double.valueOf(value));
    }

    public Object get(String key) throws JSONException {
        if (key == null) {
            throw new JSONException("Null key.");
        }
        Object object = this.opt(key);
        if (object == null) {
            throw new JSONException("JSONObject[" + quote(key) + "] not found.");
        }
        return object;
    }

    public Object opt(String key) {
        return key == null ? null : this.map.get(key);
    }

    public String getString(String key) throws JSONException {
        Object object = this.get(key);
        if (object instanceof String) {
            return (String) object;
        }
        return object.toString();
    }

    public String optString(String key) {
        return this.optString(key, "");
    }

    public String optString(String key, String defaultValue) {
        Object object = this.opt(key);
        return NULL.equals(object) || object == null ? defaultValue : object.toString();
    }

    public int getInt(String key) throws JSONException {
        Object object = this.get(key);
        if (object instanceof Number) {
            return ((Number) object).intValue();
        }
        try {
            return Integer.parseInt((String) object);
        } catch (Exception e) {
            throw new JSONException("JSONObject[" + quote(key) + "] is not an int.");
        }
    }

    public int optInt(String key, int defaultValue) {
        try {
            return this.getInt(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public long getLong(String key) throws JSONException {
        Object object = this.get(key);
        if (object instanceof Number) {
            return ((Number) object).longValue();
        }
        try {
            return Long.parseLong((String) object);
        } catch (Exception e) {
            throw new JSONException("JSONObject[" + quote(key) + "] is not a long.");
        }
    }

    public long optLong(String key, long defaultValue) {
        try {
            return this.getLong(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key) throws JSONException {
        Object object = this.get(key);
        if (Boolean.TRUE.equals(object) || (object instanceof String && ((String) object).equalsIgnoreCase("true"))) {
            return true;
        }
        if (Boolean.FALSE.equals(object) || (object instanceof String && ((String) object).equalsIgnoreCase("false"))) {
            return false;
        }
        throw new JSONException("JSONObject[" + quote(key) + "] is not a boolean.");
    }

    public boolean optBoolean(String key, boolean defaultValue) {
        try {
            return this.getBoolean(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public JSONObject getJSONObject(String key) throws JSONException {
        Object object = this.get(key);
        if (object instanceof JSONObject) {
            return (JSONObject) object;
        }
        throw new JSONException("JSONObject[" + quote(key) + "] is not a JSONObject.");
    }

    public JSONObject optJSONObject(String key) {
        Object object = this.opt(key);
        return object instanceof JSONObject ? (JSONObject) object : null;
    }

    public JSONArray getJSONArray(String key) throws JSONException {
        Object object = this.get(key);
        if (object instanceof JSONArray) {
            return (JSONArray) object;
        }
        throw new JSONException("JSONObject[" + quote(key) + "] is not a JSONArray.");
    }

    public JSONArray optJSONArray(String key) {
        Object object = this.opt(key);
        return object instanceof JSONArray ? (JSONArray) object : null;
    }

    public boolean has(String key) {
        return this.map.containsKey(key);
    }

    public boolean isNull(String key) {
        return NULL.equals(this.opt(key));
    }

    public Iterator<String> keys() {
        return this.map.keySet().iterator();
    }

    public int length() {
        return this.map.size();
    }

    public static String quote(String string) {
        if (string == null || string.length() == 0) {
            return "\"\"";
        }

        char b;
        char c = 0;
        String hhhh;
        int i;
        int len = string.length();
        StringBuffer sb = new StringBuffer(len + 4);

        sb.append('"');
        for (i = 0; i < len; i += 1) {
            b = c;
            c = string.charAt(i);
            switch (c) {
                case '\\':
                case '"':
                    sb.append('\\');
                    sb.append(c);
                    break;
                case '/':
                    if (b == '<') {
                        sb.append('\\');
                    }
                    sb.append(c);
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                default:
                    if (c < ' ' || (c >= '\u0080' && c < '\u00a0') || (c >= '\u2000' && c < '\u2100')) {
                        sb.append("\\u");
                        hhhh = Integer.toHexString(c);
                        sb.append("0000", 0, 4 - hhhh.length());
                        sb.append(hhhh);
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    @Override
    public String toString() {
        try {
            return this.toString(0);
        } catch (Exception e) {
            return null;
        }
    }

    public String toString(int indentSpaces) throws JSONException {
        StringBuffer sb = new StringBuffer("{");
        Iterator<String> keys = this.keys();
        boolean first = true;
        while (keys.hasNext()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            String key = keys.next();
            sb.append(quote(key));
            sb.append(":");
            Object val = this.map.get(key);
            if (val == null || NULL.equals(val)) {
                sb.append("null");
            } else if (val instanceof JSONObject) {
                sb.append(((JSONObject) val).toString(indentSpaces));
            } else if (val instanceof JSONArray) {
                sb.append(((JSONArray) val).toString(indentSpaces));
            } else if (val instanceof Number || val instanceof Boolean) {
                sb.append(val.toString());
            } else {
                sb.append(quote(val.toString()));
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
