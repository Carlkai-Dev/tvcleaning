package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import androidx.recyclerview.widget.RecyclerView;

/** Clip the whole scrolling surface, while allowing enlarged cards outside their individual cells. */
public final class ViewportGrid extends RecyclerView {
    private final Rect viewport = new Rect();

    public ViewportGrid(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        viewport.set(0, 0, width, height);
        setClipBounds(viewport);
    }
}
