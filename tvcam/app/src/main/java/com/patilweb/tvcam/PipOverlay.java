package com.patilweb.tvcam;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Live PiP on the chosen screen area of whichever TV Cam screen is open.
 * Only shown when PiP preference is ON and a camera is connected.
 */
final class PipOverlay {
    private static View overlay;
    private static ImageView image;
    private static TextView badge;
    private static Activity host;

    private PipOverlay() {}

    static void attach(Activity activity) {
        if (!Prefs.pipActive(activity)) {
            detach(activity);
            return;
        }
        // Don't draw in-app PiP on the position picker (dummy already there)
        // or on Main live view (full preview already fills the screen).
        if (activity instanceof PipPositionActivity || activity instanceof MainActivity) {
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
        applyPlacement(activity, lp);
        decor.addView(overlay, lp);
        host = activity;
    }

    static void applySizeAndPosition(Activity activity) {
        if (overlay == null || !(overlay.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) overlay.getLayoutParams();
        applyPlacement(activity, lp);
        overlay.setLayoutParams(lp);
    }

    private static void applyPlacement(Activity activity, FrameLayout.LayoutParams lp) {
        float density = activity.getResources().getDisplayMetrics().density;
        int bottomReserve = Math.round(96f * density);
        PipLayout.applyFrame(activity, lp, Prefs.pipSizePx(activity),
                Prefs.pipXPct(activity), Prefs.pipYPct(activity), bottomReserve);
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
        if (Prefs.pipActive(a)) {
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
