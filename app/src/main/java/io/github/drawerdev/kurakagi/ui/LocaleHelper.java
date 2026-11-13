package io.github.drawerdev.kurakagi.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

/**
 * Runtime locale switcher supporting "auto" (system default), "en", and "ja" across API 7-34.
 */
public final class LocaleHelper {

    public static final String PREFS_NAME = "kurakagi_prefs";
    public static final String KEY_APP_LANGUAGE = "app_language";

    public static final String LANG_AUTO = "auto";
    public static final String LANG_EN = "en";
    public static final String LANG_JA = "ja";

    private static Locale sSystemDefaultLocale;

    private LocaleHelper() {
    }

    public static String getLanguage(Context context) {
        if (context == null) {
            return LANG_AUTO;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return normalizeLanguage(prefs.getString(KEY_APP_LANGUAGE, LANG_AUTO));
    }

    public static void setLanguage(Context context, String lang) {
        if (context == null) {
            return;
        }
        String normalized = normalizeLanguage(lang);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Use commit() rather than apply() for API 7 compatibility.
        prefs.edit().putString(KEY_APP_LANGUAGE, normalized).commit();
    }

    /**
     * Applies the persisted language preference to the given Context/Resources.
     * Safe to call in Activity.onCreate() before setContentView(), or inside
     * attachBaseContext(Context) via {@code super.attachBaseContext(LocaleHelper.applyLocale(newBase))}.
     */
    @SuppressWarnings("deprecation")
    public static Context applyLocale(Context context) {
        if (context == null) {
            return null;
        }
        Resources res = context.getResources();
        captureSystemLocaleIfNeeded(res);

        String lang = getLanguage(context);
        Locale targetLocale = resolveLocale(lang);
        Locale.setDefault(targetLocale);

        Configuration config = new Configuration(res.getConfiguration());
        if (Build.VERSION.SDK_INT >= 17) {
            config.setLocale(targetLocale);
        } else {
            config.locale = targetLocale;
        }

        res.updateConfiguration(config, res.getDisplayMetrics());

        if (Build.VERSION.SDK_INT >= 17 && !(context instanceof Activity)) {
            return context.createConfigurationContext(config);
        }
        return context;
    }

    private static String normalizeLanguage(String lang) {
        if (LANG_EN.equals(lang)) {
            return LANG_EN;
        }
        if (LANG_JA.equals(lang)) {
            return LANG_JA;
        }
        return LANG_AUTO;
    }

    @SuppressWarnings("deprecation")
    private static synchronized void captureSystemLocaleIfNeeded(Resources res) {
        if (sSystemDefaultLocale != null) {
            return;
        }
        Configuration config = res.getConfiguration();
        if (Build.VERSION.SDK_INT >= 24) {
            if (!config.getLocales().isEmpty()) {
                sSystemDefaultLocale = config.getLocales().get(0);
            }
        } else {
            sSystemDefaultLocale = config.locale;
        }
        if (sSystemDefaultLocale == null) {
            sSystemDefaultLocale = Locale.getDefault();
        }
    }

    private static Locale resolveLocale(String lang) {
        if (LANG_EN.equals(lang)) {
            return Locale.ENGLISH;
        }
        if (LANG_JA.equals(lang)) {
            return Locale.JAPANESE;
        }
        return sSystemDefaultLocale != null ? sSystemDefaultLocale : Locale.getDefault();
    }
}
