package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Enhanced tvOS / Google TV style dock decoration:
 * - Draws the translucent sapphire frosted glass band behind the pinned dock row.
 * - Draws clean section headers: "★ FAVORITES" above the dock, and "✦ ALL APPLICATIONS" above the grid.
 */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint allAppsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    DockDecoration(int dockSize, float density) {
        this.dockSize = dockSize;
        this.density = density;

        band.setColor(0xCC09172A); // Translucent Sapphire Midnight Navy Glass
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(1.2f * density);
        edge.setColor(0x2E38BDF8); // Subtle Cyan-Sapphire luminous rim

        titlePaint.setColor(0xCC38BDF8); // Electric Cyan
        titlePaint.setTextSize(11f * density);
        titlePaint.setLetterSpacing(0.14f);

        allAppsPaint.setColor(0x9994A3B8); // Slate
        allAppsPaint.setTextSize(11f * density);
        allAppsPaint.setLetterSpacing(0.14f);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (20 * density);  // room for "★ FAVORITES" title
            outRect.bottom = (int) (12 * density);
        } else if (pos >= dockSize && pos < dockSize + dockSize) {
            outRect.top = (int) (24 * density);  // room for "✦ ALL APPLICATIONS" title
            outRect.bottom = (int) (6 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        boolean any = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockSize) continue;
            RecyclerView.ViewHolder vh = parent.getChildViewHolder(child);
            View card = vh instanceof AppAdapter.Holder ? ((AppAdapter.Holder) vh).card : child.findViewById(R.id.card);
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
        if (any) {
            float pad = 12 * density;
            rect.inset(-pad, -pad);
            float radius = 24 * density;
            c.drawRoundRect(rect, radius, radius, band);
            c.drawRoundRect(rect, radius, radius, edge);
        }

        // Draw Section Titles
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos == 0) {
                View card = child.findViewById(R.id.card);
                float textX = child.getLeft() + (card != null ? card.getLeft() : 0);
                float textY = child.getTop() + child.getTranslationY() - (6 * density);
                c.drawText("★ FAVORITES", textX, textY, titlePaint);
            } else if (pos == dockSize) {
                View card = child.findViewById(R.id.card);
                float textX = child.getLeft() + (card != null ? card.getLeft() : 0);
                float textY = child.getTop() + child.getTranslationY() - (8 * density);
                c.drawText("✦ ALL APPLICATIONS", textX, textY, allAppsPaint);
            }
        }
    }
}
