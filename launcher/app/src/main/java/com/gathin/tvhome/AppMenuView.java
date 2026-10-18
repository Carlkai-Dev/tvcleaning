package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Lightweight contextual menu. A separate preview keeps the chosen tile above the dimmed Home. */
final class AppMenuView extends FrameLayout {
    interface Listener {
        void onAction(int action);
        void onDismiss();
    }
    static final int MOVE = 0, UNINSTALL = 1, HIDE = 2;
    private final Listener listener;
    private final TextView[] rows = new TextView[3];
    private final MenuIcon[] icons = new MenuIcon[3];
    private int selected;
    private boolean confirmDown;
    private final Rect anchor;
    private final float density;
    private final FrameLayout previewCard;
    private final LinearLayout panel;
    private boolean openingStarted;

    AppMenuView(Context context, AppAdapter.Entry entry, Rect tile, float initialScale, Listener listener) {
        super(context);
        this.listener = listener;
        anchor = new Rect(tile);
        density = getResources().getDisplayMetrics().density;
        setBackgroundColor(0x99000000);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setOnClickListener(v -> listener.onDismiss());
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

        ImageView preview = new ImageView(context);
        Drawable artwork = entry.banner != null ? entry.banner : entry.icon;
        if (artwork != null && artwork.getConstantState() != null)
            artwork = artwork.getConstantState().newDrawable().mutate();
        preview.setImageDrawable(artwork);
        preview.setScaleType(entry.banner != null ? ImageView.ScaleType.FIT_XY : ImageView.ScaleType.CENTER_INSIDE);
        preview.setContentDescription(entry.label);
        previewCard = new FrameLayout(context);
        previewCard.setBackground(round(0xFF16161A, 16 * density, 0, 0));
        previewCard.setClipToOutline(true);
        previewCard.setElevation(16 * density);
        previewCard.setScaleX(initialScale);
        previewCard.setScaleY(initialScale);
        previewCard.addView(preview, new LayoutParams(-1, -1));
        GlassView previewHighlight = new GlassView(context, null);
        previewCard.addView(previewHighlight, new LayoutParams(-1, -1));
        LayoutParams previewLayout = new LayoutParams(tile.width(), tile.height());
        previewLayout.leftMargin = tile.left;
        previewLayout.topMargin = tile.top;
        addView(previewCard, previewLayout);

        panel = new LinearLayout(context);
        // Never render the panel at FrameLayout's default (0, 0) while waiting for layout.
        panel.setAlpha(0f);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding((int) (7 * density), (int) (5 * density),
                (int) (7 * density), (int) (5 * density));
        panel.setBackground(round(0xF22C2C32, 18 * density, 0x55FFFFFF, density));
        panel.setElevation(24 * density);
        panel.setClickable(true);
        TextView title = new TextView(context);
        title.setText(entry.label);
        title.setTextColor(0xFFBDBDC8);
        title.setTextSize(13);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding((int) (13 * density), 0, (int) (13 * density), 0);
        panel.addView(title, new LinearLayout.LayoutParams(-1, (int) (36 * density)));

        int[] labels = {R.string.app_menu_move, R.string.app_menu_uninstall, R.string.app_menu_hide};
        for (int i = 0; i < rows.length; i++) {
            final int action = i;
            TextView row = new TextView(context);
            row.setText(labels[i]);
            row.setTextSize(18);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) (14 * density), 0, (int) (14 * density), 0);
            icons[i] = new MenuIcon(i, density);
            icons[i].setBounds(0, 0, (int) (22 * density), (int) (22 * density));
            row.setCompoundDrawables(icons[i], null, null, null);
            row.setCompoundDrawablePadding((int) (14 * density));
            row.setOnClickListener(v -> listener.onAction(action));
            rows[i] = row;
            panel.addView(row, new LinearLayout.LayoutParams(-1, (int) (48 * density)));
        }
        TextView footer = new TextView(context);
        footer.setText(R.string.app_menu_close_hint);
        footer.setTextColor(0xFFBDBDC8);
        footer.setTextSize(11);
        footer.setGravity(Gravity.CENTER);
        panel.addView(footer, new LinearLayout.LayoutParams(-1, (int) (30 * density)));
        int panelWidth = (int) (250 * density);
        LayoutParams panelLayout = new LayoutParams(panelWidth, -2);
        addView(panel, panelLayout);
        updateSelection();

    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // Position synchronously using measured dimensions, before this frame can be drawn.
        // No posted callback or second layout is needed to move the panel from the origin.
        int panelWidth = panel.getMeasuredWidth(), panelHeight = panel.getMeasuredHeight();
        int gap = (int) (28 * density), margin = (int) (24 * density);
        int x = anchor.right + gap;
        if (x + panelWidth > getWidth() - margin) x = anchor.left - gap - panelWidth;
        x = Math.max(margin, Math.min(x, getWidth() - panelWidth - margin));
        int y = Math.max(margin, Math.min(anchor.centerY() - panelHeight / 2,
                getHeight() - panelHeight - margin));
        panel.layout(x, y, x + panelWidth, y + panelHeight);
        if (!openingStarted) {
            openingStarted = true;
            previewCard.animate().scaleX(AppAdapter.MENU_SCALE).scaleY(AppAdapter.MENU_SCALE)
                    .setDuration(220).start();
            panel.setTranslationY(8 * density);
            panel.animate().alpha(1f).translationY(0).setDuration(180).start();
        }
    }

    private void updateSelection() {
        for (int i = 0; i < rows.length; i++) {
            boolean focused = i == selected;
            int color = i == UNINSTALL ? (focused ? 0xFFBD1835 : 0xFFFF637B)
                    : (focused ? 0xFF18181C : Color.WHITE);
            rows[i].setTextColor(color);
            rows[i].setBackground(round(focused ? 0xFFF3F3F7 : Color.TRANSPARENT, 12 * getResources()
                    .getDisplayMetrics().density, 0, 0));
            icons[i].color = color;
            icons[i].invalidateSelf();
            rows[i].setSelected(focused);
        }
        setContentDescription(rows[selected].getText());
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) listener.onDismiss();
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) confirmDown = true;
            // Ignore the key-up from the long press that opened this menu.
            if (event.getAction() == KeyEvent.ACTION_UP && confirmDown) {
                confirmDown = false;
                listener.onAction(selected);
            }
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                selected = Math.max(0, Math.min(2, selected + (key == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1)));
                updateSelection();
            }
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
        return super.dispatchKeyEvent(event);
    }

    private static GradientDrawable round(int color, float radius, int stroke, float width) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        if (width > 0) background.setStroke(Math.max(1, (int) width), stroke);
        return background;
    }

    private static final class MenuIcon extends Drawable {
        final int type;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int color = Color.WHITE;
        MenuIcon(int type, float density) { this.type = type; }
        @Override public void draw(Canvas canvas) {
            canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.8f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            if (type == MOVE) {
                canvas.drawLine(12, 3, 12, 21, paint);
                canvas.drawLine(3, 12, 21, 12, paint);
                Path arrows = new Path();
                arrows.moveTo(9, 6); arrows.lineTo(12, 3); arrows.lineTo(15, 6);
                arrows.moveTo(9, 18); arrows.lineTo(12, 21); arrows.lineTo(15, 18);
                arrows.moveTo(6, 9); arrows.lineTo(3, 12); arrows.lineTo(6, 15);
                arrows.moveTo(18, 9); arrows.lineTo(21, 12); arrows.lineTo(18, 15);
                canvas.drawPath(arrows, paint);
            } else if (type == UNINSTALL) {
                canvas.drawRoundRect(6, 7, 18, 21, 2, 2, paint);
                canvas.drawLine(4, 5, 20, 5, paint);
                canvas.drawLine(9, 3, 15, 3, paint);
                canvas.drawLine(10, 10, 10, 17, paint);
                canvas.drawLine(14, 10, 14, 17, paint);
            } else {
                Path eye = new Path();
                eye.moveTo(2, 12); eye.cubicTo(7, 4, 17, 4, 22, 12);
                eye.cubicTo(17, 20, 7, 20, 2, 12);
                canvas.drawPath(eye, paint);
                canvas.drawCircle(12, 12, 3, paint);
                canvas.drawLine(3, 3, 21, 21, paint);
            }
            canvas.restore();
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
