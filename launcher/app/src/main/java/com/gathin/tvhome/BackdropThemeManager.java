package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import androidx.annotation.Nullable;

import java.util.Random;

/**
 * Manages Home Screen visual aesthetics:
 * 1. Rich ambient vignette gradients (Ruby, Sapphire, Emerald, Amethyst, Amber, Dynamic App Tint, OLED Black).
 * 2. Curated high-resolution TV wallpapers (Cosmic Space, Minimalist Peaks, Obsidian Waves, Nordic Aurora).
 * 3. Seamless switching and live preview between Gradient and Wallpaper modes.
 */
public class BackdropThemeManager {
    private static final String PREFS_NAME = "backdrop_prefs";
    private static final String KEY_MODE = "backdrop_mode"; // "gradient" or "wallpaper"
    private static final String KEY_GRADIENT = "gradient_preset";
    private static final String KEY_WALLPAPER = "wallpaper_preset";

    public static final String MODE_GRADIENT = "gradient";
    public static final String MODE_WALLPAPER = "wallpaper";

    // Gradient Presets
    public static final String GRAD_RUBY = "ruby"; // Default from screenshot 1
    public static final String GRAD_DYNAMIC = "dynamic";
    public static final String GRAD_SAPPHIRE = "sapphire";
    public static final String GRAD_EMERALD = "emerald";
    public static final String GRAD_AMETHYST = "amethyst";
    public static final String GRAD_AMBER = "amber";
    public static final String GRAD_OLED = "oled";

    // Wallpaper Presets
    public static final String WALL_SPACE = "space";
    public static final String WALL_PEAKS = "peaks";
    public static final String WALL_OBSIDIAN = "obsidian";
    public static final String WALL_AURORA = "aurora";

    private static BackdropThemeManager instance;
    private final SharedPreferences prefs;
    private Bitmap cachedBitmap;
    private String cachedKey = "";

    private BackdropThemeManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized BackdropThemeManager getInstance(Context context) {
        if (instance == null) {
            instance = new BackdropThemeManager(context);
        }
        return instance;
    }

    public String getMode() {
        return prefs.getString(KEY_MODE, MODE_GRADIENT);
    }

    public void setMode(String mode) {
        prefs.edit().putString(KEY_MODE, mode).apply();
        clearCache();
    }

    public String getGradientPreset() {
        return prefs.getString(KEY_GRADIENT, GRAD_SAPPHIRE);
    }

    public void setGradientPreset(String preset) {
        prefs.edit().putString(KEY_GRADIENT, preset).apply();
        clearCache();
    }

    public String getWallpaperPreset() {
        return prefs.getString(KEY_WALLPAPER, WALL_SPACE);
    }

    public void setWallpaperPreset(String preset) {
        prefs.edit().putString(KEY_WALLPAPER, preset).apply();
        clearCache();
    }

    public void clearCache() {
        if (cachedBitmap != null) {
            cachedBitmap.recycle();
            cachedBitmap = null;
        }
        cachedKey = "";
    }

    /**
     * Renders the current backdrop bitmap (low CPU, high performance 960x540 buffer stretched to 1080p/4K).
     */
    public Bitmap getBackdropBitmap(int screenWidth, int screenHeight, @Nullable Integer dynamicAppColor) {
        String mode = getMode();
        String preset = MODE_WALLPAPER.equals(mode) ? getWallpaperPreset() : getGradientPreset();
        int dyn = (dynamicAppColor != null) ? dynamicAppColor : 0;
        String key = mode + "_" + preset + "_" + (GRAD_DYNAMIC.equals(preset) ? dyn : 0);

        if (cachedBitmap != null && key.equals(cachedKey)) {
            return cachedBitmap;
        }

        // Texture size: 960x540 gives crystal sharp gradients with minimal memory footprint (only ~2MB)
        int w = 960;
        int h = 540;

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        if (MODE_WALLPAPER.equals(mode)) {
            renderWallpaper(canvas, w, h, preset);
        } else {
            renderGradient(canvas, w, h, preset, dynamicAppColor);
        }

        if (cachedBitmap != null) cachedBitmap.recycle();
        cachedBitmap = bmp;
        cachedKey = key;
        return bmp;
    }

    private void renderGradient(Canvas canvas, int w, int h, String preset, @Nullable Integer dynamicColor) {
        int bgColor = 0xFF060608;
        int primaryColor = 0xFFD32F2F; // Ruby default

        if (GRAD_RUBY.equals(preset)) {
            bgColor = 0xFF070507;
            primaryColor = 0xFFD32F2F;
        } else if (GRAD_SAPPHIRE.equals(preset)) {
            // Aurora Sapphire Design: Deep Midnight Navy (#07111F) + Sapphire Nebula + Celestial Cyan
            canvas.drawColor(0xFF07111F);

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            // Core Sapphire Blue Nebula centered at (0.32w, 0.38h)
            paint.setShader(new RadialGradient(w * 0.32f, h * 0.38f, w * 0.70f,
                    new int[]{0x991E4E8C, 0x4D123158, 0x1A091C33, 0x00000000},
                    new float[]{0f, 0.40f, 0.75f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);

            // Restrained Celestial Cyan ambient shimmer at top-right (0.80w, 0.22h)
            paint.setShader(new RadialGradient(w * 0.80f, h * 0.22f, w * 0.42f,
                    new int[]{0x3338BDF8, 0x140284C7, 0x00000000},
                    new float[]{0f, 0.50f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);

            // Subtle dark vignette to preserve contrast for cards and header
            paint.setShader(new RadialGradient(w * 0.5f, h * 0.5f, w * 0.75f,
                    new int[]{0x00000000, 0x66030914},
                    new float[]{0.55f, 1.0f},
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            return;
        } else if (GRAD_EMERALD.equals(preset)) {
            bgColor = 0xFF040907;
            primaryColor = 0xFF00897B;
        } else if (GRAD_AMETHYST.equals(preset)) {
            bgColor = 0xFF08040C;
            primaryColor = 0xFF8E24AA;
        } else if (GRAD_AMBER.equals(preset)) {
            bgColor = 0xFF0C0604;
            primaryColor = 0xFFF4511E;
        } else if (GRAD_DYNAMIC.equals(preset)) {
            bgColor = 0xFF060608;
            primaryColor = (dynamicColor != null && dynamicColor != -1) ? dynamicColor : 0xFFD32F2F;
        } else if (GRAD_OLED.equals(preset)) {
            canvas.drawColor(0xFF000000);
            return;
        }

        canvas.drawColor(bgColor);

        // Warm ambient radial glow centered at (0.30w, 0.40h) like screenshot 1
        float cx = w * 0.30f;
        float cy = h * 0.40f;
        float radius = w * 0.65f;

        int r = (primaryColor >> 16) & 0xFF;
        int g = (primaryColor >> 8) & 0xFF;
        int b = primaryColor & 0xFF;

        int c1 = Color.argb(145, r, g, b); // Soft warm center core
        int c2 = Color.argb(65, r, g, b);
        int c3 = Color.argb(20, r, g, b);
        int c4 = Color.argb(0, r, g, b);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new RadialGradient(cx, cy, radius,
                new int[]{c1, c2, c3, c4},
                new float[]{0f, 0.35f, 0.70f, 1f},
                Shader.TileMode.CLAMP));

        canvas.drawRect(0, 0, w, h, paint);

        // Subtle dark corner vignette to focus center-left app tiles
        paint.setShader(new RadialGradient(w * 0.5f, h * 0.5f, w * 0.75f,
                new int[]{0x00000000, 0x88000000},
                new float[]{0.5f, 1.0f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
    }

    private void renderWallpaper(Canvas canvas, int w, int h, String preset) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        if (WALL_SPACE.equals(preset)) {
            // Cosmic Deep Space
            p.setShader(new LinearGradient(0, 0, 0, h, 0xFF03040A, 0xFF0A0D1A, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, p);

            // Purple & Cyan Nebula Clouds
            p.setShader(new RadialGradient(w * 0.35f, h * 0.45f, w * 0.45f,
                    new int[]{0x456A1B9A, 0x221565C0, 0x00000000},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, p);

            p.setShader(new RadialGradient(w * 0.75f, h * 0.30f, w * 0.35f,
                    new int[]{0x3500897B, 0x00000000},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, p);

            // Stars
            Random rnd = new Random(1337);
            Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            for (int i = 0; i < 220; i++) {
                float sx = rnd.nextFloat() * w;
                float sy = rnd.nextFloat() * h;
                float sr = 0.6f + rnd.nextFloat() * 1.4f;
                int alpha = 70 + rnd.nextInt(185);
                starPaint.setColor(Color.argb(alpha, 230, 240, 255));
                canvas.drawCircle(sx, sy, sr, starPaint);
            }

        } else if (WALL_PEAKS.equals(preset)) {
            // Minimalist Dark Peaks
            p.setShader(new LinearGradient(0, 0, 0, h, 0xFF080C16, 0xFF14213D, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, p);

            // Moon / Horizon Glow
            p.setShader(new RadialGradient(w * 0.65f, h * 0.55f, w * 0.35f,
                    new int[]{0x55E0E8F5, 0x184A6572, 0x00000000},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, p);

            // Background Mountains
            Path bgRidge = new Path();
            bgRidge.moveTo(0, h * 0.65f);
            bgRidge.lineTo(w * 0.22f, h * 0.50f);
            bgRidge.lineTo(w * 0.45f, h * 0.62f);
            bgRidge.lineTo(w * 0.68f, h * 0.46f);
            bgRidge.lineTo(w * 0.88f, h * 0.58f);
            bgRidge.lineTo(w, h * 0.52f);
            bgRidge.lineTo(w, h);
            bgRidge.lineTo(0, h);
            bgRidge.close();
            p.setShader(null);
            p.setColor(0xFF101B2B);
            canvas.drawPath(bgRidge, p);

            // Foreground Mountains
            Path fgRidge = new Path();
            fgRidge.moveTo(0, h * 0.75f);
            fgRidge.lineTo(w * 0.32f, h * 0.58f);
            fgRidge.lineTo(w * 0.55f, h * 0.72f);
            fgRidge.lineTo(w * 0.78f, h * 0.56f);
            fgRidge.lineTo(w, h * 0.68f);
            fgRidge.lineTo(w, h);
            fgRidge.lineTo(0, h);
            fgRidge.close();
            p.setColor(0xFF070B12);
            canvas.drawPath(fgRidge, p);

        } else if (WALL_AURORA.equals(preset)) {
            // Nordic Aurora
            p.setShader(new LinearGradient(0, 0, 0, h, 0xFF03080E, 0xFF05131C, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, p);

            // Emerald Aurora ribbon wave
            p.setShader(new RadialGradient(w * 0.40f, h * 0.38f, w * 0.50f,
                    new int[]{0x6500E676, 0x301DE9B6, 0x00000000},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, p);

            // Violet upper fringe
            p.setShader(new RadialGradient(w * 0.55f, h * 0.22f, w * 0.40f,
                    new int[]{0x40AA00FF, 0x00000000},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, p);

            // Distant hills horizon
            p.setShader(null);
            p.setColor(0xFF040A0F);
            canvas.drawRect(0, h * 0.78f, w, h, p);

        } else {
            // Obsidian Abstract Waves
            p.setShader(new LinearGradient(0, 0, w, h, 0xFF08080C, 0xFF151620, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, p);

            p.setShader(new RadialGradient(w * 0.25f, h * 0.35f, w * 0.45f,
                    new int[]{0x33455A64, 0x00000000},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, p);

            p.setShader(new RadialGradient(w * 0.70f, h * 0.65f, w * 0.40f,
                    new int[]{0x2237474F, 0x00000000},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, p);
        }

        // Soft 35% dark scrim across wallpapers so all icons & text maintain TV contrast
        p.setShader(null);
        p.setColor(0x59000000);
        canvas.drawRect(0, 0, w, h, p);
    }
}
