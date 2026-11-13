package io.github.drawerdev.kurakagi.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.widget.ProgressBar;

import io.github.drawerdev.kurakagi.R;

/**
 * Dual-theme manager for Kurakagi (API 7-34).
 * Supports Dark ("Calm Vault Slate", default) and Light ("Clean Ledger Paper").
 */
public final class ThemeManager {

    public static final String PREFS_NAME = "kurakagi_prefs";
    public static final String KEY_LIGHT_THEME = "light_theme";

    private static final int COLOR_QR_WHITE_BG = 0xFFFFFFFF;
    private static final int COLOR_SCAN_OVERLAY = 0xCC111827;
    private static final int COLOR_SCAN_MASK = 0x88111827;
    private static final int COLOR_SCAN_FRAME = 0x5560A5FA;
    private static final int COLOR_SCAN_CORNER = 0xFF60A5FA;
    private static final int COLOR_SCAN_LASER = 0xCC2DD4BF;

    private ThemeManager() {
    }

    public static boolean isLightTheme(Context context) {
        if (context == null) {
            return false;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_LIGHT_THEME, false);
    }

    public static void setLightTheme(Context context, boolean light) {
        if (context == null) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Use commit() rather than apply() for API 7 compatibility.
        prefs.edit().putBoolean(KEY_LIGHT_THEME, light).commit();
    }

    public static void applyBaseTheme(Activity activity, boolean fullscreen) {
        if (activity == null) {
            return;
        }
        boolean light = isLightTheme(activity);
        int themeRes;
        if (fullscreen) {
            themeRes = light
                    ? R.style.Theme_Kurakagi_Light_Fullscreen
                    : R.style.Theme_Kurakagi_Dark_Fullscreen;
        } else {
            themeRes = light
                    ? R.style.Theme_Kurakagi_Light
                    : R.style.Theme_Kurakagi_Dark;
        }
        activity.setTheme(themeRes);
    }

    @SuppressWarnings("deprecation")
    private static int resolveColor(Context context, int darkRes, int lightRes) {
        int resId = isLightTheme(context) ? lightRes : darkRes;
        return context.getResources().getColor(resId);
    }

    public static int getBgColor(Context context) {
        return resolveColor(context, R.color.dark_bg, R.color.light_bg);
    }

    public static int getSurfaceColor(Context context) {
        return resolveColor(context, R.color.dark_surface, R.color.light_surface);
    }

    public static int getSurfaceAltColor(Context context) {
        return resolveColor(context, R.color.dark_surface_alt, R.color.light_surface_alt);
    }

    public static int getSurfaceHighlightColor(Context context) {
        return resolveColor(context, R.color.dark_surface_highlight, R.color.light_surface_highlight);
    }

    public static int getTextPrimaryColor(Context context) {
        return resolveColor(context, R.color.dark_text_primary, R.color.light_text_primary);
    }

    public static int getTextSecondaryColor(Context context) {
        return resolveColor(context, R.color.dark_text_secondary, R.color.light_text_secondary);
    }

    public static int getTextMutedColor(Context context) {
        return resolveColor(context, R.color.dark_text_muted, R.color.light_text_muted);
    }

    public static int getAccentPrimaryColor(Context context) {
        return resolveColor(context, R.color.dark_accent_primary, R.color.light_accent_primary);
    }

    public static int getAccentHeaderColor(Context context) {
        return resolveColor(context, R.color.dark_accent_header, R.color.light_accent_header);
    }

    public static int getAccentSecondaryColor(Context context) {
        return resolveColor(context, R.color.dark_accent_secondary, R.color.light_accent_secondary);
    }

    public static int getTimerSafeColor(Context context) {
        return resolveColor(context, R.color.dark_safe, R.color.light_safe);
    }

    public static int getTimerWarningColor(Context context) {
        return resolveColor(context, R.color.dark_warning, R.color.light_warning);
    }

    public static int getTimerDangerColor(Context context) {
        return resolveColor(context, R.color.dark_danger, R.color.light_danger);
    }

    public static int getBorderColor(Context context) {
        return resolveColor(context, R.color.dark_border, R.color.light_border);
    }

    public static int getBorderActiveColor(Context context) {
        return resolveColor(context, R.color.dark_border_active, R.color.light_border_active);
    }

    public static int getDividerColor(Context context) {
        return resolveColor(context, R.color.dark_divider, R.color.light_divider);
    }

    public static int getPrimaryButtonTextColor(Context context) {
        return resolveColor(context, R.color.dark_text_on_primary, R.color.light_text_on_primary);
    }

    public static int getSecondaryButtonTextColor(Context context) {
        return resolveColor(context, R.color.dark_text_primary, R.color.light_text_primary);
    }

    public static int getHeaderButtonTextColor(Context context) {
        return resolveColor(context, R.color.dark_text_secondary, R.color.light_text_secondary);
    }

    public static int getDangerButtonTextColor(Context context) {
        return resolveColor(context, R.color.dark_text_on_primary, R.color.light_text_on_primary);
    }

    public static int getQrBackgroundColor() {
        return COLOR_QR_WHITE_BG;
    }

    public static int getScanOverlayColor() {
        return COLOR_SCAN_OVERLAY;
    }

    public static int getScanMaskColor() {
        return COLOR_SCAN_MASK;
    }

    public static int getScanFrameColor() {
        return COLOR_SCAN_FRAME;
    }

    public static int getScanCornerColor() {
        return COLOR_SCAN_CORNER;
    }

    public static int getScanLaserColor() {
        return COLOR_SCAN_LASER;
    }

    public static int getCardBgRes(Context context) {
        return isLightTheme(context) ? R.drawable.bg_card_light : R.drawable.bg_card_dark;
    }

    public static int getCardHighlightBgRes(Context context) {
        return isLightTheme(context)
                ? R.drawable.bg_card_highlight_light
                : R.drawable.bg_card_highlight_dark;
    }

    public static int getBtnPrimaryBgRes(Context context) {
        return isLightTheme(context)
                ? R.drawable.bg_btn_primary_light
                : R.drawable.bg_btn_primary_dark;
    }

    public static int getBtnSecondaryBgRes(Context context) {
        return isLightTheme(context)
                ? R.drawable.bg_btn_secondary_light
                : R.drawable.bg_btn_secondary_dark;
    }

    public static int getBtnCompactBgRes(Context context) {
        return isLightTheme(context)
                ? R.drawable.bg_btn_compact_light
                : R.drawable.bg_btn_compact_dark;
    }

    public static int getBtnDangerBgRes(Context context) {
        return isLightTheme(context)
                ? R.drawable.bg_btn_danger_light
                : R.drawable.bg_btn_danger_dark;
    }

    public static int getEditTextBgRes(Context context) {
        return isLightTheme(context)
                ? R.drawable.bg_edit_text_light
                : R.drawable.bg_edit_text_dark;
    }

    public static int getSlotBoxBgRes(Context context) {
        return isLightTheme(context)
                ? R.drawable.bg_slot_box_light
                : R.drawable.bg_slot_box_dark;
    }

    public static int getSlotBoxActiveBgRes(Context context) {
        return isLightTheme(context)
                ? R.drawable.bg_slot_box_active_light
                : R.drawable.bg_slot_box_active_dark;
    }

    public static int getProgressDrawableRes(Context context) {
        return isLightTheme(context)
                ? R.drawable.progress_bar_light
                : R.drawable.progress_bar_dark;
    }

    @SuppressWarnings("deprecation")
    public static void applyProgressDrawable(ProgressBar bar, Context context) {
        if (bar == null || context == null) {
            return;
        }
        Drawable oldDrawable = bar.getProgressDrawable();
        Rect bounds = oldDrawable != null ? oldDrawable.copyBounds() : null;
        Drawable newDrawable = context.getResources().getDrawable(getProgressDrawableRes(context));
        if (bounds != null && !bounds.isEmpty()) {
            newDrawable.setBounds(bounds);
        }
        int progress = bar.getProgress();
        bar.setProgressDrawable(newDrawable);
        bar.setProgress(progress);
    }
}
