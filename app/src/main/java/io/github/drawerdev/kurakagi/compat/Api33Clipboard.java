package io.github.drawerdev.kurakagi.compat;

import android.annotation.TargetApi;
import android.content.Context;

/**
 * Android 13 (API 33) and later: copies text to the clipboard marked as sensitive, so the
 * system clipboard preview does not show it on screen.
 * Only call this after checking {@code Build.VERSION.SDK_INT >= 33}. The class is loaded only
 * then, so the app still runs on Android 2.1.
 */
@TargetApi(33)
public final class Api33Clipboard {

    private Api33Clipboard() {
    }

    public static boolean copySensitive(Context context, String label, String text) {
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
