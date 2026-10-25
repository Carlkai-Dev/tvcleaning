package com.gathin.volumekeys;

import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The tile and module card open this: the volume each colour button sets. Left and Right on a row change its
 * level (hold to run), OK sets the volume to it now so it can be judged by ear.
 */
public class MainActivity extends Activity {
    private final Row[] rows = new Row[Levels.KEYS.length];
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFF0D1117);
        page.setPadding(Math.round(48 * dp), Math.round(32 * dp), Math.round(48 * dp), Math.round(24 * dp));
        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(28);
        title.setTextColor(0xFFFFFFFF);
        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(0xFF8B949E);
        status.setPadding(0, 0, 0, Math.round(16 * dp));
        page.addView(title);
        page.addView(status);
        for (int i = 0; i < rows.length; i++) {
            final int index = i;
            rows[i] = new Row(this, Levels.NAMES[i] + " button", "Left / Right to change, OK to hear it.", () -> apply(index));
            page.addView(rows[i], new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            show(i);
        }
        setContentView(page);
        rows[0].requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        status.setText(serviceOn() ? "On: the colour buttons set the volume."
                : "Off: could not switch the key service on. Run install.sh once, or turn it on in Settings > Accessibility.");
    }

    private void show(int i) {
        rows[i].setValue("◄  " + Levels.get(this, i) + "  ►");
    }

    private void apply(int i) {
        AudioManager audio = getSystemService(AudioManager.class);
        if (audio != null) audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                Levels.clamp(Levels.get(this, i), audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)),
                AudioManager.FLAG_SHOW_UI);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int dir = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 0;
        for (int i = 0; dir != 0 && i < rows.length; i++) {
            if (!rows[i].isFocused()) continue;
            Levels.set(this, i, Levels.get(this, i) + dir);
            show(i);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** Switches the key service on when it is off and the permission was granted; true when it is on. */
    private boolean serviceOn() {
        String me = new ComponentName(this, KeyService.class).flattenToString();
        String list = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (list != null && containsService(list, me)) return true;
        if (checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        Settings.Secure.putString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                list == null || list.isEmpty() ? me : list + ":" + me);
        Settings.Secure.putInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        return true;
    }

    static boolean containsService(String list, String service) {
        for (String s : list.split(":")) if (s.equals(service)) return true;
        return false;
    }
}
