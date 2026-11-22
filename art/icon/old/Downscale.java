// SPDX-License-Identifier: GPL-3.0-or-later
// Area-averaging downscaler for the icon PNGs (see art/icon/README.md).
// Usage: java Downscale.java <in.png> <size> <out.png> [<size> <out.png> ...]
// Each output pixel is the exact area-weighted mean of the input pixels it covers
// (alpha-premultiplied), so small sizes stay sharp without ringing.

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class Downscale {
    public static void main(String[] args) throws Exception {
        BufferedImage src = ImageIO.read(new File(args[0]));
        for (int i = 1; i + 1 < args.length; i += 2) {
            int size = Integer.parseInt(args[i]);
            ImageIO.write(scale(src, size), "png", new File(args[i + 1]));
        }
    }

    static BufferedImage scale(BufferedImage src, int size) {
        int sw = src.getWidth(), sh = src.getHeight();
        double fx = (double) sw / size, fy = (double) sh / size;
        boolean opaque = !src.getColorModel().hasAlpha();
        BufferedImage out = new BufferedImage(size, size,
                opaque ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            double y0 = y * fy, y1 = y0 + fy;
            for (int x = 0; x < size; x++) {
                double x0 = x * fx, x1 = x0 + fx;
                double a = 0, r = 0, g = 0, b = 0, w = 0;
                for (int sy = (int) y0; sy < Math.min(sh, Math.ceil(y1)); sy++) {
                    double wy = Math.min(y1, sy + 1) - Math.max(y0, sy);
                    for (int sx = (int) x0; sx < Math.min(sw, Math.ceil(x1)); sx++) {
                        double wx = Math.min(x1, sx + 1) - Math.max(x0, sx);
                        double k = wx * wy;
                        int p = src.getRGB(sx, sy);
                        double pa = ((p >>> 24) & 255) / 255.0;
                        a += pa * k;
                        r += ((p >> 16) & 255) * pa * k;
                        g += ((p >> 8) & 255) * pa * k;
                        b += (p & 255) * pa * k;
                        w += k;
                    }
                }
                int oa = (int) Math.round(a / w * 255);
                int or = a > 0 ? (int) Math.round(r / a) : 0;
                int og = a > 0 ? (int) Math.round(g / a) : 0;
                int ob = a > 0 ? (int) Math.round(b / a) : 0;
                out.setRGB(x, y, (oa << 24) | (or << 16) | (og << 8) | ob);
            }
        }
        return out;
    }
}
