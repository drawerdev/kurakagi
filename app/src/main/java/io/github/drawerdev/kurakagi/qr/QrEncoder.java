package io.github.drawerdev.kurakagi.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.EnumMap;
import java.util.Map;

/**
 * QR Code Encoder engine (ISO/IEC 18004).
 * Powered by pure-Java ZXing core engine.
 * Fully compatible with Android 2.1 (API 7) Dalvik VM.
 */
public final class QrEncoder {

    public enum ErrorCorrectionLevel {
        L(com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L),
        M(com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M),
        Q(com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.Q),
        H(com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H);

        public final com.google.zxing.qrcode.decoder.ErrorCorrectionLevel zxingLevel;

        ErrorCorrectionLevel(com.google.zxing.qrcode.decoder.ErrorCorrectionLevel zxingLevel) {
            this.zxingLevel = zxingLevel;
        }
    }

    private QrEncoder() {
        // Utility class
    }

    /**
     * Encodes a string into a QrMatrix with default Error Correction Level L.
     */
    public static QrMatrix encode(String content) {
        return encode(content, ErrorCorrectionLevel.L);
    }

    /**
     * Encodes a string into a QrMatrix with specified Error Correction Level.
     */
    public static QrMatrix encode(String content, ErrorCorrectionLevel ec) {
        if (content == null || content.length() == 0) {
            throw new IllegalArgumentException("Content cannot be null or empty");
        }
        if (ec == null) {
            ec = ErrorCorrectionLevel.L;
        }

        try {
            Map<EncodeHintType, Object> hints = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ec.zxingLevel);
            hints.put(EncodeHintType.MARGIN, 0); // 余白は QrMatrix.toPixelArray で付与

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 0, 0, hints);

            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            QrMatrix matrix = new QrMatrix(width, height);

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    matrix.set(x, y, bitMatrix.get(x, y));
                }
            }

            return matrix;
        } catch (Exception e) {
            throw new RuntimeException("QR Encode failed: " + e.getMessage(), e);
        }
    }
}
