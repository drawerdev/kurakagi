package io.github.drawerdev.kurakagi.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import io.github.drawerdev.kurakagi.R;
import io.github.drawerdev.kurakagi.core.Base32;
import io.github.drawerdev.kurakagi.storage.Account;

/**
 * TOTP アカウントの手動登録画面。
 * Base32 バリデーション、詳細パラメータ（桁数・更新周期・ハッシュ）のカスタマイズに対応。
 */
public class ManualAddActivity extends Activity {

    private View layoutRoot;
    private View layoutHeader;
    private View vHeaderDivider;
    private TextView tvTitle;
    private View layoutFormCard;
    private TextView tvLabelAccountName;
    private TextView tvLabelIssuer;
    private TextView tvLabelSecret;
    private TextView tvSecretFormatNote;
    private View layoutAdvancedCard;
    private TextView tvLabelDigits;
    private TextView tvLabelPeriod;
    private TextView tvLabelAlgorithm;

    private EditText etAccountName;
    private EditText etIssuer;
    private EditText etSecret;
    private Button btnToggleAdvanced;
    private View layoutAdvancedOptions;
    private RadioButton rbDigits6;
    private RadioButton rbDigits8;
    private RadioButton rbPeriod30;
    private RadioButton rbPeriod60;
    private RadioButton rbAlgoSha1;
    private RadioButton rbAlgoSha256;
    private RadioButton rbAlgoSha512;
    private Button btnSave;
    private Button btnBack;

    private boolean isAdvancedVisible = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.applyLocale(this);
        ThemeManager.applyBaseTheme(this, false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_add);

        layoutRoot = findViewById(R.id.layout_manual_root);
        layoutHeader = findViewById(R.id.layout_manual_header);
        vHeaderDivider = findViewById(R.id.v_manual_header_divider);
        tvTitle = (TextView) findViewById(R.id.tv_manual_title);
        layoutFormCard = findViewById(R.id.layout_manual_form_card);
        tvLabelAccountName = (TextView) findViewById(R.id.tv_label_account_name);
        tvLabelIssuer = (TextView) findViewById(R.id.tv_label_issuer);
        tvLabelSecret = (TextView) findViewById(R.id.tv_label_secret);
        tvSecretFormatNote = (TextView) findViewById(R.id.tv_secret_format_note);
        layoutAdvancedCard = findViewById(R.id.layout_manual_advanced_card);
        tvLabelDigits = (TextView) findViewById(R.id.tv_label_digits);
        tvLabelPeriod = (TextView) findViewById(R.id.tv_label_period);
        tvLabelAlgorithm = (TextView) findViewById(R.id.tv_label_algorithm);

        etAccountName = (EditText) findViewById(R.id.et_account_name);
        etIssuer = (EditText) findViewById(R.id.et_issuer);
        etSecret = (EditText) findViewById(R.id.et_secret);
        btnToggleAdvanced = (Button) findViewById(R.id.btn_toggle_advanced);
        layoutAdvancedOptions = findViewById(R.id.layout_advanced_options);
        rbDigits6 = (RadioButton) findViewById(R.id.rb_digits_6);
        rbDigits8 = (RadioButton) findViewById(R.id.rb_digits_8);
        rbPeriod30 = (RadioButton) findViewById(R.id.rb_period_30);
        rbPeriod60 = (RadioButton) findViewById(R.id.rb_period_60);
        rbAlgoSha1 = (RadioButton) findViewById(R.id.rb_algo_sha1);
        rbAlgoSha256 = (RadioButton) findViewById(R.id.rb_algo_sha256);
        rbAlgoSha512 = (RadioButton) findViewById(R.id.rb_algo_sha512);
        btnSave = (Button) findViewById(R.id.btn_save_account);
        btnBack = (Button) findViewById(R.id.btn_manual_back);

        applyTheme();

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnToggleAdvanced.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isAdvancedVisible = !isAdvancedVisible;
                layoutAdvancedOptions.setVisibility(isAdvancedVisible ? View.VISIBLE : View.GONE);
                btnToggleAdvanced.setText(isAdvancedVisible ? R.string.btn_advanced_hide : R.string.btn_advanced_show);
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAccount();
            }
        });
    }

    private void applyTheme() {
        int textPrimary = ThemeManager.getTextPrimaryColor(this);
        int textSecondary = ThemeManager.getTextSecondaryColor(this);
        int textMuted = ThemeManager.getTextMutedColor(this);
        int accentPrimary = ThemeManager.getAccentPrimaryColor(this);

        layoutRoot.setBackgroundColor(ThemeManager.getBgColor(this));
        layoutHeader.setBackgroundColor(ThemeManager.getSurfaceColor(this));
        vHeaderDivider.setBackgroundColor(ThemeManager.getDividerColor(this));
        tvTitle.setTextColor(textPrimary);

        setBackgroundKeepPadding(btnBack, ThemeManager.getBtnCompactBgRes(this));
        btnBack.setTextColor(ThemeManager.getHeaderButtonTextColor(this));

        int cardBgRes = ThemeManager.getCardBgRes(this);
        setBackgroundKeepPadding(layoutFormCard, cardBgRes);
        setBackgroundKeepPadding(layoutAdvancedCard, cardBgRes);

        tvLabelAccountName.setTextColor(textSecondary);
        tvLabelIssuer.setTextColor(textSecondary);
        tvLabelSecret.setTextColor(accentPrimary);
        tvSecretFormatNote.setTextColor(textMuted);

        int editBgRes = ThemeManager.getEditTextBgRes(this);
        setBackgroundKeepPadding(etAccountName, editBgRes);
        etAccountName.setTextColor(textPrimary);
        etAccountName.setHintTextColor(textMuted);

        setBackgroundKeepPadding(etIssuer, editBgRes);
        etIssuer.setTextColor(textPrimary);
        etIssuer.setHintTextColor(textMuted);

        setBackgroundKeepPadding(etSecret, editBgRes);
        etSecret.setTextColor(accentPrimary);
        etSecret.setHintTextColor(textMuted);

        setBackgroundKeepPadding(btnToggleAdvanced, ThemeManager.getBtnCompactBgRes(this));
        btnToggleAdvanced.setTextColor(ThemeManager.getHeaderButtonTextColor(this));

        tvLabelDigits.setTextColor(textSecondary);
        tvLabelPeriod.setTextColor(textSecondary);
        tvLabelAlgorithm.setTextColor(textSecondary);

        rbDigits6.setTextColor(textPrimary);
        rbDigits8.setTextColor(textPrimary);
        rbPeriod30.setTextColor(textPrimary);
        rbPeriod60.setTextColor(textPrimary);
        rbAlgoSha1.setTextColor(textPrimary);
        rbAlgoSha256.setTextColor(textPrimary);
        rbAlgoSha512.setTextColor(textPrimary);

        setBackgroundKeepPadding(btnSave, ThemeManager.getBtnPrimaryBgRes(this));
        btnSave.setTextColor(ThemeManager.getPrimaryButtonTextColor(this));
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

    private void saveAccount() {
        String name = etAccountName.getText().toString().trim();
        String issuer = etIssuer.getText().toString().trim();
        String secretRaw = etSecret.getText().toString();

        if (name == null || name.length() == 0) {
            Toast.makeText(this, R.string.error_empty_account_name, Toast.LENGTH_SHORT).show();
            etAccountName.requestFocus();
            return;
        }

        if (secretRaw == null || secretRaw.trim().length() == 0) {
            Toast.makeText(this, R.string.error_empty_secret, Toast.LENGTH_SHORT).show();
            etSecret.requestFocus();
            return;
        }

        // Base32 検証
        byte[] decodedKey;
        try {
            decodedKey = Base32.decode(secretRaw);
            if (decodedKey == null || decodedKey.length == 0) {
                throw new IllegalArgumentException("Empty key");
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_invalid_secret, Toast.LENGTH_LONG).show();
            etSecret.requestFocus();
            return;
        }

        // Base32 標準形式にサニタイズして保存
        String cleanSecret = Base32.encode(decodedKey, false);

        int digits = rbDigits8.isChecked() ? 8 : 6;
        int period = rbPeriod60.isChecked() ? 60 : 30;
        String algo = "HmacSHA1";
        if (rbAlgoSha256.isChecked()) {
            algo = "HmacSHA256";
        } else if (rbAlgoSha512.isChecked()) {
            algo = "HmacSHA512";
        }

        if (issuer == null || issuer.length() == 0) {
            issuer = name;
        }

        Account account = new Account(name, issuer, cleanSecret);
        account.setDigits(digits);
        account.setPeriodSeconds(period);
        account.setAlgorithm(algo);

        TokenStorageHelper.addAccount(this, account);
        Toast.makeText(this, getString(R.string.toast_account_added_format, name), Toast.LENGTH_SHORT).show();
        finish();
    }
}
