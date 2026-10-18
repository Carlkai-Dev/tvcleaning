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

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF inset = new RectF();
    private final Matrix shaderPosition = new Matrix();
    private LinearGradient surfaceGradient;
    private LinearGradient edgeGradient;
    private float gradientHeight = -1;

    DockDecoration(int dockSize, float density) {
        this.dockSize = dockSize;
        this.density = density;
        tint.setColor(0x3014161A);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(0.8f * density);
        innerEdge.setStyle(Paint.Style.STROKE);
        innerEdge.setStrokeWidth(0.6f * density);
        innerEdge.setColor(0x12FFFFFF);
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
            if (pos < 0 || pos >= dockSize) continue;
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
        float pad = 13 * density;
        rect.inset(-pad, -pad);
        float radius = 24 * density;
        // Scrolling only translates the cached shaders; focus scaling never resizes the dock.
        if (Math.abs(gradientHeight - rect.height()) > 0.5f) {
            gradientHeight = rect.height();
            surfaceGradient = new LinearGradient(0, 0, 0, gradientHeight,
                    new int[]{0x48FFFFFF, 0x2AFFFFFF, 0x1CFFFFFF, 0x27FFFFFF},
                    new float[]{0f, 0.34f, 0.78f, 1f}, Shader.TileMode.CLAMP);
            edgeGradient = new LinearGradient(0, 0, 0, gradientHeight,
                    new int[]{0x88FFFFFF, 0x24FFFFFF, 0x50FFFFFF},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
            band.setShader(surfaceGradient);
            edge.setShader(edgeGradient);
        }
        shaderPosition.setTranslate(0, rect.top);
        surfaceGradient.setLocalMatrix(shaderPosition);
        edgeGradient.setLocalMatrix(shaderPosition);
        c.drawRoundRect(rect, radius, radius, tint);
        c.drawRoundRect(rect, radius, radius, band);
        c.drawRoundRect(rect, radius, radius, edge);
        inset.set(rect);
        inset.inset(1.5f * density, 1.5f * density);
        c.drawRoundRect(inset, radius - 1.5f * density, radius - 1.5f * density, innerEdge);
    }
}
