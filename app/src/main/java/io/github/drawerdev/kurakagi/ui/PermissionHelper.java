package io.github.drawerdev.kurakagi.ui;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.lang.reflect.Method;

/**
 * Android 6.0 (API 23) 以降の Dangerous Permission (ランタイム権限) を
 * Android 2.1 (API 7) 互換で安全に処理するリフレクションヘルパー。
 */
public final class PermissionHelper {

    public static final String CAMERA = "android.permission.CAMERA";
    public static final String WRITE_EXTERNAL_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE";
    public static final String READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE";

    public static final int REQUEST_CODE_CAMERA = 1001;
    public static final int REQUEST_CODE_STORAGE = 1002;

    private PermissionHelper() {
    }

    /**
     * 指定された権限が付与されているかチェックする。
     * Android 5.1 以前 (API < 23) はインストール時に付与されるため常に true を返す。
     */
    public static boolean hasPermission(Context context, String permission) {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                Method checkMethod = Context.class.getMethod("checkSelfPermission", String.class);
                int result = (Integer) checkMethod.invoke(context, permission);
                return result == PackageManager.PERMISSION_GRANTED;
            } catch (Exception e) {
                return true;
            }
        }
        return true;
    }

    /**
     * ランタイム権限をリクエストする。
     * Android 5.1 以前 (API < 23) では何もしない。
     */
    public static void requestPermissions(Activity activity, String[] permissions, int requestCode) {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                Method reqMethod = Activity.class.getMethod("requestPermissions", String[].class, int.class);
                reqMethod.invoke(activity, permissions, requestCode);
            } catch (Exception ignore) {
            }
        }
    }
}
