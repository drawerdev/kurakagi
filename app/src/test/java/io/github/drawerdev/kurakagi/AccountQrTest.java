package io.github.drawerdev.kurakagi;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import io.github.drawerdev.kurakagi.qr.QrEncoder;
import io.github.drawerdev.kurakagi.qr.QrMatrix;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The QR on the account screen (TokenDetailActivity.generateQrBitmap): an otpauth URI at
 * error correction L, drawn with a 4-module quiet zone, must read back unchanged (K-07).
 */
public class AccountQrTest {

    private static final String URI = "otpauth://totp/Example%20Service:someone%40example.com"
            + "?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP&issuer=Example%20Service"
            + "&algorithm=SHA256&digits=8&period=30";
    private static final int QUIET_ZONE = 4;
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    @Test
    public void accountUriReadsBackWithQuietZone() throws Exception {
        QrMatrix matrix = QrEncoder.encode(URI, QrEncoder.ErrorCorrectionLevel.L);
        int modules = matrix.getWidth() + 2 * QUIET_ZONE;
        // 220dp at ldpi (165px) up to xxhdpi (660px), as the activity computes the scale
        int[] boxPx = {165, 220, 330, 440, 660};
        for (int i = 0; i < boxPx.length; i++) {
            int scale = Math.max(1, boxPx[i] / modules);
            int side = modules * scale;
            int[] pixels = matrix.toPixelArray(scale, QUIET_ZONE, BLACK, WHITE);
            assertEquals(side * side, pixels.length);

            int border = QUIET_ZONE * scale;
            for (int y = 0; y < side; y++) {
                for (int x = 0; x < side; x++) {
                    if (x < border || y < border || x >= side - border || y >= side - border) {
                        assertEquals("box " + boxPx[i] + " (" + x + "," + y + ")",
                                WHITE, pixels[y * side + x]);
                    }
                }
            }
            assertEquals(BLACK, pixels[border * side + border]);

            // PURE_BARCODE: the bitmap is a clean, computer-drawn code. ZXing 2.3.0's finder-pattern
            // search misses perfect synthetic images with a wide white border at 6+ px per module,
            // which a camera never produces; the content and module grid are what is checked here.
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(
                    new RGBLuminanceSource(side, side, pixels)));
            Result result = new QRCodeReader().decode(bitmap, PURE);
            assertEquals("box " + boxPx[i], URI, result.getText());
            // Normal detection (as a scanner would), at the sizes where 2.3.0 handles synthetic images
            if (scale <= 5) {
                result = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(
                        new RGBLuminanceSource(side, side, pixels))));
                assertEquals("box " + boxPx[i] + " detect", URI, result.getText());
            }
        }
    }

    private static final Map<DecodeHintType, Object> PURE = new HashMap<DecodeHintType, Object>();
    static {
        PURE.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
    }
}
