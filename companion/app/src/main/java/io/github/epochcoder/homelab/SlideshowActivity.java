package io.github.epochcoder.homelab;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;

/** The screensaver's slideshow as an activity, to try a config.json right after pushing it. Back leaves. */
public class SlideshowActivity extends Activity {
    private Slideshow slideshow;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.slideshow);
        slideshow = new Slideshow(getWindow().getDecorView());
    }

    @Override
    protected void onStart() {
        super.onStart();
        slideshow.start();
    }

    @Override
    protected void onStop() {
        slideshow.stop();
        super.onStop();
    }
}
