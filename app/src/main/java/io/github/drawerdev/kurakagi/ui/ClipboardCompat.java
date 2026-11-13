package io.github.drawerdev.kurakagi.ui;

import android.content.Context;
import android.os.Build;

import io.github.drawerdev.kurakagi.compat.Api33Clipboard;

/**
 * Copies text to the clipboard. On Android 13 (API 33) and later a sensitive clip is marked as
 * such, so the system clipboard preview does not show it on screen.
 * The API 33 code lives in {@link Api33Clipboard}, which is only loaded on API 33+,
 * so this file still runs on Android 2.1.
 */
final class ClipboardCompat {

    private ClipboardCompat() {
    }

    @SuppressWarnings("deprecation")
    static boolean copy(Context context, String label, String text, boolean sensitive) {
        try {
            if (sensitive && Build.VERSION.SDK_INT >= 33) {
                return Api33Clipboard.copySensitive(context, label, text);
            }
            android.text.ClipboardManager cm =
                    (android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) return false;
            cm.setText(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
