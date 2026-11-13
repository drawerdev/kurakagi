package io.github.drawerdev.kurakagi;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import io.github.drawerdev.kurakagi.qr.QrEncoder;
import io.github.drawerdev.kurakagi.qr.QrMatrix;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The QR on the About screen (SettingsActivity.showOfficialQr): the official download URL,
 * drawn with a 4-module quiet zone, must read back as the same URL.
 */
public class OfficialQrTest {

    private static final String URL = "https://drawerdev.github.io/";
    private static final int QUIET_ZONE = 4;
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    @Test
    public void officialUrlReadsBack() throws Exception {
        for (int scale = 2; scale <= 9; scale++) {
            QrMatrix matrix = QrEncoder.encode(URL, QrEncoder.ErrorCorrectionLevel.M);
            int side = (matrix.getWidth() + 2 * QUIET_ZONE) * scale;
            int[] pixels = matrix.toPixelArray(scale, QUIET_ZONE, BLACK, WHITE);
            assertEquals(side * side, pixels.length);

            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(
                    new RGBLuminanceSource(side, side, pixels)));
            Result result = new QRCodeReader().decode(bitmap);
            assertEquals("scale " + scale, URL, result.getText());
        }
    }

    @Test
    public void quietZoneIsFourWhiteModules() {
        int scale = 3;
        QrMatrix matrix = QrEncoder.encode(URL, QrEncoder.ErrorCorrectionLevel.M);
        int side = (matrix.getWidth() + 2 * QUIET_ZONE) * scale;
        int[] pixels = matrix.toPixelArray(scale, QUIET_ZONE, BLACK, WHITE);
        int border = QUIET_ZONE * scale;
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                boolean inBorder = x < border || y < border || x >= side - border || y >= side - border;
                if (inBorder) {
                    assertEquals("(" + x + "," + y + ")", WHITE, pixels[y * side + x]);
                }
            }
        }
        // The finder pattern starts right after the quiet zone.
        assertEquals(BLACK, pixels[border * side + border]);
    }
}
