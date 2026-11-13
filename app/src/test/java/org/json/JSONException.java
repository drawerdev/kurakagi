/*
 * Test-only minimal stand-in for Android's org.json, so the unit tests run on a desktop JVM.
 * Not part of the app (build_apk.ps1 compiles app/src/main only). Some error messages match
 * JSON-java (https://github.com/stleary/JSON-java), which is in the Public Domain. See NOTICE.
 */
package org.json;

/**
 * Standard JSONException compatible with Android org.json.
 */
public class JSONException extends Exception {
    private static final long serialVersionUID = 1L;

    public JSONException(String message) {
        super(message);
    }

    public JSONException(String message, Throwable cause) {
        super(message, cause);
    }

    public JSONException(Throwable cause) {
        super(cause);
    }
}
