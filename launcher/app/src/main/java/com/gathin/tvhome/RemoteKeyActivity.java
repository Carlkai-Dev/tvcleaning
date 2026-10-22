package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.Toast;

/**
 * Remote buttons the TV itself answers, handed over with tools/tcl-keys.sh. TCL Google TV can start an activity
 * for a remote button from its own key settings (CustomKeyList and CustomKey_<keycode> in Settings.Global), in
 * any app and with no key-filtering accessibility service bound, so a held volume key keeps ramping (#763). The
 * TV starts this activity with the button's keycode as the button goes down, and it does what HomeKeyService and
 * InputKeyService would: the Settings button (settings_key_keycodes), an app_keys entry or an input_key_keycodes
 * button. "hold:" entries are left out, since the TV starts it before anyone knows how long the button is held.
 * Not exported: the system starts it all the same.
 */
public class RemoteKeyActivity extends Activity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        answer(getIntent().getIntExtra("keycode", 0));
        finish();   // Theme.NoDisplay: done before onResume
    }

    private void answer(int keyCode) {
        Config config = Config.load(this);
        if (keyCode == KeyEvent.KEYCODE_SETTINGS || contains(config.settingsKeycodes, keyCode)) {
            // Coming in front closed a panel that was up, so this press is the one that closes it.
            if (QuickSettingsActivity.isOpen()) return;
            start(config.settingsKeyPanel ? new Intent(this, QuickSettingsActivity.class)
                    : SystemSettings.intent(this, Settings.ACTION_SETTINGS));
            return;
        }
        String app = RemoteKeys.parseAppKeys(config.appKeys).get(keyCode);
        if (app != null) {
            RemoteKeys.launch(this, app);
        } else if (contains(config.inputKeycodes, keyCode)) {
            start(new Intent(InputKeyService.ACTION_VIEW_INPUTS));
        }
    }

    private void start(Intent intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private static boolean contains(int[] codes, int keyCode) {
        for (int code : codes) if (code == keyCode) return true;
        return false;
    }
}
