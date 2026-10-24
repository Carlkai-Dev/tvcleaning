package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Draws the tvOS style lighter band behind the first (dock) row and adds a gap below it. */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    /** Extra band below the tiles for their names (always_show_names), in px. */
    private final float namesRoom;
    /** Light glass like the top bar's pills (config.xml dock_glass): a white tint that fades downwards. */
    private final boolean lightGlass;
    /** Padding all round the tiles equal to the gap between them (config.xml dock_even_padding). */
    private final boolean evenPadding;
    /** The band's top and bottom the glass gradient was made for; it is remade when they move. */
    private float shaderTop = Float.NaN, shaderBottom = Float.NaN;
    /** Room at the band's top for the "Apps" title and the "1-6 / 12" counter; 0 without a header. */
    private final float headerRoom;
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint counter = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String titleText;

    /** The same band with a header: {@code titleText} at its top left, the focused row's range at its top right. */
    DockDecoration(int dockSize, float density, boolean translucent, boolean names, int opacity, boolean lightGlass,
                   boolean evenPadding, String titleText, Typeface typeface) {
        this(dockSize, density, translucent, names, opacity, lightGlass, evenPadding, titleText, typeface, true);
    }

    /**
     * @param translucent the cinematic shelf's band: the artwork and the glow show through it.
     * @param names       the dock tiles show their names, so the band reaches down over them.
     * @param opacity     the plain band's opacity in percent (dock_opacity), so a wallpaper can show through it.
     * @param lightGlass  a light frosted band like the pills, for a sky or wallpaper behind it.
     * @param evenPadding the band's padding is the gap between the tiles on all four sides (dock_even_padding).
     */
    DockDecoration(int dockSize, float density, boolean translucent, boolean names, int opacity, boolean lightGlass,
                   boolean evenPadding) {
        this(dockSize, density, translucent, names, opacity, lightGlass, evenPadding, null, null, false);
    }

    private DockDecoration(int dockSize, float density, boolean translucent, boolean names, int opacity,
                           boolean lightGlass, boolean evenPadding, String titleText, Typeface typeface,
                           boolean header) {
        this.titleText = titleText;
        this.headerRoom = header ? 28 * density : 0;
        title.setTypeface(typeface);
        title.setTextSize(16 * density);
        title.setColor(0xD9FFFFFF);
        counter.setTypeface(typeface);
        counter.setTextSize(14 * density);
        counter.setColor(0x99FFFFFF);
        counter.setTextAlign(Paint.Align.RIGHT);
        this.dockSize = dockSize;
        this.density = density;
        this.namesRoom = names ? 22 * density : 0;
        this.lightGlass = lightGlass;
        this.evenPadding = evenPadding;
        band.setColor(translucent ? 0x8C121014 : (Math.round(opacity * 2.55f) << 24) | 0x141416);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        edge.setColor(lightGlass ? 0x38FFFFFF : translucent ? 0x26FFFFFF : 0x1FFFFFFF);
    }

    /** Concentric corners: the band's radius is the tiles' radius plus the band's padding around them. */
    static float radiusPx(float tileRadiusPx, float paddingPx) {
        return tileRadiusPx + paddingPx;
    }

    /**
     * The band's padding on every side for dock_even_padding: the gap between two dock tiles (a cell minus its tile, as
     * AppAdapter.tileWidthPx lays them out), capped by the room the grid leaves at its edges.
     */
    static float evenPaddingPx(int innerWidthPx, int columns, int tileWidthPx, float roomPx) {
        float gap = columns > 0 ? innerWidthPx / (float) columns - tileWidthPx : 0f;
        return Math.max(0f, Math.min(gap, roomPx));
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density + headerRoom);
            outRect.bottom = (int) (12 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        boolean any = false;
        int cardWidth = 0;
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
            cardWidth = card.getWidth();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        float pad = 14 * density;
        // The band can only draw inside the grid, and the first and last dock tiles sit 12dp in from its
        // edges: a full 14dp at the sides sliced the rounded ends flat (#454). Keep the sides, with the
        // 1dp edge line, inside the grid; the top and bottom have room.
        // 22dp at the sides (the grid's 10dp padding makes room): a focused first or last tile grows about 7dp
        // outwards and still keeps about the band's top margin from its rounded end.
        float sidePad = 22 * density;
        float side = Math.max(0f, Math.min(sidePad, Math.min(rect.left, parent.getWidth() - rect.right) - density));
        float radius = AppAdapter.cornerRadius(cardWidth, density);
        if (evenPadding) {
            // The same padding on all four sides, so the band's corners are concentric with the tiles' everywhere.
            int inner = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
            float room = Math.min(rect.top, Math.min(rect.left, parent.getWidth() - rect.right)) - density;
            float even = evenPaddingPx(inner, dockSize, cardWidth, room);
            rect.inset(-even, -even);
            radius = radiusPx(radius, even);
        } else {
            // dock_even_padding off: the band as before, with its fixed 26 dp corners.
            rect.inset(-side, -pad);
            radius = 26 * density;
        }
        rect.bottom += namesRoom;
        rect.top -= headerRoom;
        if (lightGlass && (rect.top != shaderTop || rect.bottom != shaderBottom)) {
            // The pills' glass (pill_bg: white, 18% fading to 8%), a little fainter over this much larger area.
            band.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, 0x24FFFFFF, 0x0DFFFFFF,
                    Shader.TileMode.CLAMP));
            shaderTop = rect.top;
            shaderBottom = rect.bottom;
        }
        c.drawRoundRect(rect, radius, radius, band);
        c.drawRoundRect(rect, radius, radius, edge);
        if (headerRoom > 0) {
            float baseline = rect.top + 26 * density;
            c.drawText(titleText, rect.left + 22 * density, baseline, title);
            c.drawText(rowRange(parent), rect.right - 22 * density, baseline, counter);
        }
    }

    /** "1-6 / 12": the focused row's tiles out of all of them (the dock's row when nothing is focused). */
    private String rowRange(RecyclerView parent) {
        RecyclerView.Adapter<?> a = parent.getAdapter();
        int total = a instanceof AppAdapter ? ((AppAdapter) a).appCount() : a == null ? 0 : a.getItemCount();
        View f = parent.getFocusedChild();
        int pos = f == null ? 0 : Math.max(0, parent.getChildAdapterPosition(f));
        return rowRange(pos, dockSize, total);
    }

    static String rowRange(int focused, int columns, int total) {
        int first = Math.min(focused, Math.max(0, total - 1)) / columns * columns;
        return (first + 1) + "-" + Math.min(first + columns, total) + " / " + total;
    }
}
