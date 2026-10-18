package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;

/** A bounded, scrollable glass list. No blur, bitmap or idle animation. */
final class HeaderDropdown {
    final View panel;
    final LinearLayout rows;
    final PopupWindow window;
    private final View anchor;
    private final float density;

    HeaderDropdown(Activity activity, ViewGroup root, View anchor) {
        this.anchor = anchor;
        density = activity.getResources().getDisplayMetrics().density;
        panel = activity.getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        panel.setBackground(new GlassSurface(activity, 24, false));
        rows = panel.findViewById(R.id.rows);
        // A focused row must settle fully inside the viewport, including its rounded selection.
        ((ScrollView) panel.findViewById(R.id.popupScroll)).setSmoothScrollingEnabled(false);
        window = new PopupWindow(panel, (int) (256 * density), -2, true);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setOutsideTouchable(true);
        window.setAnimationStyle(0);
        window.setElevation(0);
        window.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        window.setOnDismissListener(() -> anchor.requestFocus());
    }

    void refreshAppearance() { panel.invalidate(); }

    void show() {
        int width = window.getWidth();
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((int) (216 * density), View.MeasureSpec.AT_MOST));
        window.setHeight(panel.getMeasuredHeight());
        // Resolve the anchor before revealing any pixels, including the opening frame.
        panel.setAlpha(0);
        panel.setTranslationY(-4 * density);
        window.showAsDropDown(anchor, 0, (int) (10 * density), Gravity.END);
        panel.animate().alpha(1).translationY(0).setDuration(LauncherOptions.duration(160)).start();
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            if (row.isFocusable()) {
                row.requestFocus();
                break;
            }
        }
    }

    static void containNavigation(View row) {
        row.setOnKeyListener((v, code, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            return code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
        });
    }
}
