package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import java.util.List;

/** Four generous app miniatures in a 2x2 grid. Extra members remain inside the folder. */
final class FolderPreviewDrawable extends Drawable {
    private final List<Drawable> artwork;
    private final RectF cell = new RectF();
    private final Rect savedBounds = new Rect();
    private final Path clip = new Path();
    FolderPreviewDrawable(List<Drawable> artwork) { this.artwork = artwork; }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        float padX = b.width() * .095f, padY = b.height() * .10f;
        float gapX = b.width() * .045f, gapY = b.height() * .055f;
        float w = (b.width() - padX * 2 - gapX) / 2;
        float h = (b.height() - padY * 2 - gapY) / 2;
        float radius = h * .18f;
        for (int i = 0; i < Math.min(4, artwork.size()); i++) {
            float x = b.left + padX + (i % 2) * (w + gapX);
            float y = b.top + padY + (i / 2) * (h + gapY);
            cell.set(x, y, x + w, y + h);
            Drawable app = artwork.get(i);
            if (app != null) {
                savedBounds.set(app.getBounds());
                int save = canvas.save();
                clip.reset();
                clip.addRoundRect(cell, radius, radius, Path.Direction.CW);
                canvas.clipPath(clip);
                int iw = app.getIntrinsicWidth(), ih = app.getIntrinsicHeight();
                if (iw <= 0 || ih <= 0) { iw = 5; ih = 3; }
                // Banners fill their miniature without distortion; square app icons keep their shape.
                float scale = iw > ih * 1.2f ? Math.max(w / iw, h / ih)
                        : Math.min(w * .86f / iw, h * .86f / ih);
                float aw = iw * scale, ah = ih * scale;
                app.setBounds(Math.round(cell.centerX() - aw / 2), Math.round(cell.centerY() - ah / 2),
                        Math.round(cell.centerX() + aw / 2), Math.round(cell.centerY() + ah / 2));
                app.draw(canvas);
                app.setBounds(savedBounds);
                canvas.restoreToCount(save);
            }
        }
    }
    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter filter) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
