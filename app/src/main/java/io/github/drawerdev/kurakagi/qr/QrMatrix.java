package io.github.drawerdev.kurakagi.qr;

/**
 * 2D Matrix representation of a QR Code.
 * Fully compatible with Android 2.1 (API 7).
 */
public class QrMatrix {

    private final int width;
    private final int height;
    private final boolean[][] modules;

    public QrMatrix(int size) {
        this(size, size);
    }

    public QrMatrix(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be positive");
        }
        this.width = width;
        this.height = height;
        this.modules = new boolean[height][width];
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean get(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return false;
        }
        return modules[y][x];
    }

    public void set(int x, int y, boolean value) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            modules[y][x] = value;
        }
    }

    /**
     * Converts the QR matrix to an ASCII art string for debug / logs.
     */
    public String toAscii() {
        StringBuffer sb = new StringBuffer((width + 1) * height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                sb.append(modules[y][x] ? "##" : "  ");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Renders QR matrix into an ARGB_8888 pixel array with specified scale and quiet zone margin.
     * Ready for Android Bitmap.createBitmap(pixels, totalWidth, totalHeight, Bitmap.Config.RGB_565).
     */
    public int[] toPixelArray(int scale, int margin, int colorBlack, int colorWhite) {
        int s = scale <= 0 ? 1 : scale;
        int m = margin < 0 ? 0 : margin;
        int totalWidth = (width + 2 * m) * s;
        int totalHeight = (height + 2 * m) * s;
        int[] pixels = new int[totalWidth * totalHeight];

        for (int y = 0; y < totalHeight; y++) {
            int matrixY = (y / s) - m;
            for (int x = 0; x < totalWidth; x++) {
                int matrixX = (x / s) - m;
                boolean isBlack = false;
                if (matrixX >= 0 && matrixX < width && matrixY >= 0 && matrixY < height) {
                    isBlack = modules[matrixY][matrixX];
                }
                pixels[y * totalWidth + x] = isBlack ? colorBlack : colorWhite;
            }
        }
        return pixels;
    }
}
