package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import androidx.core.content.res.ResourcesCompat;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Generates ultra-premium 16:9 cinematic streaming card artwork for applications that lack
 * a widescreen TV banner. Transforms basic mobile icons into high-end Apple TV / Google TV
 * style posters with deep brand gradients, elevated emblems, and refined typography.
 */
public final class CinematicBannerFactory {

    private static final Map<String, Drawable> sCache = new HashMap<>();

    public static Drawable getOrGenerateBanner(Context ctx, String pkg, String label, Drawable icon, int accentColor) {
        String key = (pkg != null ? pkg : "") + "::" + (label != null ? label : "");
        Drawable cached = sCache.get(key);
        if (cached != null) return cached;

        Drawable generated = createBanner(ctx, pkg, label, icon, accentColor);
        sCache.put(key, generated);
        return generated;
    }

    private static Drawable createBanner(Context ctx, String pkg, String label, Drawable icon, int accentColor) {
        int w = 320;
        int h = 180;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        String lower = (pkg + " " + (label == null ? "" : label)).toLowerCase(Locale.US);

        Typeface fontMedium = null;
        try {
            fontMedium = ResourcesCompat.getFont(ctx, R.font.outfit_medium);
        } catch (Throwable ignored) {}
        if (fontMedium == null) fontMedium = Typeface.DEFAULT_BOLD;

        if (lower.contains("smarttube") || lower.contains("videomanager")) {
            renderSmartTubeBanner(canvas, w, h, fontMedium);
            return new BitmapDrawable(ctx.getResources(), bmp);
        } else if (lower.contains("cricfy")) {
            renderCricfyBanner(canvas, w, h, icon, fontMedium);
            return new BitmapDrawable(ctx.getResources(), bmp);
        } else if (lower.contains("downloader")) {
            renderDownloaderBanner(canvas, w, h, icon, fontMedium);
            return new BitmapDrawable(ctx.getResources(), bmp);
        } else if (lower.contains("appstore") || lower.contains("amazon.venezia")) {
            renderAppstoreBanner(canvas, w, h, icon, fontMedium);
            return new BitmapDrawable(ctx.getResources(), bmp);
        }

        renderGenericBrandCard(canvas, w, h, label, icon, accentColor, fontMedium);
        return new BitmapDrawable(ctx.getResources(), bmp);
    }

    private static void renderSmartTubeBanner(Canvas c, int w, int h, Typeface font) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Background: Rich Deep Burgundy / Crimson Nebula
        p.setShader(new LinearGradient(0, 0, w, h, 0xFF6B0E17, 0xFF140205, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);

        // Top specular rim
        p.setShader(new LinearGradient(0, 0, w, 0, 0x00FFFFFF, 0x4DFF3B4E, Shader.TileMode.CLAMP));
        p.setStrokeWidth(2f);
        p.setStyle(Paint.Style.STROKE);
        c.drawLine(0, 1, w, 1, p);
        p.setStyle(Paint.Style.FILL);

        // Center YouTube-style crimson badge
        float cx = w * 0.5f;
        float cy = h * 0.42f;
        RectF r = new RectF(cx - 38, cy - 26, cx + 38, cy + 26);
        p.setShader(null);
        p.setColor(0xFFE50914);
        c.drawRoundRect(r, 14, 14, p);

        // White play triangle
        p.setColor(Color.WHITE);
        Path tri = new Path();
        tri.moveTo(cx - 9, cy - 14);
        tri.lineTo(cx + 14, cy);
        tri.lineTo(cx - 9, cy + 14);
        tri.close();
        c.drawPath(tri, p);

        // "SmartTube" title
        p.setTypeface(font);
        p.setTextSize(20f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(0xFFF8FAFC);
        c.drawText("SmartTube", cx, h - 22, p);

        // Top-right 4K badge
        drawCornerBadge(c, w - 14, 14, "4K 60FPS", 0xFFE50914, font);
    }

    private static void renderCricfyBanner(Canvas c, int w, int h, Drawable icon, Typeface font) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Background: Midnight Stadium Emerald
        p.setShader(new LinearGradient(0, 0, w, h, 0xFF064E3B, 0xFF071322, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);

        // Icon
        float cx = w * 0.5f;
        drawCenteredIcon(c, icon, (int) cx, (int) (h * 0.40f), 58);

        // Title
        p.setShader(null);
        p.setTypeface(font);
        p.setTextSize(19f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(0xFFF8FAFC);
        c.drawText("CRICFy TV", cx, h - 22, p);

        drawCornerBadge(c, w - 14, 14, "LIVE SPORTS", 0xFF059669, font);
    }

    private static void renderDownloaderBanner(Canvas c, int w, int h, Drawable icon, Typeface font) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Background: Warm Carbon Amber
        p.setShader(new LinearGradient(0, 0, w, h, 0xFF7C2D12, 0xFF1C0A04, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);

        float cx = w * 0.5f;
        drawCenteredIcon(c, icon, (int) cx, (int) (h * 0.40f), 58);

        p.setShader(null);
        p.setTypeface(font);
        p.setTextSize(18f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(0xFFF8FAFC);
        c.drawText("Downloader", cx, h - 22, p);

        drawCornerBadge(c, w - 14, 14, "SIDELOAD", 0xFFD97706, font);
    }

    private static void renderAppstoreBanner(Canvas c, int w, int h, Drawable icon, Typeface font) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Background: Amazon Sapphire Slate
        p.setShader(new LinearGradient(0, 0, w, h, 0xFF0369A1, 0xFF071424, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);

        float cx = w * 0.5f;
        drawCenteredIcon(c, icon, (int) cx, (int) (h * 0.40f), 58);

        p.setShader(null);
        p.setTypeface(font);
        p.setTextSize(18f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(0xFFF8FAFC);
        c.drawText("Appstore", cx, h - 22, p);

        drawCornerBadge(c, w - 14, 14, "AMAZON", 0xFF0284C7, font);
    }

    private static void renderGenericBrandCard(Canvas c, int w, int h, String label, Drawable icon, int accent, Typeface font) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Calculate rich tone from accent
        float[] hsv = new float[3];
        if (accent != 0) {
            Color.colorToHSV(accent, hsv);
        } else {
            hsv[0] = 210f; hsv[1] = 0.70f; hsv[2] = 0.50f;
        }
        hsv[1] = Math.min(0.85f, Math.max(0.40f, hsv[1]));
        hsv[2] = 0.38f; // Deep, rich background shade
        int brandShade = Color.HSVToColor(hsv);

        p.setShader(new LinearGradient(0, 0, w, h, brandShade, 0xFF07111F, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);

        // Subtle top specular highlight
        p.setShader(new LinearGradient(0, 0, w, 0, 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        p.setStrokeWidth(1.5f);
        p.setStyle(Paint.Style.STROKE);
        c.drawLine(0, 1, w, 1, p);
        p.setStyle(Paint.Style.FILL);

        // Centered elevated icon
        float cx = w * 0.5f;
        drawCenteredIcon(c, icon, (int) cx, (int) (h * 0.40f), 58);

        // Frosted label strip at bottom
        p.setShader(null);
        p.setColor(0xCC07111F);
        RectF foot = new RectF(14, h - 34, w - 14, h - 10);
        c.drawRoundRect(foot, 8, 8, p);

        p.setColor(0x3338BDF8);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        c.drawRoundRect(foot, 8, 8, p);
        p.setStyle(Paint.Style.FILL);

        // Clean label
        String text = label != null ? label : "";
        p.setTypeface(font);
        p.setTextSize(13f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(0xFFF8FAFC);
        c.drawText(text, cx, h - 18, p);
    }

    private static void drawCenteredIcon(Canvas c, Drawable icon, int cx, int cy, int size) {
        if (icon == null) return;
        int half = size / 2;
        icon.setBounds(cx - half, cy - half, cx + half, cy + half);
        icon.draw(c);
    }

    private static void drawCornerBadge(Canvas c, float right, float top, String text, int color, Typeface font) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(font);
        p.setTextSize(10f);

        float textWidth = p.measureText(text);
        float padX = 7f;
        float padY = 3.5f;
        float bw = textWidth + padX * 2;
        float bh = 18f;

        RectF badgeRect = new RectF(right - bw, top, right, top + bh);

        // Badge background
        p.setColor(0x4D000000 | (color & 0xFFFFFF));
        c.drawRoundRect(badgeRect, 5, 5, p);

        // Badge border
        p.setColor(0x80000000 | (color & 0xFFFFFF));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        c.drawRoundRect(badgeRect, 5, 5, p);
        p.setStyle(Paint.Style.FILL);

        // Badge text
        p.setColor(0xFFF8FAFC);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(text, badgeRect.centerX(), badgeRect.centerY() + 3.5f, p);
    }
}
