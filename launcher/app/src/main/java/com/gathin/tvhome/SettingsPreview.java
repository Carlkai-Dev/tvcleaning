package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * A block sketch of Home for Launcher settings: the top bar, the dock band and the rows under it, drawn with
 * the launcher's own geometry (AppAdapter.tileWidthPx) on this TV's screen size, scaled to fit. It shows the
 * setting under focus before it is chosen. Drawn only when a setting changes; nothing animates.
 */
public class SettingsPreview extends View {

    /** The TV's own screen in dp (960x540 on 1080p and 4K sets, other sizes on others), sketched at scale. */
    private final float SCREEN_W, SCREEN_H;
    /** Muted stand-ins for app banners, so the tiles read as different apps without looking like any one. */
    private static final int[] TILE_COLORS = {0xFFE8E8EA, 0xFF2F6FE0, 0xFFD8D8DC, 0xFF7A3FE0,
            0xFF2A2A2E, 0xFFB8C8E8, 0xFFC0287A, 0xFF5C5CF0};

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private int columns = 4;
    private int rows = 1;
    private int dockOpacity = 100;
    private boolean animations = true;
    private boolean wallpaper;

    public SettingsPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        ring.setStyle(Paint.Style.STROKE);
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        ((android.view.WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
        float w = dm.widthPixels / dm.density, h = dm.heightPixels / dm.density;
        SCREEN_W = Math.max(w, h);
        SCREEN_H = Math.min(w, h);
    }

    void show(int columns, int rows, int dockOpacity, boolean animations, boolean wallpaper) {
        if (columns == this.columns && rows == this.rows && dockOpacity == this.dockOpacity
                && animations == this.animations && wallpaper == this.wallpaper) return;
        this.columns = columns;
        this.rows = rows;
        this.dockOpacity = dockOpacity;
        this.animations = animations;
        this.wallpaper = wallpaper;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(w, Math.round(w * SCREEN_H / SCREEN_W));   // always 16:9
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float k = getWidth() / SCREEN_W;   // dp of the sketched screen to pixels of this view
        canvas.save();
        r.set(0, 0, getWidth(), getHeight());
        fill.setShader(null);
        fill.setColor(0xFF000000);
        canvas.drawRoundRect(r, 16 * getResources().getDisplayMetrics().density,
                16 * getResources().getDisplayMetrics().density, fill);
        android.graphics.Path clip = new android.graphics.Path();
        float corner = 16 * getResources().getDisplayMetrics().density;
        clip.addRoundRect(r, corner, corner, android.graphics.Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.scale(k, k);

        // Background: a wallpaper reads as a dark gradient, otherwise black with the focused app's glow.
        if (wallpaper) {
            fill.setShader(new android.graphics.LinearGradient(0, 0, SCREEN_W, SCREEN_H,
                    0xFF1C2A4A, 0xFF3A1E3E, Shader.TileMode.CLAMP));
        } else {
            fill.setShader(new RadialGradient(SCREEN_W * 0.3f, SCREEN_H * 0.4f, SCREEN_W * 0.6f,
                    0x55C0283A, 0x00000000, Shader.TileMode.CLAMP));
        }
        canvas.drawRect(0, 0, SCREEN_W, SCREEN_H, fill);
        fill.setShader(null);

        // Top bar: the time and the date line on the left, three buttons on the right (Readable Home sizes).
        fill.setColor(0xFFFFFFFF);
        block(canvas, 56, 40, 52, 12, 3);
        fill.setColor(0x99FFFFFF);
        block(canvas, 56, 60, 150, 8, 3);
        fill.setColor(0x33FFFFFF);
        float[] pills = {104, 140, 118};
        float x = SCREEN_W - 56;
        for (int i = pills.length - 1; i >= 0; i--) {
            x -= pills[i];
            block(canvas, x, 38, pills[i], 36, 18);
            x -= 16;
        }

        // The grid, sized the way the launcher sizes it.
        float inner = SCREEN_W - 2 * (56 + 8);
        float gridH = SCREEN_H - 90;
        int tileW = AppAdapter.tileWidthPx(Math.round(inner), Math.round(gridH), columns, rows, false, true, 1f);
        int tileH = AppAdapter.tileHeightPx(tileW);
        float tileR = AppAdapter.cornerRadius(tileW, 1f);
        float cell = inner / columns;
        float left = 64;

        // Dock band, 24dp around the tiles with room for the focused name: 48dp under the top bar when rows show
        // below it, near the bottom of the screen when none do, as MainActivity places it.
        float bandH = 24 + tileH + 8 + 24 + 16;
        float bandTop = rows > 0 ? 80 + 48 : SCREEN_H - 50 - bandH;   // at 0 the next row peeks in below
        float tileTop = bandTop + 24;
        float bandBottom = bandTop + bandH;
        fill.setColor(Math.round(dockOpacity * 2.55f) << 24 | 0x141416);
        r.set(56, bandTop, SCREEN_W - 56, bandBottom);
        canvas.drawRoundRect(r, tileR + 24, tileR + 24, fill);
        for (int c = 0; c < columns; c++) {
            float cx = left + cell * c + (cell - tileW) / 2f;
            fill.setColor(TILE_COLORS[c % TILE_COLORS.length]);
            r.set(cx, tileTop, cx + tileW, tileTop + tileH);
            canvas.drawRoundRect(r, tileR, tileR, fill);
            if (c == 0) {
                focus(canvas, r, tileR);
                fill.setColor(0xFFFFFFFF);
                block(canvas, cx + tileW / 2f - tileW * 0.22f, tileTop + tileH + 10, tileW * 0.44f, 9, 3);
            }
        }

        // Rows under the dock: plain blocks, running off the bottom like the real grid.
        float rowTop = bandBottom + 32;
        int i = columns;
        while (rowTop < SCREEN_H) {
            for (int c = 0; c < columns; c++, i++) {
                float cx = left + cell * c + (cell - tileW) / 2f;
                fill.setColor((TILE_COLORS[(i * 3) % TILE_COLORS.length] & 0x00FFFFFF) | 0x8C000000);
                r.set(cx, rowTop, cx + tileW, rowTop + tileH);
                canvas.drawRoundRect(r, tileR, tileR, fill);
            }
            rowTop += tileH + 8 + 24 + 32;
        }
        canvas.restore();
    }

    private void block(Canvas canvas, float x, float y, float w, float h, float radius) {
        r.set(x, y, x + w, y + h);
        canvas.drawRoundRect(r, radius, radius, fill);
    }

    /** The glass frame: a 4dp white band, with its glow only when animations are on, as on Home. */
    private void focus(Canvas canvas, RectF tile, float tileR) {
        if (animations) {
            for (int i = 0; i < 8; i++) {
                float o = 4 + i;
                ring.setStrokeWidth(1.5f);
                ring.setColor(Math.round(0x30 * (1f - i / 8f)) << 24 | 0xFFFFFF);
                canvas.drawRoundRect(tile.left - o, tile.top - o, tile.right + o, tile.bottom + o,
                        tileR + o, tileR + o, ring);
            }
        }
        ring.setStrokeWidth(4);
        ring.setColor(0xF2FFFFFF);
        canvas.drawRoundRect(tile.left - 2, tile.top - 2, tile.right + 2, tile.bottom + 2, tileR + 2, tileR + 2, ring);
    }
}
