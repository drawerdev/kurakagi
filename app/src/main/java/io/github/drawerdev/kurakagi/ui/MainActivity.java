package io.github.drawerdev.kurakagi.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import io.github.drawerdev.kurakagi.R;
import io.github.drawerdev.kurakagi.core.TimeSyncManager;
import io.github.drawerdev.kurakagi.storage.Account;

import java.util.List;

/**
 * メイン画面。
 * トークン一覧表示、30秒カウントダウンゲージ、クイックNTP同期、各種画面への遷移を統括。
 */
public class MainActivity extends Activity {

    private View layoutMainRoot;
    private View layoutMainHeader;
    private View layoutCountdownBar;
    private View layoutMainBottom;
    private ListView lvTokens;
    private View layoutEmpty;
    private TextView tvMainTitle;
    private TextView tvHeaderOffset;
    private TextView tvCountdownLabel;
    private TextView tvCountdownSeconds;
    private TextView tvEmptyTitle;
    private TextView tvEmptyDesc;
    private ProgressBar pbTimeCountdown;
    private Button btnQuickNtp;
    private Button btnSettings;
    private Button btnScan;
    private Button btnManual;

    private TokenAdapter adapter;
    private TimeSyncManager timeSyncManager;
    private final Handler timerHandler = new Handler();
    private boolean isTimerRunning = false;
    private boolean appliedLightTheme = false;
    private String appliedLanguage = LocaleHelper.LANG_AUTO;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateTick();
            if (isTimerRunning) {
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.applyLocale(this);
        ThemeManager.applyBaseTheme(this, false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        appliedLightTheme = ThemeManager.isLightTheme(this);
        appliedLanguage = LocaleHelper.getLanguage(this);
        timeSyncManager = TimeSyncManager.getInstance(this);

        layoutMainRoot = findViewById(R.id.layout_main_root);
        layoutMainHeader = findViewById(R.id.layout_main_header);
        layoutCountdownBar = findViewById(R.id.layout_countdown_bar);
        layoutMainBottom = findViewById(R.id.layout_main_bottom);
        lvTokens = (ListView) findViewById(R.id.lv_tokens);
        layoutEmpty = findViewById(R.id.layout_empty);
        tvMainTitle = (TextView) findViewById(R.id.tv_main_title);
        tvHeaderOffset = (TextView) findViewById(R.id.tv_header_offset);
        tvCountdownLabel = (TextView) findViewById(R.id.tv_countdown_label);
        tvCountdownSeconds = (TextView) findViewById(R.id.tv_countdown_seconds);
        tvEmptyTitle = (TextView) findViewById(R.id.tv_empty_title);
        tvEmptyDesc = (TextView) findViewById(R.id.tv_empty_desc);
        pbTimeCountdown = (ProgressBar) findViewById(R.id.pb_time_countdown);
        btnQuickNtp = (Button) findViewById(R.id.btn_quick_ntp);
        btnSettings = (Button) findViewById(R.id.btn_settings);
        btnScan = (Button) findViewById(R.id.btn_main_scan);
        btnManual = (Button) findViewById(R.id.btn_main_manual);

        applyTheme();

        adapter = new TokenAdapter(this);
        lvTokens.setAdapter(adapter);

        // クイックNTP同期
        btnQuickNtp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, getString(R.string.toast_ntp_connecting), Toast.LENGTH_SHORT).show();
                timeSyncManager.syncWithNtp(null, new TimeSyncManager.SyncCallback() {
                    @Override
                    public void onSuccess(long offsetMs) {
                        int sec = (int) (offsetMs / 1000L);
                        String sign = sec >= 0 ? "+" : "";
                        Toast.makeText(MainActivity.this, getString(R.string.toast_ntp_success_format, sign, sec), Toast.LENGTH_LONG).show();
                        updateHeaderOffset();
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Toast.makeText(MainActivity.this, getString(R.string.toast_ntp_failed_format, errorMessage), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });

        // 設定画面
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

        // QRスキャン画面（Android 6.0+ ランタイム権限チェック対応）
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (PermissionHelper.hasPermission(MainActivity.this, PermissionHelper.CAMERA)) {
                    startActivity(new Intent(MainActivity.this, ScanActivity.class));
                } else {
                    PermissionHelper.requestPermissions(MainActivity.this, new String[] { PermissionHelper.CAMERA }, PermissionHelper.REQUEST_CODE_CAMERA);
                }
            }
        });

        // 手動追加画面
        btnManual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ManualAddActivity.class));
            }
        });
    }

    private void applyTheme() {
        layoutMainRoot.setBackgroundColor(ThemeManager.getBgColor(this));
        layoutMainHeader.setBackgroundColor(ThemeManager.getSurfaceColor(this));
        tvMainTitle.setTextColor(ThemeManager.getTextPrimaryColor(this));

        setBackgroundPreservingPadding(btnQuickNtp, ThemeManager.getBtnSecondaryBgRes(this));
        btnQuickNtp.setTextColor(ThemeManager.getSecondaryButtonTextColor(this));
        setBackgroundPreservingPadding(btnSettings, ThemeManager.getBtnSecondaryBgRes(this));
        btnSettings.setTextColor(ThemeManager.getSecondaryButtonTextColor(this));

        layoutCountdownBar.setBackgroundColor(ThemeManager.getSurfaceAltColor(this));
        tvCountdownLabel.setTextColor(ThemeManager.getTextMutedColor(this));
        tvCountdownSeconds.setTextColor(ThemeManager.getAccentPrimaryColor(this));
        ThemeManager.applyProgressDrawable(pbTimeCountdown, this);

        tvEmptyTitle.setTextColor(ThemeManager.getTextPrimaryColor(this));
        tvEmptyDesc.setTextColor(ThemeManager.getTextSecondaryColor(this));

        layoutMainBottom.setBackgroundColor(ThemeManager.getSurfaceColor(this));
        setBackgroundPreservingPadding(btnScan, ThemeManager.getBtnPrimaryBgRes(this));
        btnScan.setTextColor(ThemeManager.getPrimaryButtonTextColor(this));
        setBackgroundPreservingPadding(btnManual, ThemeManager.getBtnSecondaryBgRes(this));
        btnManual.setTextColor(ThemeManager.getSecondaryButtonTextColor(this));
    }

    private static void setBackgroundPreservingPadding(View view, int resId) {
        int left = view.getPaddingLeft();
        int top = view.getPaddingTop();
        int right = view.getPaddingRight();
        int bottom = view.getPaddingBottom();
        view.setBackgroundResource(resId);
        view.setPadding(left, top, right, bottom);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ThemeManager.isLightTheme(this) != appliedLightTheme
                || !LocaleHelper.getLanguage(this).equals(appliedLanguage)) {
            finish();
            startActivity(getIntent());
            overridePendingTransition(0, 0);
            return;
        }
        loadData();
        updateHeaderOffset();
        startTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimer();
    }

    private void loadData() {
        List<Account> accounts = TokenStorageHelper.loadAccounts(this);
        adapter.setAccounts(accounts);
        if (accounts == null || accounts.size() == 0) {
            lvTokens.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
        } else {
            lvTokens.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
        }
    }

    private void updateHeaderOffset() {
        int offsetSec = timeSyncManager.getTimeOffsetSeconds();
        if (offsetSec == 0) {
            tvHeaderOffset.setText(getString(R.string.header_offset_ok));
            tvHeaderOffset.setTextColor(ThemeManager.getAccentHeaderColor(this));
        } else {
            String sign = offsetSec > 0 ? "+" : "";
            tvHeaderOffset.setText(getString(R.string.header_offset_format, sign, offsetSec));
            tvHeaderOffset.setTextColor(ThemeManager.getTimerWarningColor(this));
        }
    }

    private void startTimer() {
        if (!isTimerRunning) {
            isTimerRunning = true;
            timerHandler.post(timerRunnable);
        }
    }

    private void stopTimer() {
        isTimerRunning = false;
        timerHandler.removeCallbacks(timerRunnable);
    }

    private void updateTick() {
        long currentMs = timeSyncManager.getCurrentTimeMillis();
        long currentSec = currentMs / 1000L;
        int remainingSec = (int) (30 - (currentSec % 30));

        // 30秒カウントダウン表示
        tvCountdownSeconds.setText(getString(R.string.countdown_seconds_format, remainingSec));
        if (remainingSec <= 5) {
            tvCountdownSeconds.setTextColor(ThemeManager.getTimerDangerColor(this));
        } else {
            tvCountdownSeconds.setTextColor(ThemeManager.getAccentPrimaryColor(this));
        }

        // プログレスバー（ミリ秒単位でスムースに計算）
        long elapsedInStepMs = currentMs % 30000L;
        int progress = (int) (30000L - elapsedInStepMs);
        pbTimeCountdown.setProgress(progress);

        // トークンリストのアダプターを再描画
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PermissionHelper.REQUEST_CODE_CAMERA) {
            if (grantResults != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startActivity(new Intent(MainActivity.this, ScanActivity.class));
            } else {
                Toast.makeText(this, getString(R.string.toast_camera_permission_required), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
