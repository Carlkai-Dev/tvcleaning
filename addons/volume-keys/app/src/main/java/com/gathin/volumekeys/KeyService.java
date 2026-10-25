package com.gathin.volumekeys;

import android.accessibilityservice.AccessibilityService;
import android.media.AudioManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Sets the media volume when Red, Green, Yellow or Blue is pressed. Every other key passes through untouched. */
public class KeyService extends AccessibilityService {
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int index = Levels.indexOf(event.getKeyCode());
        if (index < 0) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            AudioManager audio = getSystemService(AudioManager.class);
            if (audio != null) audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                    Levels.clamp(Levels.get(this, index), audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)),
                    AudioManager.FLAG_SHOW_UI);
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }
}
