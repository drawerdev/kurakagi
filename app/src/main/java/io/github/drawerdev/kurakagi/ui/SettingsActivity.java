package io.github.drawerdev.kurakagi.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import io.github.drawerdev.kurakagi.R;
import io.github.drawerdev.kurakagi.core.TimeSyncManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * 設定画面。
 * 時刻補正、SDカードバックアップ・復元、テーマ・言語切替、アプリ情報・セキュリティの透明性。
 */
public class SettingsActivity extends Activity {

    private static final String APP_VERSION = "1.0.1";
    private static final String EXTRA_SCROLL_Y = "extra_scroll_y";
    private static final String STATE_SCROLL_Y = "state_scroll_y";

    private View layoutRoot;
    private View layoutHeader;
    private View vHeaderDivider;
    private TextView tvTitle;
    private ScrollView scrollView;

    private View layoutTimeCard;
    private TextView tvTimeTitle;
    private TextView tvTimeDesc;
    private View layoutOffsetBox;
    private TextView tvOffsetLabel;
    private TextView tvCurrentOffset;
    private TextView tvNtpStatus;
    private Button btnM10;
    private Button btnM1;
    private Button btnP1;
    private Button btnP10;
    private Button btnReset;
    private Button btnNtp;

    private View layoutBackupCard;
    private TextView tvBackupTitle;
    private TextView tvBackupDesc;
    private Button btnExport;
    private Button btnImport;

    private View layoutThemeCard;
    private TextView tvThemeTitle;
    private TextView tvThemeDesc;
    private Button btnThemeDark;
    private Button btnThemeLight;

    private View layoutLanguageCard;
    private TextView tvLanguageTitle;
    private TextView tvLanguageDesc;
    private Button btnLangAuto;
    private Button btnLangEn;
    private Button btnLangJa;

    private View layoutAboutCard;
    private TextView tvAboutTitle;
    private TextView tvAboutAppVersion;
    private TextView tvAboutDeveloperLabel;
    private TextView tvAboutDeveloperName;
    private View layoutAboutGithub;
    private TextView tvAboutGithubLabel;
    private TextView tvAboutGithubUrl;
    private View vAboutDivider;
    private TextView tvAboutSecurityHeading;
    private TextView tvAboutTrustOffline;
    private TextView tvAboutTrustNoAds;
    private TextView tvAboutTrustNetwork;
    private TextView tvAboutTrustStorage;

    private Button btnBack;

    private TimeSyncManager timeSyncManager;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.applyLocale(this);
        ThemeManager.applyBaseTheme(this, false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        timeSyncManager = TimeSyncManager.getInstance(this);

        bindViews();
        tvAboutAppVersion.setText(getString(
                R.string.about_app_name_version_format,
                getString(R.string.app_name),
                APP_VERSION
        ));

        applyTheme();
        updateOffsetDisplay();
        setupListeners();

        int targetScrollY = 0;
        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_SCROLL_Y)) {
            targetScrollY = savedInstanceState.getInt(STATE_SCROLL_Y, 0);
        } else if (getIntent() != null) {
            targetScrollY = getIntent().getIntExtra(EXTRA_SCROLL_Y, 0);
        }
        restoreScrollPosition(targetScrollY);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (scrollView != null) {
            outState.putInt(STATE_SCROLL_Y, scrollView.getScrollY());
        }
    }

    private void restoreScrollPosition(final int targetScrollY) {
        if (scrollView == null || targetScrollY <= 0) {
            return;
        }
        final ViewTreeObserver observer = scrollView.getViewTreeObserver();
        if (observer == null || !observer.isAlive()) {
            scrollView.post(new Runnable() {
                @Override
                public void run() {
                    if (scrollView != null) {
                        scrollView.scrollTo(0, targetScrollY);
                    }
                }
            });
            return;
        }
        observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            private boolean restored = false;

            @SuppressWarnings("deprecation")
            @Override
            public void onGlobalLayout() {
                if (restored || scrollView.getHeight() == 0) {
                    return;
                }
                restored = true;
                scrollView.scrollTo(0, targetScrollY);
                ViewTreeObserver currentObserver = scrollView.getViewTreeObserver();
                if (currentObserver != null && currentObserver.isAlive()) {
                    currentObserver.removeGlobalOnLayoutListener(this);
                }
            }
        });
    }

    private void bindViews() {
        layoutRoot = findViewById(R.id.layout_settings_root);
        layoutHeader = findViewById(R.id.layout_settings_header);
        vHeaderDivider = findViewById(R.id.v_settings_header_divider);
        tvTitle = (TextView) findViewById(R.id.tv_settings_title);
        btnBack = (Button) findViewById(R.id.btn_settings_back);
        scrollView = (ScrollView) findViewById(R.id.settings_scroll_view);

        layoutTimeCard = findViewById(R.id.layout_settings_time_card);
        tvTimeTitle = (TextView) findViewById(R.id.tv_settings_time_title);
        tvTimeDesc = (TextView) findViewById(R.id.tv_settings_time_desc);
        layoutOffsetBox = findViewById(R.id.layout_settings_offset_box);
        tvOffsetLabel = (TextView) findViewById(R.id.tv_settings_offset_label);
        tvCurrentOffset = (TextView) findViewById(R.id.tv_settings_current_offset);
        tvNtpStatus = (TextView) findViewById(R.id.tv_settings_ntp_status);
        btnM10 = (Button) findViewById(R.id.btn_offset_m10);
        btnM1 = (Button) findViewById(R.id.btn_offset_m1);
        btnP1 = (Button) findViewById(R.id.btn_offset_p1);
        btnP10 = (Button) findViewById(R.id.btn_offset_p10);
        btnReset = (Button) findViewById(R.id.btn_offset_reset);
        btnNtp = (Button) findViewById(R.id.btn_settings_ntp);

        layoutBackupCard = findViewById(R.id.layout_settings_backup_card);
        tvBackupTitle = (TextView) findViewById(R.id.tv_settings_backup_title);
        tvBackupDesc = (TextView) findViewById(R.id.tv_settings_backup_desc);
        btnExport = (Button) findViewById(R.id.btn_settings_export);
        btnImport = (Button) findViewById(R.id.btn_settings_import);

        layoutThemeCard = findViewById(R.id.layout_settings_theme_card);
        tvThemeTitle = (TextView) findViewById(R.id.tv_settings_theme_title);
        tvThemeDesc = (TextView) findViewById(R.id.tv_settings_theme_desc);
        btnThemeDark = (Button) findViewById(R.id.btn_theme_dark);
        btnThemeLight = (Button) findViewById(R.id.btn_theme_light);

        layoutLanguageCard = findViewById(R.id.layout_settings_language_card);
        tvLanguageTitle = (TextView) findViewById(R.id.tv_settings_language_title);
        tvLanguageDesc = (TextView) findViewById(R.id.tv_settings_language_desc);
        btnLangAuto = (Button) findViewById(R.id.btn_lang_auto);
        btnLangEn = (Button) findViewById(R.id.btn_lang_en);
        btnLangJa = (Button) findViewById(R.id.btn_lang_ja);

        layoutAboutCard = findViewById(R.id.layout_settings_about_card);
        tvAboutTitle = (TextView) findViewById(R.id.tv_settings_about_title);
        tvAboutAppVersion = (TextView) findViewById(R.id.tv_about_app_version);
        tvAboutDeveloperLabel = (TextView) findViewById(R.id.tv_about_developer_label);
        tvAboutDeveloperName = (TextView) findViewById(R.id.tv_about_developer_name);
        layoutAboutGithub = findViewById(R.id.layout_about_github);
        tvAboutGithubLabel = (TextView) findViewById(R.id.tv_about_github_label);
        tvAboutGithubUrl = (TextView) findViewById(R.id.tv_about_github_url);
        vAboutDivider = findViewById(R.id.v_about_divider);
        tvAboutSecurityHeading = (TextView) findViewById(R.id.tv_about_security_heading);
        tvAboutTrustOffline = (TextView) findViewById(R.id.tv_about_trust_offline);
        tvAboutTrustNoAds = (TextView) findViewById(R.id.tv_about_trust_no_ads);
        tvAboutTrustNetwork = (TextView) findViewById(R.id.tv_about_trust_network);
        tvAboutTrustStorage = (TextView) findViewById(R.id.tv_about_trust_storage);
    }

    private void applyTheme() {
        int textPrimary = ThemeManager.getTextPrimaryColor(this);
        int textSecondary = ThemeManager.getTextSecondaryColor(this);
        int textMuted = ThemeManager.getTextMutedColor(this);
        int accentPrimary = ThemeManager.getAccentPrimaryColor(this);
        int dividerColor = ThemeManager.getDividerColor(this);
        int cardBgRes = ThemeManager.getCardBgRes(this);
        int slotBgRes = ThemeManager.getSlotBoxBgRes(this);
        int primaryBtnBgRes = ThemeManager.getBtnPrimaryBgRes(this);
        int secondaryBtnBgRes = ThemeManager.getBtnSecondaryBgRes(this);
        int compactBtnBgRes = ThemeManager.getBtnCompactBgRes(this);
        int primaryBtnText = ThemeManager.getPrimaryButtonTextColor(this);
        int secondaryBtnText = ThemeManager.getSecondaryButtonTextColor(this);
        int headerBtnText = ThemeManager.getHeaderButtonTextColor(this);

        layoutRoot.setBackgroundColor(ThemeManager.getBgColor(this));
        layoutHeader.setBackgroundColor(ThemeManager.getSurfaceColor(this));
        vHeaderDivider.setBackgroundColor(dividerColor);
        tvTitle.setTextColor(textPrimary);

        setBackgroundKeepPadding(btnBack, compactBtnBgRes);
        btnBack.setTextColor(headerBtnText);

        setBackgroundKeepPadding(layoutTimeCard, cardBgRes);
        setBackgroundKeepPadding(layoutBackupCard, cardBgRes);
        setBackgroundKeepPadding(layoutThemeCard, cardBgRes);
        setBackgroundKeepPadding(layoutLanguageCard, cardBgRes);
        setBackgroundKeepPadding(layoutAboutCard, cardBgRes);

        tvTimeTitle.setTextColor(textPrimary);
        tvTimeDesc.setTextColor(textMuted);
        setBackgroundKeepPadding(layoutOffsetBox, slotBgRes);
        tvOffsetLabel.setTextColor(textSecondary);

        setBackgroundKeepPadding(btnM10, secondaryBtnBgRes);
        btnM10.setTextColor(secondaryBtnText);
        setBackgroundKeepPadding(btnM1, secondaryBtnBgRes);
        btnM1.setTextColor(secondaryBtnText);
        setBackgroundKeepPadding(btnP1, secondaryBtnBgRes);
        btnP1.setTextColor(secondaryBtnText);
        setBackgroundKeepPadding(btnP10, secondaryBtnBgRes);
        btnP10.setTextColor(secondaryBtnText);

        setBackgroundKeepPadding(btnReset, compactBtnBgRes);
        btnReset.setTextColor(headerBtnText);

        setBackgroundKeepPadding(btnNtp, primaryBtnBgRes);
        btnNtp.setTextColor(primaryBtnText);
        tvNtpStatus.setTextColor(textMuted);

        tvBackupTitle.setTextColor(textPrimary);
        tvBackupDesc.setTextColor(textMuted);
        setBackgroundKeepPadding(btnExport, secondaryBtnBgRes);
        btnExport.setTextColor(secondaryBtnText);
        setBackgroundKeepPadding(btnImport, secondaryBtnBgRes);
        btnImport.setTextColor(secondaryBtnText);

        tvThemeTitle.setTextColor(textPrimary);
        tvThemeDesc.setTextColor(textMuted);
        boolean isLight = ThemeManager.isLightTheme(this);
        styleOptionButton(btnThemeDark, !isLight, primaryBtnBgRes, secondaryBtnBgRes, primaryBtnText, secondaryBtnText);
        styleOptionButton(btnThemeLight, isLight, primaryBtnBgRes, secondaryBtnBgRes, primaryBtnText, secondaryBtnText);

        tvLanguageTitle.setTextColor(textPrimary);
        tvLanguageDesc.setTextColor(textMuted);
        String currentLang = LocaleHelper.getLanguage(this);
        styleOptionButton(btnLangAuto, LocaleHelper.LANG_AUTO.equals(currentLang),
                primaryBtnBgRes, secondaryBtnBgRes, primaryBtnText, secondaryBtnText);
        styleOptionButton(btnLangEn, LocaleHelper.LANG_EN.equals(currentLang),
                primaryBtnBgRes, secondaryBtnBgRes, primaryBtnText, secondaryBtnText);
        styleOptionButton(btnLangJa, LocaleHelper.LANG_JA.equals(currentLang),
                primaryBtnBgRes, secondaryBtnBgRes, primaryBtnText, secondaryBtnText);

        tvAboutTitle.setTextColor(textPrimary);
        tvAboutAppVersion.setTextColor(textPrimary);
        tvAboutDeveloperLabel.setTextColor(textSecondary);
        tvAboutDeveloperName.setTextColor(textPrimary);

        setBackgroundKeepPadding(layoutAboutGithub, slotBgRes);
        tvAboutGithubLabel.setTextColor(textSecondary);
        tvAboutGithubUrl.setTextColor(accentPrimary);

        vAboutDivider.setBackgroundColor(dividerColor);
        tvAboutSecurityHeading.setTextColor(textPrimary);
        tvAboutTrustOffline.setTextColor(textSecondary);
        tvAboutTrustNoAds.setTextColor(textSecondary);
        tvAboutTrustNetwork.setTextColor(textSecondary);
        tvAboutTrustStorage.setTextColor(textSecondary);
    }

    private void styleOptionButton(Button button, boolean selected,
            int activeBgRes, int inactiveBgRes, int activeTextColor, int inactiveTextColor) {
        setBackgroundKeepPadding(button, selected ? activeBgRes : inactiveBgRes);
        button.setTextColor(selected ? activeTextColor : inactiveTextColor);
        button.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    private void setBackgroundKeepPadding(View view, int resId) {
        if (view == null) return;
        int left = view.getPaddingLeft();
        int top = view.getPaddingTop();
        int right = view.getPaddingRight();
        int bottom = view.getPaddingBottom();
        view.setBackgroundResource(resId);
        view.setPadding(left, top, right, bottom);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // ステッパーボタン
        btnM10.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                timeSyncManager.addTimeOffsetSeconds(-10);
                updateOffsetDisplay();
            }
        });

        btnM1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                timeSyncManager.addTimeOffsetSeconds(-1);
                updateOffsetDisplay();
            }
        });

        btnP1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                timeSyncManager.addTimeOffsetSeconds(1);
                updateOffsetDisplay();
            }
        });

        btnP10.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                timeSyncManager.addTimeOffsetSeconds(10);
                updateOffsetDisplay();
            }
        });

        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                timeSyncManager.resetOffset();
                updateOffsetDisplay();
                Toast.makeText(SettingsActivity.this, R.string.toast_offset_reset, Toast.LENGTH_SHORT).show();
            }
        });

        // NTP 同期
        btnNtp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvNtpStatus.setText(R.string.ntp_status_connecting);
                tvNtpStatus.setTextColor(ThemeManager.getAccentPrimaryColor(SettingsActivity.this));
                timeSyncManager.syncWithNtp(null, new TimeSyncManager.SyncCallback() {
                    @Override
                    public void onSuccess(long offsetMs) {
                        int sec = (int) (offsetMs / 1000L);
                        String sign = sec >= 0 ? "+" : "";
                        tvNtpStatus.setText(getString(R.string.ntp_status_success_format, sign, sec));
                        tvNtpStatus.setTextColor(ThemeManager.getTimerSafeColor(SettingsActivity.this));
                        updateOffsetDisplay();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        tvNtpStatus.setText(getString(R.string.ntp_status_error_format, errorMessage));
                        tvNtpStatus.setTextColor(ThemeManager.getTimerDangerColor(SettingsActivity.this));
                    }
                });
            }
        });

        // SDカードエクスポート
        btnExport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportBackup();
            }
        });

        // SDカードインポート
        btnImport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importBackup();
            }
        });

        // テーマ切替
        btnThemeDark.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTheme(false);
            }
        });

        btnThemeLight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTheme(true);
            }
        });

        // 言語切替
        btnLangAuto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchLanguage(LocaleHelper.LANG_AUTO);
            }
        });

        btnLangEn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchLanguage(LocaleHelper.LANG_EN);
            }
        });

        btnLangJa.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchLanguage(LocaleHelper.LANG_JA);
            }
        });

        // GitHub URL
        layoutAboutGithub.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openGithubUrl();
            }
        });

        layoutAboutGithub.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (copyToClipboard(getString(R.string.about_github_url))) {
                    Toast.makeText(SettingsActivity.this, R.string.about_github_copied, Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            }
        });
    }

    private void switchTheme(boolean light) {
        if (ThemeManager.isLightTheme(this) == light) {
            return;
        }
        ThemeManager.setLightTheme(this, light);
        Toast.makeText(
                this,
                light ? R.string.toast_theme_changed_light : R.string.toast_theme_changed_dark,
                Toast.LENGTH_SHORT
        ).show();
        restartSelf();
    }

    private void switchLanguage(String lang) {
        if (lang.equals(LocaleHelper.getLanguage(this))) {
            return;
        }
        LocaleHelper.setLanguage(this, lang);
        LocaleHelper.applyLocale(this);
        restartSelf();
    }

    private void restartSelf() {
        Intent intent = new Intent(getIntent());
        if (scrollView != null) {
            intent.putExtra(EXTRA_SCROLL_Y, scrollView.getScrollY());
        }
        finish();
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void openGithubUrl() {
        String url = getString(R.string.about_github_url);
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            if (copyToClipboard(url)) {
                Toast.makeText(
                        this,
                        getString(R.string.about_github_open_error) + "\n" + getString(R.string.about_github_copied),
                        Toast.LENGTH_LONG
                ).show();
            } else {
                Toast.makeText(this, R.string.about_github_open_error, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @SuppressWarnings("deprecation")
    private boolean copyToClipboard(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setText(text);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void updateOffsetDisplay() {
        int sec = timeSyncManager.getTimeOffsetSeconds();
        if (sec == 0) {
            tvCurrentOffset.setText(R.string.settings_offset_exact);
            tvCurrentOffset.setTextColor(ThemeManager.getAccentPrimaryColor(this));
        } else {
            String sign = sec > 0 ? "+" : "";
            tvCurrentOffset.setText(getString(R.string.settings_offset_format, sign, sec));
            tvCurrentOffset.setTextColor(ThemeManager.getTimerWarningColor(this));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Backup and restore
    //
    // Backups are always encrypted (format v2: PBKDF2-HMAC-SHA256 + AES-256-CBC + HMAC-SHA256).
    // Android 4.4+ (API 19): the system file picker (Storage Access Framework). No storage
    //   permission is needed, and it keeps working on Android 10+ where direct SD access is blocked.
    // Android 4.3 and older: /sdcard/Kurakagi/ as before. The file is encrypted, so other apps
    //   that can read the SD card only see ciphertext.
    // Key derivation takes seconds on old phones, so it runs off the UI thread.
    // ---------------------------------------------------------------------------------------------

    private static final int REQUEST_CREATE_BACKUP = 2001;
    private static final int REQUEST_OPEN_BACKUP = 2002;
    private static final int MIN_PASSWORD_LENGTH = 8;

    /** Encrypted bytes waiting for the user to choose where to save them (API 19+). */
    private byte[] pendingBackup;

    private void exportBackup() {
        askNewBackupPassword();
    }

    private EditText newPasswordField(int hintRes) {
        EditText field = new EditText(this);
        // setSingleLine() would replace the password masking, so do not call it.
        // TYPE_CLASS_TEXT without MULTI_LINE is already one line.
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setTransformationMethod(PasswordTransformationMethod.getInstance());
        field.setHint(hintRes);
        return field;
    }

    private void askNewBackupPassword() {
        final EditText pass1 = newPasswordField(R.string.hint_backup_password);
        final EditText pass2 = newPasswordField(R.string.hint_backup_password_confirm);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(pass1);
        box.addView(pass2);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_backup_password_title)
                .setMessage(R.string.dialog_backup_password_msg)
                .setView(box)
                .setPositiveButton(R.string.btn_backup_create, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String a = pass1.getText().toString();
                        String b = pass2.getText().toString();
                        if (a.length() < MIN_PASSWORD_LENGTH) {
                            Toast.makeText(SettingsActivity.this, R.string.toast_password_too_short, Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (!a.equals(b)) {
                            Toast.makeText(SettingsActivity.this, R.string.toast_password_mismatch, Toast.LENGTH_LONG).show();
                            return;
                        }
                        encryptAndSave(a.toCharArray());
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void encryptAndSave(final char[] password) {
        final ProgressDialog progress = ProgressDialog.show(this, null, getString(R.string.progress_encrypting), true, false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] data = null;
                Exception error = null;
                try {
                    data = TokenStorageHelper.buildEncryptedBackup(SettingsActivity.this, password);
                } catch (Exception e) {
                    error = e;
                } finally {
                    Arrays.fill(password, '\0');
                }
                final byte[] result = data;
                final Exception failure = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dismissQuietly(progress);
                        if (failure != null || result == null) {
                            showError(failure);
                            return;
                        }
                        saveEncryptedBackup(result);
                    }
                });
            }
        }).start();
    }

    private void saveEncryptedBackup(byte[] encrypted) {
        if (Build.VERSION.SDK_INT >= 19) {
            pendingBackup = encrypted;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_TITLE, TokenStorageHelper.newBackupFileName());
            try {
                startActivityForResult(intent, REQUEST_CREATE_BACKUP);
            } catch (ActivityNotFoundException e) {
                pendingBackup = null;
                Toast.makeText(this, R.string.toast_no_file_picker, Toast.LENGTH_LONG).show();
            }
            return;
        }
        try {
            File file = TokenStorageHelper.writeBackupToSdCard(encrypted);
            Toast.makeText(this, getString(R.string.toast_export_success_format, file.getAbsolutePath()), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.toast_export_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CREATE_BACKUP) {
            byte[] encrypted = pendingBackup;
            pendingBackup = null;
            if (resultCode != RESULT_OK || data == null || data.getData() == null || encrypted == null) {
                return;
            }
            try {
                OutputStream out = getContentResolver().openOutputStream(data.getData());
                if (out == null) throw new IOException("Cannot open the chosen file");
                try {
                    out.write(encrypted);
                    out.flush();
                } finally {
                    out.close();
                }
                Toast.makeText(this, R.string.toast_export_saved, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, R.string.toast_export_failed, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_OPEN_BACKUP) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                return;
            }
            try {
                InputStream in = getContentResolver().openInputStream(data.getData());
                if (in == null) throw new IOException("Cannot open the chosen file");
                handleBackupBytes(TokenStorageHelper.readAll(in));
            } catch (Exception e) {
                showImportError(e);
            }
        }
    }

    private void handleBackupBytes(final byte[] bytes) {
        if (!TokenStorageHelper.needsPassword(bytes)) {
            // Plain JSON written by the early test version. Accept it once, and tell the user to delete it.
            try {
                int count = TokenStorageHelper.importBackupBytes(this, bytes, null);
                Toast.makeText(this, getString(R.string.toast_import_success_format, count), Toast.LENGTH_LONG).show();
                new AlertDialog.Builder(this)
                        .setTitle(R.string.dialog_plain_backup_title)
                        .setMessage(R.string.dialog_plain_backup_msg)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            } catch (Exception e) {
                showImportError(e);
            }
            return;
        }
        final EditText pass = newPasswordField(R.string.hint_backup_password);
        LinearLayout box = new LinearLayout(this);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(pass, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_restore_password_title)
                .setView(box)
                .setPositiveButton(R.string.btn_restore, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        decryptAndImport(bytes, pass.getText().toString().toCharArray());
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void decryptAndImport(final byte[] bytes, final char[] password) {
        final ProgressDialog progress = ProgressDialog.show(this, null, getString(R.string.progress_decrypting), true, false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                int count = -1;
                Exception error = null;
                try {
                    count = TokenStorageHelper.importBackupBytes(SettingsActivity.this, bytes, password);
                } catch (Exception e) {
                    error = e;
                } finally {
                    Arrays.fill(password, '\0');
                }
                final int restored = count;
                final Exception failure = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dismissQuietly(progress);
                        if (failure != null) {
                            showImportError(failure);
                        } else {
                            Toast.makeText(SettingsActivity.this,
                                    getString(R.string.toast_import_success_format, restored), Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void showImportError(Exception e) {
        if (e instanceof GeneralSecurityException) {
            Toast.makeText(this, R.string.toast_wrong_password, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, getString(R.string.toast_import_error_format,
                    e == null ? "" : e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void showError(Exception e) {
        Toast.makeText(this, getString(R.string.toast_error_format, e == null ? "" : e.getMessage()), Toast.LENGTH_LONG).show();
    }

    private static void dismissQuietly(ProgressDialog dialog) {
        try {
            dialog.dismiss();
        } catch (Exception ignored) {
            // the activity may already be gone
        }
    }

    private void importBackup() {
        if (Build.VERSION.SDK_INT >= 19) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            try {
                startActivityForResult(intent, REQUEST_OPEN_BACKUP);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.toast_no_file_picker, Toast.LENGTH_LONG).show();
            }
            return;
        }
        final File backupDir = TokenStorageHelper.getBackupDirectory();
        if (backupDir == null || !backupDir.exists()) {
            Toast.makeText(this, R.string.toast_backup_dir_missing, Toast.LENGTH_SHORT).show();
            return;
        }

        final File[] files = backupDir.listFiles();
        if (files == null || files.length == 0) {
            Toast.makeText(this, R.string.toast_no_backup_files, Toast.LENGTH_SHORT).show();
            return;
        }

        final String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            names[i] = files[i].getName();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_import_title);
        builder.setItems(names, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                File target = files[which];
                try {
                    handleBackupBytes(TokenStorageHelper.readAll(new FileInputStream(target)));
                } catch (Exception e) {
                    showImportError(e);
                }
            }
        });
        builder.setNegativeButton(R.string.btn_cancel, null);
        builder.show();
    }
}
