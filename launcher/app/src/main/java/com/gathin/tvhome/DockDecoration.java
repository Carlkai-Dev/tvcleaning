package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Translucent dock glass. Cached gradients need no bitmap, blur pass or idle animation. */
public class DockDecoration extends RecyclerView.ItemDecoration {

    static final float VERTICAL_PADDING_DP = 19f;

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Rect visibleBounds = new Rect();
    private final Rect canvasBounds = new Rect();
    private final int[] screenLocation = new int[2];
    private final Matrix shaderPosition = new Matrix();
    private LinearGradient surfaceGradient;
    private LinearGradient edgeGradient;
    private float gradientHeight = -1;
    private float opacity = -1;

    DockDecoration(int dockSize, float density) {
        this.dockSize = dockSize;
        this.density = density;
        tint.setColor(0x3014161A);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(0.8f * density);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        boolean any = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockSize) {
                child.setAlpha(1f);
                continue;
            }
            View card = child.findViewById(R.id.card);
            if (card == null) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        // Reserve clearance for the fully enlarged tile and its compact halo.
        // Card scale/translation intentionally do not enter these fixed dock bounds.
        rect.inset(-23 * density, -VERTICAL_PADDING_DP * density);
        // Ancestors can clip a negative-margin child above the Canvas's reported local clip.
        // Use the actual visible viewport, including ancestor clipping and the outline stroke.
        if (!parent.getGlobalVisibleRect(visibleBounds)) return;
        parent.getLocationOnScreen(screenLocation);
        c.getClipBounds(canvasBounds);
        float clipTop = Math.max(canvasBounds.top, visibleBounds.top - screenLocation[1]);
        float visibility = Math.max(0f, Math.min(1f,
                (rect.top - clipTop - density) / (12f * density)));
        // The dock leaves as one surface; its cards must not leave cropped slivers behind the glass.
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);
            if (position >= 0 && position < dockSize) child.setAlpha(visibility);
        }
        if (visibility == 0f) return;
        float radius = 26 * density;
        // Scrolling only translates the cached shaders; focus scaling never resizes the dock.
        if (Math.abs(gradientHeight - rect.height()) > 0.5f || opacity != LauncherOptions.dockOpacity) {
            gradientHeight = rect.height();
            opacity = LauncherOptions.dockOpacity;
            tint.setColor(GlassSurface.tintColor());
            surfaceGradient = new LinearGradient(0, 0, 0, gradientHeight,
                    GlassSurface.surfaceColors(), GlassSurface.SURFACE_STOPS, Shader.TileMode.CLAMP);
            edgeGradient = new LinearGradient(0, 0, 0, gradientHeight,
                    GlassSurface.edgeColors(), GlassSurface.EDGE_STOPS, Shader.TileMode.CLAMP);
            band.setShader(surfaceGradient);
            edge.setShader(edgeGradient);
        }
        shaderPosition.setTranslate(0, rect.top);
        surfaceGradient.setLocalMatrix(shaderPosition);
        edgeGradient.setLocalMatrix(shaderPosition);
        tint.setAlpha((int) (Math.min(255, 48 * opacity) * visibility));
        band.setAlpha((int) (255 * visibility));
        edge.setAlpha((int) (255 * visibility));
        c.drawRoundRect(rect, radius, radius, tint);
        c.drawRoundRect(rect, radius, radius, band);
        c.drawRoundRect(rect, radius, radius, edge);
    }

}
