package io.github.drawerdev.kurakagi.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * Camera NV21 / YUV420SP preview frame processing and QR code scanning engine.
 * Powered by pure-Java ZXing core engine (RFC ISO/IEC 18004 QR decoder).
 * Fully compatible with Android 2.1 (API 7) Dalvik VM.
 */
public final class QrYuvDecoder {

    private static final Map<DecodeHintType, Object> HINTS = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);

    static {
        HINTS.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        HINTS.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        Collection<BarcodeFormat> formats = new ArrayList<BarcodeFormat>(1);
        formats.add(BarcodeFormat.QR_CODE);
        HINTS.put(DecodeHintType.POSSIBLE_FORMATS, formats);
    }

    private QrYuvDecoder() {
        // Utility class
    }

    /**
     * Represents the result of a QR scan attempt.
     */
    public static class QrScanResult {
        public final boolean success;
        public final String text;
        public final FinderPattern[] patterns;
        public final long elapsedMs;
        public final String errorMessage;

        public QrScanResult(boolean success, String text, FinderPattern[] patterns, long elapsedMs, String errorMessage) {
            this.success = success;
            this.text = text;
            this.patterns = patterns;
            this.elapsedMs = elapsedMs;
            this.errorMessage = errorMessage;
        }

        public static QrScanResult success(String text, FinderPattern[] patterns, long elapsedMs) {
            return new QrScanResult(true, text, patterns, elapsedMs, null);
        }

        public static QrScanResult failure(String errorMessage, long elapsedMs) {
            return new QrScanResult(false, null, null, elapsedMs, errorMessage);
        }
    }

    /**
     * Coordinate and estimated size of a detected QR finder pattern.
     */
    public static class FinderPattern {
        public final float x;
        public final float y;
        public final float moduleSize;

        public FinderPattern(float x, float y, float moduleSize) {
            this.x = x;
            this.y = y;
            this.moduleSize = moduleSize;
        }

        @Override
        public String toString() {
            return "FinderPattern(" + x + ", " + y + ", size=" + moduleSize + ")";
        }
    }

    /**
     * Extracts the luminance (Y) plane from NV21 camera preview data.
     * The Y plane consists of the first (width * height) bytes.
     */
    public static byte[] extractYPlane(byte[] nv21, int width, int height) {
        if (nv21 == null) {
            throw new IllegalArgumentException("NV21 buffer cannot be null");
        }
        int ySize = width * height;
        if (nv21.length < ySize) {
            throw new IllegalArgumentException("NV21 buffer smaller than width*height");
        }
        byte[] yPlane = new byte[ySize];
        System.arraycopy(nv21, 0, yPlane, 0, ySize);
        return yPlane;
    }

    /**
     * Crops a rectangular region of interest from the luminance (Y) plane.
     */
    public static byte[] cropYPlane(byte[] yPlane, int srcWidth, int srcHeight,
                                    int cropLeft, int cropTop, int cropWidth, int cropHeight) {
        if (cropLeft < 0 || cropTop < 0 || cropLeft + cropWidth > srcWidth || cropTop + cropHeight > srcHeight) {
            throw new IllegalArgumentException("Crop bounds out of image range");
        }
        byte[] cropped = new byte[cropWidth * cropHeight];
        for (int y = 0; y < cropHeight; y++) {
            int srcPos = (cropTop + y) * srcWidth + cropLeft;
            int dstPos = y * cropWidth;
            System.arraycopy(yPlane, srcPos, cropped, dstPos, cropWidth);
        }
        return cropped;
    }

    /**
     * Rotates a luminance plane by 90 degrees clockwise (for portrait mode cameras).
     */
    public static byte[] rotateYPlane90(byte[] yPlane, int width, int height) {
        byte[] rotated = new byte[width * height];
        int i = 0;
        for (int x = 0; x < width; x++) {
            for (int y = height - 1; y >= 0; y--) {
                rotated[i++] = yPlane[y * width + x];
            }
        }
        return rotated;
    }

    /**
     * Performs global Otsu binarization on the luminance plane.
     */
    public static boolean[] binarizeOtsu(byte[] yPlane, int width, int height) {
        int total = width * height;
        int[] histogram = new int[256];
        for (int i = 0; i < total; i++) {
            histogram[yPlane[i] & 0xFF]++;
        }

        float sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += i * histogram[i];
        }

        float sumB = 0;
        int wB = 0;
        float varMax = 0;
        int threshold = 128;

        for (int t = 0; t < 256; t++) {
            wB += histogram[t];
            if (wB == 0) continue;
            int wF = total - wB;
            if (wF == 0) break;

            sumB += (float) (t * histogram[t]);
            float mB = sumB / wB;
            float mF = (sum - sumB) / wF;

            float varBetween = (float) wB * (float) wF * (mB - mF) * (mB - mF);
            if (varBetween > varMax) {
                varMax = varBetween;
                threshold = t;
            }
        }

        boolean[] binary = new boolean[total];
        for (int i = 0; i < total; i++) {
            binary[i] = (yPlane[i] & 0xFF) < threshold;
        }
        return binary;
    }

    /**
     * Performs local adaptive binarization using integral image.
     */
    public static boolean[] binarizeAdaptive(byte[] yPlane, int width, int height, int windowSize, int cConstant) {
        int total = width * height;
        int s = windowSize / 2;
        if (s < 1) s = 1;

        long[] integral = new long[(width + 1) * (height + 1)];
        for (int y = 0; y < height; y++) {
            long rowSum = 0;
            for (int x = 0; x < width; x++) {
                rowSum += (yPlane[y * width + x] & 0xFF);
                integral[(y + 1) * (width + 1) + (x + 1)] = integral[y * (width + 1) + (x + 1)] + rowSum;
            }
        }

        boolean[] binary = new boolean[total];
        for (int y = 0; y < height; y++) {
            int y1 = Math.max(0, y - s);
            int y2 = Math.min(height - 1, y + s);
            for (int x = 0; x < width; x++) {
                int x1 = Math.max(0, x - s);
                int x2 = Math.min(width - 1, x + s);

                long count = (x2 - x1 + 1) * (y2 - y1 + 1);
                long sum = integral[(y2 + 1) * (width + 1) + (x2 + 1)]
                         - integral[y1 * (width + 1) + (x2 + 1)]
                         - integral[(y2 + 1) * (width + 1) + x1]
                         + integral[y1 * (width + 1) + x1];

                int pixel = yPlane[y * width + x] & 0xFF;
                binary[y * width + x] = ((long) pixel * count) < (sum * (100 - cConstant) / 100);
            }
        }
        return binary;
    }

    /**
     * Scans binarized image for 1:1:3:1:1 QR Finder Patterns.
     */
    public static java.util.List findFinderPatterns(boolean[] binary, int width, int height) {
        java.util.List list = new java.util.ArrayList();
        int[] stateCount = new int[5];

        for (int y = 0; y < height; y++) {
            stateCount[0] = 0;
            stateCount[1] = 0;
            stateCount[2] = 0;
            stateCount[3] = 0;
            stateCount[4] = 0;
            int currentState = 0;

            for (int x = 0; x < width; x++) {
                boolean isBlack = binary[y * width + x];
                if (isBlack) {
                    if ((currentState & 1) == 1) {
                        currentState++;
                    }
                    stateCount[currentState]++;
                } else {
                    if ((currentState & 1) == 0) {
                        if (currentState == 4) {
                            if (checkFinderRatio(stateCount)) {
                                float centerCol = centerFromEnd(stateCount, x);
                                float moduleSize = (float) (stateCount[0] + stateCount[1] + stateCount[2] + stateCount[3] + stateCount[4]) / 7.0f;
                                list.add(new FinderPattern(centerCol, y, moduleSize));
                            }
                            stateCount[0] = stateCount[2];
                            stateCount[1] = stateCount[3];
                            stateCount[2] = stateCount[4];
                            stateCount[3] = 1;
                            stateCount[4] = 0;
                            currentState = 3;
                            continue;
                        } else {
                            currentState++;
                        }
                    }
                    stateCount[currentState]++;
                }
            }
        }
        return list;
    }

    /**
     * Verifies the 1:1:3:1:1 module ratio within 50% tolerance.
     */
    public static boolean checkFinderRatio(int[] stateCount) {
        int total = 0;
        for (int i = 0; i < 5; i++) {
            int c = stateCount[i];
            if (c == 0) return false;
            total += c;
        }
        if (total < 7) return false;
        float moduleSize = (float) total / 7.0f;
        float maxVariance = moduleSize / 2.0f;

        return Math.abs(moduleSize - stateCount[0]) < maxVariance &&
               Math.abs(moduleSize - stateCount[1]) < maxVariance &&
               Math.abs(3.0f * moduleSize - stateCount[2]) < 3.0f * maxVariance &&
               Math.abs(moduleSize - stateCount[3]) < maxVariance &&
               Math.abs(moduleSize - stateCount[4]) < maxVariance;
    }

    private static float centerFromEnd(int[] stateCount, int end) {
        return (float) (end - stateCount[4] - stateCount[3]) - (float) stateCount[2] / 2.0f;
    }

    /**
     * High-level entry point to process an NV21 camera frame.
     */
    public static QrScanResult processPreviewFrame(byte[] nv21, int width, int height) {
        long start = System.currentTimeMillis();
        if (nv21 == null || width <= 0 || height <= 0) {
            return QrScanResult.failure("Invalid camera frame", 0);
        }

        byte[] yPlane = extractYPlane(nv21, width, height);
        boolean[] binary = binarizeOtsu(yPlane, width, height);
        java.util.List patternsList = findFinderPatterns(binary, width, height);

        FinderPattern[] patterns = new FinderPattern[patternsList.size()];
        for (int i = 0; i < patternsList.size(); i++) {
            patterns[i] = (FinderPattern) patternsList.get(i);
        }

        long elapsed = System.currentTimeMillis() - start;
        if (patterns.length >= 3) {
            return QrScanResult.success("Found " + patterns.length + " finder patterns", patterns, elapsed);
        } else {
            return QrScanResult.failure("Insufficient finder patterns (" + patterns.length + ")", elapsed);
        }
    }

    /**
     * Decodes a QR code directly from a luminance (Y) plane buffer.
     * The input buffer is assumed to be width * height in size (1 byte per pixel).
     */
    public static String decodeYPlane(byte[] yPlane, int width, int height,
                                     int cropLeft, int cropTop, int cropWidth, int cropHeight) {
        if (yPlane == null || width <= 0 || height <= 0) {
            return null;
        }

        // クロップ境界のクランプ
        if (cropLeft < 0) cropLeft = 0;
        if (cropTop < 0) cropTop = 0;
        if (cropWidth <= 0 || cropHeight <= 0) return null;
        if (cropLeft + cropWidth > width) cropWidth = width - cropLeft;
        if (cropTop + cropHeight > height) cropHeight = height - cropTop;

        try {
            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                    yPlane, width, height, cropLeft, cropTop, cropWidth, cropHeight, false
            );
            String result = tryDecode(source);
            if (result != null && result.length() > 0) {
                return result;
            }

            // 反転（ネガコード）対応
            try {
                LuminanceSource inverted = source.invert();
                result = tryDecode(inverted);
                if (result != null && result.length() > 0) {
                    return result;
                }
            } catch (Exception ignore) {
            }

        } catch (Exception ignore) {
        }
        return null;
    }

    /**
     * Decodes QR code text from camera NV21 byte buffer using ZXing engine.
     * Supports optional cropping bounds and automatic orientation handling.
     */
    public static String decodeNv21(byte[] data, int width, int height,
                                    int cropLeft, int cropTop, int cropWidth, int cropHeight,
                                    boolean isPortrait) {
        if (data == null || width <= 0 || height <= 0) {
            return null;
        }

        // クロップ境界のクランプ
        if (cropLeft < 0) cropLeft = 0;
        if (cropTop < 0) cropTop = 0;
        if (cropLeft + cropWidth > width) cropWidth = width - cropLeft;
        if (cropTop + cropHeight > height) cropHeight = height - cropTop;

        try {
            // 1. クロップ領域（照準枠内）での検出を優先
            if (cropWidth > 50 && cropHeight > 50) {
                PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                        data, width, height, cropLeft, cropTop, cropWidth, cropHeight, false
                );
                String result = decodeLuminanceSource(source, isPortrait);
                if (result != null && result.length() > 0) {
                    return result;
                }
            }

            // 2. クロップ外にはみ出している場合、全画面で検出
            PlanarYUVLuminanceSource fullSource = new PlanarYUVLuminanceSource(
                    data, width, height, 0, 0, width, height, false
            );
            String fullResult = decodeLuminanceSource(fullSource, isPortrait);
            if (fullResult != null && fullResult.length() > 0) {
                return fullResult;
            }

        } catch (Exception ignore) {
        }
        return null;
    }

    private static String decodeLuminanceSource(LuminanceSource source, boolean isPortrait) {
        // 通常向きでデコード
        String res = tryDecode(source);
        if (res != null) return res;

        // 縦画面カメラの場合、90度回転してデコード
        if (isPortrait && source.isRotateSupported()) {
            LuminanceSource rotated = source.rotateCounterClockwise();
            res = tryDecode(rotated);
            if (res != null) return res;
        }

        return null;
    }

    private static String tryDecode(LuminanceSource source) {
        try {
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            QRCodeReader reader = new QRCodeReader();
            Result result = reader.decode(bitmap, HINTS);
            if (result != null && result.getText() != null) {
                return result.getText();
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    /**
     * Backward-compatible decode method.
     */
    public static String decode(byte[] nv21, int width, int height, int cropLeft, int cropTop, int cropWidth, int cropHeight) {
        return decodeNv21(nv21, width, height, cropLeft, cropTop, cropWidth, cropHeight, false);
    }
}
