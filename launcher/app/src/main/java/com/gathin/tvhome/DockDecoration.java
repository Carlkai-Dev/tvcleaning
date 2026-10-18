package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Draws the tvOS style lighter band behind the first (dock) row, and the "Recent" heading above the Recent row. */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint heading = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final AppAdapter adapter;
    private final String recentLabel;

    DockDecoration(int dockSize, float density, AppAdapter adapter, String recentLabel, Typeface font, float scaledDensity) {
        this.dockSize = dockSize;
        this.density = density;
        this.adapter = adapter;
        this.recentLabel = recentLabel.toUpperCase();
        heading.setColor(0x8CFFFFFF);
        heading.setTypeface(font);
        heading.setTextSize(12 * scaledDensity);
        heading.setLetterSpacing(0.22f);
        band.setColor(0xFF141416);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        edge.setColor(0x1FFFFFFF);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        } else if (pos >= 0 && adapter.isRecent(pos)) {
            outRect.top = (int) (30 * density);   // room for the heading
            outRect.bottom = (int) (10 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int recentStart = adapter.recentStart();
        if (recentStart >= 0) {
            RecyclerView.ViewHolder vh = parent.findViewHolderForAdapterPosition(recentStart);
            View card = vh != null ? vh.itemView.findViewById(R.id.card) : null;
            if (card != null) {
                float x = vh.itemView.getLeft() + card.getLeft() + 4 * density;
                float y = vh.itemView.getTop() + vh.itemView.getTranslationY() - 12 * density;
                c.drawText(recentLabel, x, y, heading);
            }
        }
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
        float pad = 14 * density;
        rect.inset(-pad, -pad);
        float radius = 26 * density;
        c.drawRoundRect(rect, radius, radius, band);
        c.drawRoundRect(rect, radius, radius, edge);
    }
}
