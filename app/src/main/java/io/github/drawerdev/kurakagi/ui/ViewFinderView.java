package io.github.drawerdev.kurakagi.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

/**
 * QRコードスキャン用の照準HUDオーバーレイView。
 * Android 2.1 (API 7) 互換の Pure Canvas 描画。
 */
public class ViewFinderView extends View {

    private final Paint maskPaint = new Paint();
    private final Paint laserPaint = new Paint();
    private final Paint framePaint = new Paint();
    private final Paint cornerPaint = new Paint();

    private final Rect frameRect = new Rect();
    private int laserY = 0;
    private boolean laserMovingDown = true;

    public ViewFinderView(Context context) {
        super(context);
        init();
    }

    public ViewFinderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(2f);

        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(6f);
        cornerPaint.setStrokeCap(Paint.Cap.ROUND);

        laserPaint.setStyle(Paint.Style.STROKE);
        laserPaint.setStrokeWidth(4f);

        applyColors(
                ThemeManager.getScanMaskColor(),
                ThemeManager.getScanFrameColor(),
                ThemeManager.getScanCornerColor(),
                ThemeManager.getScanLaserColor()
        );
    }

    /**
     * Updates HUD overlay colors from the active theme and redraws.
     */
    public void setThemeColors(int mask, int frame, int corner, int laser) {
        applyColors(mask, frame, corner, laser);
        invalidate();
    }

    private void applyColors(int mask, int frame, int corner, int laser) {
        maskPaint.setColor(mask);
        framePaint.setColor(frame);
        cornerPaint.setColor(corner);
        laserPaint.setColor(laser);
    }

    public Rect getFramingRect() {
        return frameRect;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int width = right - left;
        int height = bottom - top;

        // 画面の短い方の 65% を正方形照準枠とする
        int boxSize = (int) (Math.min(width, height) * 0.65f);
        if (boxSize < 180) boxSize = 180;

        int centerX = width / 2;
        // 上下中央より少し上（上下コントロールバーとの被りを完全に防ぐ）に配置
        int centerY = height / 2 - (int) (height * 0.05f);

        frameRect.set(
            centerX - boxSize / 2,
            centerY - boxSize / 2,
            centerX + boxSize / 2,
            centerY + boxSize / 2
        );

        if (laserY == 0 || laserY < frameRect.top || laserY > frameRect.bottom) {
            laserY = frameRect.top;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();

        // 1. マスク領域の描画 (枠の外側4方向)
        canvas.drawRect(0, 0, width, frameRect.top, maskPaint);
        canvas.drawRect(0, frameRect.top, frameRect.left, frameRect.bottom + 1, maskPaint);
        canvas.drawRect(frameRect.right + 1, frameRect.top, width, frameRect.bottom + 1, maskPaint);
        canvas.drawRect(0, frameRect.bottom + 1, width, height, maskPaint);

        // 2. 枠線の描画
        canvas.drawRect(frameRect, framePaint);

        // 3. 四隅のコーナーブラケット (L字) 描画
        int cornerLen = (frameRect.right - frameRect.left) / 8;
        if (cornerLen < 20) cornerLen = 20;

        // 左上
        canvas.drawLine(frameRect.left, frameRect.top, frameRect.left + cornerLen, frameRect.top, cornerPaint);
        canvas.drawLine(frameRect.left, frameRect.top, frameRect.left, frameRect.top + cornerLen, cornerPaint);
        // 右上
        canvas.drawLine(frameRect.right, frameRect.top, frameRect.right - cornerLen, frameRect.top, cornerPaint);
        canvas.drawLine(frameRect.right, frameRect.top, frameRect.right, frameRect.top + cornerLen, cornerPaint);
        // 左下
        canvas.drawLine(frameRect.left, frameRect.bottom, frameRect.left + cornerLen, frameRect.bottom, cornerPaint);
        canvas.drawLine(frameRect.left, frameRect.bottom, frameRect.left, frameRect.bottom - cornerLen, cornerPaint);
        // 右下
        canvas.drawLine(frameRect.right, frameRect.bottom, frameRect.right - cornerLen, frameRect.bottom, cornerPaint);
        canvas.drawLine(frameRect.right, frameRect.bottom, frameRect.right, frameRect.bottom - cornerLen, cornerPaint);

        // 4. アニメーションするスキャンレーザー
        canvas.drawLine(frameRect.left + 8, laserY, frameRect.right - 8, laserY, laserPaint);

        // レーザーの移動更新
        int step = 6;
        if (laserMovingDown) {
            laserY += step;
            if (laserY >= frameRect.bottom - 4) {
                laserMovingDown = false;
            }
        } else {
            laserY -= step;
            if (laserY <= frameRect.top + 4) {
                laserMovingDown = true;
            }
        }

        // 次回再描画（API 7 互換のため postInvalidateDelayed または invalidate）
        postInvalidateDelayed(30);
    }
}
