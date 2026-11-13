/*
 * Test-only minimal stand-in for Android's org.json, so the unit tests run on a desktop JVM.
 * Not part of the app (build_apk.ps1 compiles app/src/main only). Some error messages match
 * JSON-java (https://github.com/stleary/JSON-java), which is in the Public Domain. See NOTICE.
 */
package org.json;

import java.util.ArrayList;
import java.util.List;

/**
 * Standard JSONArray compatible with Android org.json.
 */
public class JSONArray {
    private final List<Object> myArrayList;

    public JSONArray() {
        this.myArrayList = new ArrayList<Object>();
    }

    public JSONArray(String source) throws JSONException {
        this(new JSONTokener(source));
    }

    public JSONArray(JSONTokener x) throws JSONException {
        this();
        if (x.nextClean() != '[') {
            throw new JSONException("A JSONArray text must start with '['");
        }
        if (x.nextClean() != ']') {
            x.back();
            for (;;) {
                if (x.nextClean() == ',') {
                    x.back();
                    this.myArrayList.add(JSONObject.NULL);
                } else {
                    x.back();
                    this.myArrayList.add(x.nextValue());
                }
                switch (x.nextClean()) {
                    case ';':
                    case ',':
                        if (x.nextClean() == ']') {
                            return;
                        }
                        x.back();
                        break;
                    case ']':
                        return;
                    default:
                        throw new JSONException("Expected a ',' or ']'");
                }
            }
        }
    }

    public int length() {
        return this.myArrayList.size();
    }

    public JSONArray put(Object value) {
        this.myArrayList.add(value == null ? JSONObject.NULL : value);
        return this;
    }

    public JSONArray put(boolean value) {
        return this.put(Boolean.valueOf(value));
    }

    public JSONArray put(int value) {
        return this.put(Integer.valueOf(value));
    }

    public JSONArray put(long value) {
        return this.put(Long.valueOf(value));
    }

    public JSONArray put(double value) {
        return this.put(Double.valueOf(value));
    }

    public Object get(int index) throws JSONException {
        Object object = this.opt(index);
        if (object == null) {
            throw new JSONException("JSONArray[" + index + "] not found.");
        }
        return object;
    }

    public Object opt(int index) {
        return (index < 0 || index >= this.length()) ? null : this.myArrayList.get(index);
    }

    public JSONObject getJSONObject(int index) throws JSONException {
        Object object = this.get(index);
        if (object instanceof JSONObject) {
            return (JSONObject) object;
        }
        throw new JSONException("JSONArray[" + index + "] is not a JSONObject.");
    }

    public JSONObject optJSONObject(int index) {
        Object object = this.opt(index);
        return object instanceof JSONObject ? (JSONObject) object : null;
    }

    public String getString(int index) throws JSONException {
        Object object = this.get(index);
        if (object instanceof String) {
            return (String) object;
        }
        return object.toString();
    }

    public String optString(int index) {
        return this.optString(index, "");
    }

    public String optString(int index, String defaultValue) {
        Object object = this.opt(index);
        return JSONObject.NULL.equals(object) || object == null ? defaultValue : object.toString();
    }

    public int getInt(int index) throws JSONException {
        Object object = this.get(index);
        if (object instanceof Number) {
            return ((Number) object).intValue();
        }
        try {
            return Integer.parseInt((String) object);
        } catch (Exception e) {
            throw new JSONException("JSONArray[" + index + "] is not an int.");
        }
    }

    public long getLong(int index) throws JSONException {
        Object object = this.get(index);
        if (object instanceof Number) {
            return ((Number) object).longValue();
        }
        try {
            return Long.parseLong((String) object);
        } catch (Exception e) {
            throw new JSONException("JSONArray[" + index + "] is not a long.");
        }
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
        StringBuffer sb = new StringBuffer("[");
        int len = this.length();
        for (int i = 0; i < len; i++) {
            if (i > 0) {
                sb.append(",");
            }
            Object val = this.myArrayList.get(i);
            if (val == null || JSONObject.NULL.equals(val)) {
                sb.append("null");
            } else if (val instanceof JSONObject) {
                sb.append(((JSONObject) val).toString(indentSpaces));
            } else if (val instanceof JSONArray) {
                sb.append(((JSONArray) val).toString(indentSpaces));
            } else if (val instanceof Number || val instanceof Boolean) {
                sb.append(val.toString());
            } else {
                sb.append(JSONObject.quote(val.toString()));
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
