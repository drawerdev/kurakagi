package io.github.drawerdev.kurakagi.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import io.github.drawerdev.kurakagi.R;
import io.github.drawerdev.kurakagi.core.OtpUriParser;
import io.github.drawerdev.kurakagi.qr.QrYuvDecoder;
import io.github.drawerdev.kurakagi.storage.Account;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * QRコードスキャン画面。
 * Android 2.1 (API 7) 互換の縦画面 (Portrait) カメラプレビュー。
 * SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS 指定、間欠 one-shot プレビューコールバック駆動。
 */
public class ScanActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private SurfaceView surfaceView;
    private ViewFinderView viewFinderView;
    private View layoutScanHeader;
    private View layoutScanFooter;
    private TextView tvScanHeaderTitle;
    private TextView tvScanHint;
    private Button btnSwitchManual;
    private Button btnCancel;

    private Camera camera;
    private SurfaceHolder surfaceHolder;
    private boolean isScanning = false;
    private boolean isProcessingFrame = false;
    private boolean isAutoFocusing = false;
    private Handler mainHandler;

    // 不正QRコードの連続読み込み・トースト累積防止
    private Toast currentToast = null;
    private String lastInvalidQrText = null;
    private long lastInvalidQrTime = 0L;
    private static final long INVALID_QR_COOLDOWN_MS = 2500L;

    private final Runnable autoFocusRunnable = new Runnable() {
        @Override
        public void run() {
            triggerAutoFocus();
            if (isScanning) {
                mainHandler.postDelayed(this, 2000);
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
        ThemeManager.applyBaseTheme(this, true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        mainHandler = new Handler(Looper.getMainLooper());
        surfaceView = (SurfaceView) findViewById(R.id.sv_camera_preview);
        viewFinderView = (ViewFinderView) findViewById(R.id.v_finder);
        layoutScanHeader = findViewById(R.id.layout_scan_header);
        layoutScanFooter = findViewById(R.id.layout_scan_footer);
        tvScanHeaderTitle = (TextView) findViewById(R.id.tv_scan_header_title);
        tvScanHint = (TextView) findViewById(R.id.tv_scan_hint);
        btnSwitchManual = (Button) findViewById(R.id.btn_scan_switch_manual);
        btnCancel = (Button) findViewById(R.id.btn_scan_cancel);

        applyTheme();

        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        // API 7 (Android 2.1) 必須: PUSH_BUFFERS の明示
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        btnSwitchManual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ScanActivity.this, ManualAddActivity.class));
                finish();
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // 画面タップでオートフォーカスを駆動
        View.OnClickListener focusClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                triggerAutoFocus();
            }
        };
        surfaceView.setOnClickListener(focusClickListener);
        viewFinderView.setOnClickListener(focusClickListener);
    }

    @SuppressWarnings("deprecation")
    private void applyTheme() {
        int overlayColor = ThemeManager.getScanOverlayColor();
        layoutScanHeader.setBackgroundColor(overlayColor);
        layoutScanFooter.setBackgroundColor(overlayColor);

        tvScanHeaderTitle.setTextColor(ThemeManager.getScanCornerColor());
        tvScanHint.setTextColor(getResources().getColor(R.color.dark_text_primary));

        viewFinderView.setThemeColors(
                ThemeManager.getScanMaskColor(),
                ThemeManager.getScanFrameColor(),
                ThemeManager.getScanCornerColor(),
                ThemeManager.getScanLaserColor()
        );

        setBackgroundKeepPadding(btnSwitchManual, ThemeManager.getBtnPrimaryBgRes(this));
        btnSwitchManual.setTextColor(ThemeManager.getPrimaryButtonTextColor(this));

        setBackgroundKeepPadding(btnCancel, ThemeManager.getBtnSecondaryBgRes(this));
        btnCancel.setTextColor(ThemeManager.getSecondaryButtonTextColor(this));
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

    @Override
    protected void onResume() {
        super.onResume();
        if (PermissionHelper.hasPermission(this, PermissionHelper.CAMERA)) {
            if (surfaceHolder != null) {
                startCamera();
            }
        } else {
            PermissionHelper.requestPermissions(this, new String[] { PermissionHelper.CAMERA }, PermissionHelper.REQUEST_CODE_CAMERA);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        dismissToast();
        stopCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissToast();
    }

    private void dismissToast() {
        if (currentToast != null) {
            try {
                currentToast.cancel();
            } catch (Exception ignored) {
            }
            currentToast = null;
        }
    }

    private void showSingleToast(String message) {
        dismissToast();
        try {
            currentToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
            currentToast.show();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PermissionHelper.REQUEST_CODE_CAMERA) {
            if (grantResults != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, R.string.toast_camera_permission_required, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void setCameraDisplayOrientation() {
        if (camera == null) return;
        try {
            int result = 90; // Android スマートフォンの縦画面・背面カメラ標準値
            if (Build.VERSION.SDK_INT >= 9) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(0, info);
                int rotation = getWindowManager().getDefaultDisplay().getRotation();
                int degrees = 0;
                switch (rotation) {
                    case Surface.ROTATION_0: degrees = 0; break;
                    case Surface.ROTATION_90: degrees = 90; break;
                    case Surface.ROTATION_180: degrees = 180; break;
                    case Surface.ROTATION_270: degrees = 270; break;
                }
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    result = (info.orientation + degrees) % 360;
                    result = (360 - result) % 360;
                } else {
                    result = (info.orientation - degrees + 360) % 360;
                }
            }
            if (Build.VERSION.SDK_INT >= 8) {
                Method method = camera.getClass().getMethod("setDisplayOrientation", int.class);
                method.invoke(camera, result);
            }
        } catch (Exception ignore) {
        }
    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int targetWidth, int targetHeight) {
        if (sizes == null || sizes.size() == 0) return null;
        int targetW = Math.max(targetWidth, targetHeight);
        int targetH = Math.min(targetWidth, targetHeight);
        double targetRatio = (double) targetW / targetH;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        for (int i = 0; i < sizes.size(); i++) {
            Camera.Size size = sizes.get(i);
            // 720p 以下の適度な解像度を優先（処理負荷軽減）
            if (size.width > 1280 || size.height > 960) continue;
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > 0.25) continue;
            if (Math.abs(size.height - targetH) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetH);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (int i = 0; i < sizes.size(); i++) {
                Camera.Size size = sizes.get(i);
                if (size.width > 1280) continue;
                if (Math.abs(size.height - targetH) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetH);
                }
            }
        }
        return optimalSize;
    }

    private void startCamera() {
        if (!PermissionHelper.hasPermission(this, PermissionHelper.CAMERA)) {
            PermissionHelper.requestPermissions(this, new String[] { PermissionHelper.CAMERA }, PermissionHelper.REQUEST_CODE_CAMERA);
            return;
        }
        if (camera != null) return;
        try {
            camera = Camera.open();
            if (camera == null) {
                showCameraErrorDialog(getString(R.string.error_camera_open));
                return;
            }

            // 縦画面 (Portrait) の向きにカメラプレビューを正立
            setCameraDisplayOrientation();

            Camera.Parameters params = camera.getParameters();
            params.setPictureFormat(PixelFormat.JPEG);

            // 最適なプレビュー解像度を設定
            try {
                List<Camera.Size> supportedSizes = params.getSupportedPreviewSizes();
                Camera.Size optimalSize = getOptimalPreviewSize(supportedSizes, 720, 1280);
                if (optimalSize != null) {
                    params.setPreviewSize(optimalSize.width, optimalSize.height);
                }
            } catch (Exception ignore) {
            }

            // オートフォーカス対応
            try {
                List<String> focusModes = params.getSupportedFocusModes();
                if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                } else if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }
            } catch (Exception ignore) {
            }

            camera.setParameters(params);
            if (surfaceHolder != null && surfaceHolder.getSurface() != null) {
                camera.setPreviewDisplay(surfaceHolder);
                camera.startPreview();
                isScanning = true;
                requestNextFrame();
                mainHandler.postDelayed(autoFocusRunnable, 1000);
            }
        } catch (Exception e) {
            showCameraErrorDialog(getString(R.string.error_camera_start_format, e.getMessage()));
        }
    }

    private void triggerAutoFocus() {
        if (camera != null && isScanning && !isAutoFocusing) {
            try {
                isAutoFocusing = true;
                camera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera cam) {
                        isAutoFocusing = false;
                    }
                });
            } catch (Exception e) {
                isAutoFocusing = false;
            }
        }
    }

    private void stopCamera() {
        isScanning = false;
        mainHandler.removeCallbacks(autoFocusRunnable);
        if (camera != null) {
            try {
                camera.cancelAutoFocus();
            } catch (Exception ignored) {
            }
            try {
                camera.setOneShotPreviewCallback(null);
                camera.stopPreview();
                camera.release();
            } catch (Exception ignored) {
            }
            camera = null;
        }
    }

    private void requestNextFrame() {
        if (camera != null && isScanning && !isProcessingFrame) {
            camera.setOneShotPreviewCallback(this);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (PermissionHelper.hasPermission(this, PermissionHelper.CAMERA)) {
            startCamera();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (camera != null) {
            try {
                setCameraDisplayOrientation();
                camera.setPreviewDisplay(holder);
                camera.startPreview();
                isScanning = true;
                requestNextFrame();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopCamera();
    }

    @Override
    public void onPreviewFrame(final byte[] data, final Camera camera) {
        if (!isScanning || isProcessingFrame || data == null) {
            return;
        }
        isProcessingFrame = true;

        Camera.Size size = camera.getParameters().getPreviewSize();
        final int previewWidth = size.width;
        final int previewHeight = size.height;
        final Rect framingRect = viewFinderView.getFramingRect();

        // バックグラウンドスレッドで QR デコードを実行
        new Thread(new DecodeTask(this, data, previewWidth, previewHeight, framingRect)).start();
    }

    private static class DecodeTask implements Runnable {
        private final ScanActivity activity;
        private final byte[] data;
        private final int previewWidth;
        private final int previewHeight;
        private final Rect framingRect;

        public DecodeTask(ScanActivity activity, byte[] data, int previewWidth, int previewHeight, Rect framingRect) {
            this.activity = activity;
            this.data = data;
            this.previewWidth = previewWidth;
            this.previewHeight = previewHeight;
            this.framingRect = framingRect;
        }

        @Override
        public void run() {
            String decodedText = null;
            try {
                // 1. 横向きプレビューデータから Y プレーンを抽出
                byte[] yPlane = QrYuvDecoder.extractYPlane(data, previewWidth, previewHeight);

                // 2. 縦画面表示に合わせて時計回り90度回転
                byte[] rotatedY = QrYuvDecoder.rotateYPlane90(yPlane, previewWidth, previewHeight);
                int rotatedWidth = previewHeight;
                int rotatedHeight = previewWidth;

                // 3. 画面上の照準枠 (framingRect) をカメラ解像度にマッピング
                int cropLeft = 0;
                int cropTop = 0;
                int cropWidth = rotatedWidth;
                int cropHeight = rotatedHeight;

                if (framingRect != null && activity.surfaceView != null && activity.surfaceView.getWidth() > 0) {
                    int svWidth = activity.surfaceView.getWidth();
                    int svHeight = activity.surfaceView.getHeight();

                    int rawLeft = framingRect.left * rotatedWidth / svWidth;
                    int rawTop = framingRect.top * rotatedHeight / svHeight;
                    int rawWidth = (framingRect.right - framingRect.left) * rotatedWidth / svWidth;
                    int rawHeight = (framingRect.bottom - framingRect.top) * rotatedHeight / svHeight;

                    // 15% の余白を持たせてクワイエットゾーン欠けを防止
                    int marginX = (int) (rawWidth * 0.15f);
                    int marginY = (int) (rawHeight * 0.15f);
                    cropLeft = Math.max(0, rawLeft - marginX);
                    cropTop = Math.max(0, rawTop - marginY);
                    cropWidth = Math.min(rotatedWidth - cropLeft, rawWidth + marginX * 2);
                    cropHeight = Math.min(rotatedHeight - cropTop, rawHeight + marginY * 2);
                }

                // 1. 縦画面・照準枠エリアでデコード試行
                decodedText = QrYuvDecoder.decodeYPlane(rotatedY, rotatedWidth, rotatedHeight,
                        cropLeft, cropTop, cropWidth, cropHeight);

                // 2. 縦画面・画面全体でフォールバック検出
                if (decodedText == null || decodedText.length() == 0) {
                    decodedText = QrYuvDecoder.decodeYPlane(rotatedY, rotatedWidth, rotatedHeight,
                            0, 0, rotatedWidth, rotatedHeight);
                }

                // 3. 原画像（横向きバッファ）全体でフォールバック検出
                if (decodedText == null || decodedText.length() == 0) {
                    decodedText = QrYuvDecoder.decodeYPlane(yPlane, previewWidth, previewHeight,
                            0, 0, previewWidth, previewHeight);
                }
            } catch (Exception ignored) {
            }

            activity.mainHandler.post(new DecodeResultPoster(activity, decodedText));
        }
    }

    private static class DecodeResultPoster implements Runnable {
        private final ScanActivity activity;
        private final String result;

        public DecodeResultPoster(ScanActivity activity, String result) {
            this.activity = activity;
            this.result = result;
        }

        @Override
        public void run() {
            if (!activity.isScanning) return;
            if (result != null && result.length() > 0) {
                activity.handleQrResult(result);
            } else {
                activity.isProcessingFrame = false;
                activity.requestNextFrame();
            }
        }
    }

    private void handleQrResult(String qrText) {
        // otpauth://totp/... を安全にパース（不正QRや別形式QRによるクラッシュを防止）
        Account parsedAccount = null;
        try {
            parsedAccount = OtpUriParser.parse(qrText);
        } catch (Exception e) {
            parsedAccount = null;
        }

        final Account account = parsedAccount;
        if (account == null) {
            long now = System.currentTimeMillis();
            boolean isSameInvalid = (qrText != null && qrText.equals(lastInvalidQrText));
            boolean isInCooldown = (now - lastInvalidQrTime < INVALID_QR_COOLDOWN_MS);

            if (!isSameInvalid || !isInCooldown) {
                lastInvalidQrText = qrText;
                lastInvalidQrTime = now;

                String preview = (qrText == null) ? "" : qrText;
                if (preview.length() > 40) {
                    preview = preview.substring(0, 37) + "...";
                }
                showSingleToast(getString(R.string.toast_unsupported_qr_format, preview));
            }

            // スキャン継続（キューイングなし）
            isProcessingFrame = false;
            requestNextFrame();
            return;
        }

        // 正しい TOTP QR コードが検出された場合のみスキャンを停止
        isScanning = false;
        dismissToast();
        lastInvalidQrText = null;

        // 成功バイブレーション
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(80);
            }
        } catch (Exception ignored) {
        }

        // 登録確認ダイアログ
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.scan_success);
        String msg = getString(R.string.scan_confirm_account_format, account.getLabel());
        if (account.getIssuer() != null && account.getIssuer().length() > 0) {
            msg += getString(R.string.scan_confirm_issuer_format, account.getIssuer());
        }
        msg += getString(R.string.scan_confirm_question);
        builder.setMessage(msg);

        builder.setPositiveButton(R.string.btn_register_account, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                TokenStorageHelper.addAccount(ScanActivity.this, account);
                Toast.makeText(ScanActivity.this,
                        getString(R.string.toast_account_registered_format, account.getLabel()),
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                isScanning = true;
                isProcessingFrame = false;
                requestNextFrame();
            }
        });

        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                isScanning = true;
                isProcessingFrame = false;
                requestNextFrame();
            }
        });

        builder.show();
    }

    private void showCameraErrorDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_camera_error_title);
        builder.setMessage(getString(R.string.dialog_camera_error_switch_format, message));
        builder.setPositiveButton(R.string.btn_go_manual, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startActivity(new Intent(ScanActivity.this, ManualAddActivity.class));
                finish();
            }
        });
        builder.setNegativeButton(R.string.btn_dialog_back, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        builder.show();
    }
}
