package com.patilweb.tvcam;

import android.graphics.Bitmap;
import android.media.Image;

import java.nio.ByteBuffer;

/**
 * Cheap motion estimate from YUV Y-plane or RGB bitmap luma deltas.
 */
final class MotionDetector {
    private int[] previous;
    private int cols;
    private int rows;
    private long lastMotionMs;
    private static final int STEP = 8;

    /** @return true if this frame shows motion above threshold. */
    synchronized boolean analyzeImage(Image image, int threshold) {
        Image.Plane yPlane = image.getPlanes()[0];
        ByteBuffer buf = yPlane.getBuffer();
        int rowStride = yPlane.getRowStride();
        int w = image.getWidth();
        int h = image.getHeight();
        int c = sampleCount(w);
        int r = sampleCount(h);
        if (c <= 0 || r <= 0) return false;

        int samples = c * r;
        ensureBuffer(samples);
        if (cols != c || rows != r) {
            int i = 0;
            for (int y = 0; y < r; y++) {
                int py = y * STEP;
                for (int x = 0; x < c; x++) {
                    previous[i++] = buf.get(py * rowStride + x * STEP) & 0xff;
                }
            }
            cols = c;
            rows = r;
            return false;
        }

        long sum = 0;
        int i = 0;
        for (int y = 0; y < r; y++) {
            int py = y * STEP;
            for (int x = 0; x < c; x++) {
                int cur = buf.get(py * rowStride + x * STEP) & 0xff;
                sum += Math.abs(cur - previous[i]);
                previous[i++] = cur;
            }
        }
        return finish(sum, samples, threshold);
    }

    /** @return true if this bitmap shows motion above threshold. */
    synchronized boolean analyzeBitmap(Bitmap bitmap, int threshold) {
        if (bitmap == null || bitmap.isRecycled()) return false;
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int c = sampleCount(w);
        int r = sampleCount(h);
        if (c <= 0 || r <= 0) return false;

        int samples = c * r;
        ensureBuffer(samples);
        if (cols != c || rows != r) {
            int i = 0;
            for (int y = 0; y < r; y++) {
                int py = Math.min(h - 1, y * STEP);
                for (int x = 0; x < c; x++) {
                    int px = Math.min(w - 1, x * STEP);
                    previous[i++] = luma(bitmap.getPixel(px, py));
                }
            }
            cols = c;
            rows = r;
            return false;
        }

        long sum = 0;
        int i = 0;
        for (int y = 0; y < r; y++) {
            int py = Math.min(h - 1, y * STEP);
            for (int x = 0; x < c; x++) {
                int px = Math.min(w - 1, x * STEP);
                int cur = luma(bitmap.getPixel(px, py));
                sum += Math.abs(cur - previous[i]);
                previous[i++] = cur;
            }
        }
        return finish(sum, samples, threshold);
    }

    synchronized long millisSinceMotion() {
        if (lastMotionMs == 0) return Long.MAX_VALUE;
        return System.currentTimeMillis() - lastMotionMs;
    }

    synchronized void reset() {
        previous = null;
        cols = 0;
        rows = 0;
        lastMotionMs = 0;
    }

    private static int sampleCount(int size) {
        // Floor so y*STEP / x*STEP always stay inside [0, size).
        return Math.max(0, size / STEP);
    }

    private void ensureBuffer(int samples) {
        if (previous == null || previous.length < samples) {
            previous = new int[samples];
            cols = 0;
            rows = 0;
        }
    }

    private boolean finish(long sum, int samples, int threshold) {
        if (samples <= 0) return false;
        double avg = sum / (double) samples;
        if (avg >= threshold) {
            lastMotionMs = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    private static int luma(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        return (r * 30 + g * 59 + b * 11) / 100;
    }
}
