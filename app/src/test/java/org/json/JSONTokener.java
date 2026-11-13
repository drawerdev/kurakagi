/*
 * Test-only minimal stand-in for Android's org.json, so the unit tests run on a desktop JVM.
 * Not part of the app (build_apk.ps1 compiles app/src/main only). Some error messages match
 * JSON-java (https://github.com/stleary/JSON-java), which is in the Public Domain. See NOTICE.
 */
package org.json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 * Standard JSONTokener compatible with Android org.json.
 */
public class JSONTokener {
    private long character;
    private boolean eof;
    private long index;
    private long line;
    private char previous;
    private Reader reader;
    private boolean usePrevious;

    public JSONTokener(Reader reader) {
        this.reader = reader.markSupported() ? reader : new BufferedReader(reader);
        this.eof = false;
        this.usePrevious = false;
        this.previous = 0;
        this.index = 0;
        this.character = 1;
        this.line = 1;
    }

    public JSONTokener(String s) {
        this(new StringReader(s));
    }

    public void back() throws JSONException {
        if (this.usePrevious || this.index <= 0) {
            throw new JSONException("Stepping back two steps is not supported");
        }
        this.index--;
        this.character--;
        this.usePrevious = true;
        this.eof = false;
    }

    public boolean end() {
        return this.eof && !this.usePrevious;
    }

    public boolean more() throws JSONException {
        this.next();
        if (this.end()) {
            return false;
        }
        this.back();
        return true;
    }

    public char next() throws JSONException {
        int c;
        if (this.usePrevious) {
            this.usePrevious = false;
            c = this.previous;
        } else {
            try {
                c = this.reader.read();
            } catch (IOException exception) {
                throw new JSONException(exception);
            }
            if (c <= 0) {
                this.eof = true;
                c = 0;
            }
        }
        this.index++;
        if (this.previous == '\r') {
            this.line++;
            this.character = c == '\n' ? 0 : 1;
        } else if (c == '\n') {
            this.line++;
            this.character = 0;
        } else {
            this.character++;
        }
        this.previous = (char) c;
        return this.previous;
    }

    public char next(char c) throws JSONException {
        char n = this.next();
        if (n != c) {
            throw new JSONException("Expected '" + c + "' and instead saw '" + n + "'");
        }
        return n;
    }

    public char nextClean() throws JSONException {
        for (;;) {
            char c = this.next();
            if (c == 0 || c > ' ') {
                return c;
            }
        }
    }

    public String nextString(char quote) throws JSONException {
        StringBuffer sb = new StringBuffer();
        for (;;) {
            char c = this.next();
            switch (c) {
                case 0:
                case '\n':
                case '\r':
                    throw new JSONException("Unterminated string");
                case '\\':
                    c = this.next();
                    switch (c) {
                        case 'b':
                            sb.append('\b');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 'u':
                            try {
                                String hex = new String(new char[] {
                                    this.next(), this.next(), this.next(), this.next()
                                });
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (Exception e) {
                                throw new JSONException("Illegal escape.", e);
                            }
                            break;
                        case '"':
                        case '\'':
                        case '\\':
                        case '/':
                            sb.append(c);
                            break;
                        default:
                            throw new JSONException("Illegal escape.");
                    }
                    break;
                default:
                    if (c == quote) {
                        return sb.toString();
                    }
                    sb.append(c);
            }
        }
    }

    public Object nextValue() throws JSONException {
        char c = this.nextClean();
        switch (c) {
            case '"':
            case '\'':
                return this.nextString(c);
            case '{':
                this.back();
                return new JSONObject(this);
            case '[':
                this.back();
                return new JSONArray(this);
        }

        StringBuffer sb = new StringBuffer();
        while (c >= ' ' && ",:]}/\\\"[{;=#".indexOf(c) < 0) {
            sb.append(c);
            c = this.next();
        }
        this.back();

        String string = sb.toString().trim();
        if (string == null || string.length() == 0) {
            throw new JSONException("Missing value");
        }
        if ("true".equalsIgnoreCase(string)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(string)) {
            return Boolean.FALSE;
        }
        if ("null".equalsIgnoreCase(string)) {
            return JSONObject.NULL;
        }

        char b = string.charAt(0);
        if ((b >= '0' && b <= '9') || b == '-') {
            try {
                if (string.indexOf('.') > -1 || string.indexOf('e') > -1 || string.indexOf('E') > -1) {
                    return Double.valueOf(string);
                }
                Long myLong = Long.valueOf(string);
                if (string.equals(myLong.toString())) {
                    if (myLong.longValue() == (long) myLong.intValue()) {
                        return Integer.valueOf(myLong.intValue());
                    }
                    return myLong;
                }
            } catch (Exception ignore) {
            }
        }
        return string;
    }
}
