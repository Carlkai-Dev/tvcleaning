package com.patilweb.tvcam;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Live PiP on a chosen screen corner/edge of whichever TV Cam screen is open.
 */
final class PipOverlay {
    private static View overlay;
    private static ImageView image;
    private static TextView badge;
    private static Activity host;

    private PipOverlay() {}

    static void attach(Activity activity) {
        if (!Prefs.pipOverview(activity)) {
            detach(activity);
            return;
        }
        if (host == activity && overlay != null && overlay.getParent() != null) {
            applySizeAndPosition(activity);
            return;
        }
        detach(host);

        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        overlay = LayoutInflater.from(activity).inflate(R.layout.pip_overlay, decor, false);
        image = overlay.findViewById(R.id.pipImage);
        badge = overlay.findViewById(R.id.pipBadge);

        int size = Prefs.pipSizePx(activity);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
        applyGravityMargins(activity, lp, Prefs.pipPosition(activity));
        decor.addView(overlay, lp);
        host = activity;
    }

    static void applySizeAndPosition(Activity activity) {
        if (overlay == null || !(overlay.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
            return;
        }
        int size = Prefs.pipSizePx(activity);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) overlay.getLayoutParams();
        lp.width = size;
        lp.height = size;
        applyGravityMargins(activity, lp, Prefs.pipPosition(activity));
        overlay.setLayoutParams(lp);
    }

    private static void applyGravityMargins(Activity activity, FrameLayout.LayoutParams lp,
                                            int position) {
        int gravity = Prefs.pipGravity(position);
        lp.gravity = gravity;
        float density = activity.getResources().getDisplayMetrics().density;
        int margin = Math.round(24f * density);
        int bottomExtra = Math.round(72f * density);
        int left = 0, top = 0, right = 0, bottom = 0;
        if ((gravity & Gravity.START) == Gravity.START
                || (gravity & Gravity.LEFT) == Gravity.LEFT) {
            left = margin;
        }
        if ((gravity & Gravity.END) == Gravity.END
                || (gravity & Gravity.RIGHT) == Gravity.RIGHT) {
            right = margin;
        }
        if ((gravity & Gravity.TOP) == Gravity.TOP) {
            top = margin;
        }
        if ((gravity & Gravity.BOTTOM) == Gravity.BOTTOM) {
            bottom = margin + bottomExtra;
        }
        if ((gravity & Gravity.CENTER_HORIZONTAL) == Gravity.CENTER_HORIZONTAL
                && gravity != Gravity.CENTER) {
            left = 0;
            right = 0;
        }
        if ((gravity & Gravity.CENTER_VERTICAL) == Gravity.CENTER_VERTICAL
                && gravity != Gravity.CENTER) {
            top = 0;
            bottom = 0;
        }
        if (gravity == Gravity.CENTER) {
            left = top = right = bottom = 0;
        }
        lp.setMargins(left, top, right, bottom);
    }

    static void detach(Activity activity) {
        if (overlay == null) return;
        if (activity != null && host != null && activity != host) return;
        ViewGroup parent = overlay.getParent() instanceof ViewGroup
                ? (ViewGroup) overlay.getParent() : null;
        if (parent != null) parent.removeView(overlay);
        overlay = null;
        image = null;
        badge = null;
        host = null;
    }

    static void refreshVisibility() {
        Activity a = TvCamApp.get() != null ? TvCamApp.get().currentActivity() : null;
        if (a == null) return;
        if (Prefs.pipOverview(a)) {
            attach(a);
        } else {
            detach(a);
        }
    }

    static void showFrame(Bitmap frame) {
        if (image == null || frame == null || frame.isRecycled()) return;
        int size = host != null ? Prefs.pipSizePx(host) : 200;
        Bitmap scaled = Bitmap.createScaledBitmap(frame, size, size, true);
        Drawable prev = image.getDrawable();
        image.setImageBitmap(scaled);
        if (prev instanceof BitmapDrawable) {
            Bitmap old = ((BitmapDrawable) prev).getBitmap();
            if (old != null && old != scaled && !old.isRecycled()) old.recycle();
        }
    }

    static void setBadge(String text, int color) {
        if (badge == null) return;
        badge.setText(text);
        badge.setTextColor(color);
        if (host != null) {
            float sp = Prefs.pipSizeDp(host) >= 280 ? 14f : 11f;
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        }
    }
}
