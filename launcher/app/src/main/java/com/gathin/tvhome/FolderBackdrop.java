package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

/** Cached backdrop snapshots for quick settings and small menu/dialog surfaces. */
final class FolderBackdrop {
    static Bitmap capture(View home) {
        int width = 160, height = Math.max(1, width * home.getHeight() / Math.max(1, home.getWidth()));
        return capture(home, new android.graphics.Rect(0, 0, home.getWidth(), home.getHeight()), width, height, 5);
    }

    /** Capture only the material's footprint, so neighbouring app text cannot show through sharply. */
    static Bitmap capture(View home, android.graphics.Rect region, int width, int height, int radius) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(width / (float) region.width(), height / (float) region.height());
        canvas.translate(-region.left, -region.top);
        home.draw(canvas);
        return soften(bitmap, radius);
    }

    private static Bitmap soften(Bitmap bitmap, int radius) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] pixels = new int[width * height], scratch = new int[pixels.length];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int pass = 0; pass < 3; pass++) {
            blur(pixels, scratch, width, height, true, radius);
            blur(scratch, pixels, width, height, false, radius);
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    private static void blur(int[] source, int[] target, int width, int height, boolean horizontal, int radius) {
        int count = radius * 2 + 1;
        int lines = horizontal ? height : width, length = horizontal ? width : height;
        int step = horizontal ? 1 : width;
        for (int line = 0; line < lines; line++) {
            int base = horizontal ? line * width : line;
            int red = 0, green = 0, blue = 0;
            for (int i = -radius; i <= radius; i++) {
                int pixel = source[base + Math.max(0, Math.min(length - 1, i)) * step];
                red += (pixel >> 16) & 255; green += (pixel >> 8) & 255; blue += pixel & 255;
            }
            for (int i = 0; i < length; i++) {
                target[base + i * step] = 0xFF000000 | (red / count) << 16 | (green / count) << 8 | blue / count;
                int removed = source[base + Math.max(0, i - radius) * step];
                int added = source[base + Math.min(length - 1, i + radius + 1) * step];
                red += ((added >> 16) & 255) - ((removed >> 16) & 255);
                green += ((added >> 8) & 255) - ((removed >> 8) & 255);
                blue += (added & 255) - (removed & 255);
            }
        }
    }
}
