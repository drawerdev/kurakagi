package io.github.drawerdev.kurakagi.ui;

import android.content.Context;
import android.os.Build;

/**
 * Copies text to the clipboard. On Android 13 (API 33) and later the clip is marked as
 * sensitive, so the system clipboard preview does not show it on screen.
 * The API 33 classes live in a nested class that is only loaded on API 33+,
 * so this file still runs on Android 2.1.
 */
final class ClipboardCompat {

    private ClipboardCompat() {
    }

    @SuppressWarnings("deprecation")
    static boolean copy(Context context, String label, String text, boolean sensitive) {
        try {
            if (sensitive && Build.VERSION.SDK_INT >= 33) {
                return Api33.copySensitive(context, label, text);
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

    private static final class Api33 {
        static boolean copySensitive(Context context, String label, String text) {
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) return false;
            android.content.ClipData clip = android.content.ClipData.newPlainText(label, text);
            android.os.PersistableBundle extras = new android.os.PersistableBundle();
            extras.putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true);
            clip.getDescription().setExtras(extras);
            cm.setPrimaryClip(clip);
            return true;
        }
    }
}
