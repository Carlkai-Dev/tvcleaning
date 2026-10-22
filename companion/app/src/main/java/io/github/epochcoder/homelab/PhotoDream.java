package io.github.epochcoder.homelab;

import android.service.dreams.DreamService;

/**
 * The screensaver: recent Immich photos (see {@link Slideshow}). Select it with
 * build-and-install.sh SCREENSAVER=1, or Settings > Device Preferences > Screen saver where the
 * firmware still shows that page. Any key wakes the projector, as with the stock screensaver.
 */
public class PhotoDream extends DreamService {
    private Slideshow slideshow;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setContentView(R.layout.slideshow);
        slideshow = new Slideshow(getWindow().getDecorView());
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        slideshow.start();
    }

    @Override
    public void onDreamingStopped() {
        slideshow.stop();
        super.onDreamingStopped();
    }
}
