package io.github.drawerdev.kurakagi.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import io.github.drawerdev.kurakagi.R;
import io.github.drawerdev.kurakagi.core.OtpUriParser;
import io.github.drawerdev.kurakagi.qr.QrEncoder;
import io.github.drawerdev.kurakagi.qr.QrMatrix;
import io.github.drawerdev.kurakagi.storage.Account;

/**
 * トークン詳細・再バックアップ画面。
 * シークレット平文の確認・コピー、および他の端末へ鍵を多重化するための QR 再表示。
 */
public class TokenDetailActivity extends Activity {

    public static final String EXTRA_ACCOUNT_ID = "extra_account_id";

    /** QR の周りの余白（マス）。規格の最小値。 */
    private static final int QR_QUIET_ZONE = 4;
    /** QR の枠の 1 辺（activity_token_detail.xml の layout_qr_box と同じ）。 */
    private static final int QR_BOX_DP = 220;

    private View layoutDetailRoot;
    private View layoutDetailHeader;
    private TextView tvDetailTitle;
    private View cardDetailSummary;
    private View cardDetailQr;
    private TextView tvDetailQrTitle;
    private TextView tvDetailQrHint;
    private View layoutQrBox;
    private View cardDetailSecret;
    private TextView tvDetailSecretLabel;
    private TextView tvIssuer;
    private TextView tvLabel;
    private TextView tvSpecs;
    private TextView tvSecret;
    private ImageView ivQr;
    private Button btnCopySecret;
    private Button btnDelete;
    private Button btnBack;

    private Account currentAccount;

    /** "JBSWY3DPEHPK3PXP" -> "JBSW Y3DP EHPK 3PXP", with a line break after every 4 groups. */
    static String groupForPaper(String secret) {
        if (secret == null) return "";
        StringBuffer sb = new StringBuffer();
        int n = 0;
        for (int i = 0; i < secret.length(); i++) {
            char c = secret.charAt(i);
            if (c == ' ' || c == '-') continue;
            if (n > 0 && n % 4 == 0) {
                sb.append(n % 16 == 0 ? '\n' : ' ');
            }
            sb.append(Character.toUpperCase(c));
            n++;
        }
        return sb.toString();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.applyLocale(this);
        ThemeManager.applyBaseTheme(this, false);
        super.onCreate(savedInstanceState);
        // This screen shows the secret and its QR code: block screenshots, screen recording
        // and the recent-apps thumbnail. (The code list screen stays recordable.)
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_token_detail);

        String accountId = getIntent().getStringExtra(EXTRA_ACCOUNT_ID);
        currentAccount = TokenStorageHelper.findAccountById(this, accountId);

        if (currentAccount == null) {
            Toast.makeText(this, getString(R.string.toast_account_not_found), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        layoutDetailRoot = findViewById(R.id.layout_detail_root);
        layoutDetailHeader = findViewById(R.id.layout_detail_header);
        tvDetailTitle = (TextView) findViewById(R.id.tv_detail_title);
        cardDetailSummary = findViewById(R.id.card_detail_summary);
        cardDetailQr = findViewById(R.id.card_detail_qr);
        tvDetailQrTitle = (TextView) findViewById(R.id.tv_detail_qr_title);
        tvDetailQrHint = (TextView) findViewById(R.id.tv_detail_qr_hint);
        layoutQrBox = findViewById(R.id.layout_qr_box);
        cardDetailSecret = findViewById(R.id.card_detail_secret);
        tvDetailSecretLabel = (TextView) findViewById(R.id.tv_detail_secret_label);
        tvIssuer = (TextView) findViewById(R.id.tv_detail_issuer);
        tvLabel = (TextView) findViewById(R.id.tv_detail_label);
        tvSpecs = (TextView) findViewById(R.id.tv_detail_specs);
        tvSecret = (TextView) findViewById(R.id.tv_detail_secret);
        ivQr = (ImageView) findViewById(R.id.iv_detail_qr);
        btnCopySecret = (Button) findViewById(R.id.btn_copy_secret);
        btnDelete = (Button) findViewById(R.id.btn_delete_account);
        btnBack = (Button) findViewById(R.id.btn_detail_back);

        applyTheme();

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // アカウント情報の表示
        String issuer = currentAccount.getIssuer();
        if (issuer == null || issuer.length() == 0) {
            issuer = getString(R.string.default_issuer_label);
        }
        tvIssuer.setText(issuer);
        tvLabel.setText(currentAccount.getLabel());
        tvSpecs.setText(getString(R.string.detail_specs_format,
                currentAccount.getDigits(),
                currentAccount.getPeriodSeconds(),
                currentAccount.getAlgorithm()));

        // シークレットを、紙に書き写しやすいよう 4 文字ずつ区切って表示する。
        // 入力し直すときは空白・改行を無視するので、区切ったまま打ち込める。コピーは区切りなし。
        tvSecret.setText(groupForPaper(currentAccount.getSecret()));
        TextView tvSecretNote = (TextView) findViewById(R.id.tv_detail_secret_note);
        tvSecretNote.setTextColor(ThemeManager.getTextSecondaryColor(this));

        // コピーボタン
        btnCopySecret.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ClipboardCompat.copy(TokenDetailActivity.this, "secret", currentAccount.getSecret(), true)) {
                    Toast.makeText(TokenDetailActivity.this, getString(R.string.toast_secret_copied), Toast.LENGTH_SHORT).show();
                }
            }
        });

        // QR コードの再生成と描画
        generateQrBitmap();

        // アカウント削除
        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDeleteConfirmDialog();
            }
        });
    }

    private void applyTheme() {
        layoutDetailRoot.setBackgroundColor(ThemeManager.getBgColor(this));
        layoutDetailHeader.setBackgroundColor(ThemeManager.getSurfaceColor(this));
        setBackgroundPreservingPadding(btnBack, ThemeManager.getBtnSecondaryBgRes(this));
        btnBack.setTextColor(ThemeManager.getHeaderButtonTextColor(this));
        tvDetailTitle.setTextColor(ThemeManager.getTextPrimaryColor(this));

        setBackgroundPreservingPadding(cardDetailSummary, ThemeManager.getCardBgRes(this));
        tvIssuer.setTextColor(ThemeManager.getAccentPrimaryColor(this));
        tvLabel.setTextColor(ThemeManager.getTextPrimaryColor(this));
        tvSpecs.setTextColor(ThemeManager.getTextMutedColor(this));

        setBackgroundPreservingPadding(cardDetailQr, ThemeManager.getCardBgRes(this));
        tvDetailQrTitle.setTextColor(ThemeManager.getTextPrimaryColor(this));
        tvDetailQrHint.setTextColor(ThemeManager.getTextMutedColor(this));
        layoutQrBox.setBackgroundColor(ThemeManager.getQrBackgroundColor());

        setBackgroundPreservingPadding(cardDetailSecret, ThemeManager.getCardBgRes(this));
        tvDetailSecretLabel.setTextColor(ThemeManager.getTextPrimaryColor(this));
        setBackgroundPreservingPadding(tvSecret, ThemeManager.getEditTextBgRes(this));
        tvSecret.setTextColor(ThemeManager.getAccentPrimaryColor(this));
        setBackgroundPreservingPadding(btnCopySecret, ThemeManager.getBtnSecondaryBgRes(this));
        btnCopySecret.setTextColor(ThemeManager.getSecondaryButtonTextColor(this));

        setBackgroundPreservingPadding(btnDelete, ThemeManager.getBtnDangerBgRes(this));
        btnDelete.setTextColor(ThemeManager.getDangerButtonTextColor(this));
    }

    private static void setBackgroundPreservingPadding(View view, int resId) {
        int left = view.getPaddingLeft();
        int top = view.getPaddingTop();
        int right = view.getPaddingRight();
        int bottom = view.getPaddingBottom();
        view.setBackgroundResource(resId);
        view.setPadding(left, top, right, bottom);
    }

    private void generateQrBitmap() {
        try {
            String uri = OtpUriParser.toUri(currentAccount);
            QrMatrix matrix = QrEncoder.encode(uri, QrEncoder.ErrorCorrectionLevel.L);
            if (matrix != null) {
                // 余白（quiet zone）は規格どおり 4 マス。絵の中に入れるので、表示の大きさが変わっても保たれる
                int modules = matrix.getWidth() + 2 * QR_QUIET_ZONE;
                int boxPx = Math.round(QR_BOX_DP * getResources().getDisplayMetrics().density);
                int scale = Math.max(1, boxPx / modules);
                int side = modules * scale;
                int[] pixels = matrix.toPixelArray(scale, QR_QUIET_ZONE, Color.BLACK, Color.WHITE);
                Bitmap bitmap = Bitmap.createBitmap(pixels, side, side, Bitmap.Config.RGB_565);
                ivQr.setImageBitmap(bitmap);
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.toast_qr_error_format, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeleteConfirmDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.confirm_delete_title);
        builder.setMessage(R.string.confirm_delete_msg);
        builder.setPositiveButton(R.string.btn_confirm_delete, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                TokenStorageHelper.deleteAccount(TokenDetailActivity.this, currentAccount.getId());
                Toast.makeText(TokenDetailActivity.this, getString(R.string.toast_account_deleted), Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        builder.setNegativeButton(R.string.btn_cancel, null);
        builder.show();
    }
}
