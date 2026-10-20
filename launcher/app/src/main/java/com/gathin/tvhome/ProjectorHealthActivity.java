package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.widget.TextView;

import com.gathin.tvhome.ProjectorCapabilities.Feature;

/**
 * Projector Health: read once when the screen opens and
 * on explicit Refresh. No background monitoring; thermal status is the OS thermal status,
 * never presented as optical-engine temperature.
 */
public final class ProjectorHealthActivity extends Activity {

    private TextView text;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_projector_health);
        text = findViewById(R.id.healthText);
        findViewById(R.id.healthRefresh).setOnClickListener(v -> refresh());
        refresh();
        findViewById(R.id.healthRefresh).requestFocus();
    }

    private void refresh() {
        ActivityManager am = getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        long availMb = mi.availMem / (1024 * 1024);
        long totalMb = mi.totalMem / (1024 * 1024);
        long freeGbTenths = fs.getAvailableBytes() * 10 / (1024L * 1024 * 1024);
        String thermal = getString(R.string.projector_state_unknown);
        if (Build.VERSION.SDK_INT >= 30) {
            PowerManager pm = getSystemService(PowerManager.class);
            if (pm != null) thermal = String.valueOf(pm.getCurrentThermalStatus());
        }
        ProjectorCapabilities caps = ProjectorDetector.detect(this);
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.projector_device, Build.MANUFACTURER, Build.MODEL, Build.DEVICE)).append('\n');
        sb.append("Android ").append(Build.VERSION.RELEASE).append(" · API ").append(Build.VERSION.SDK_INT).append('\n');
        sb.append(Build.ID).append('\n');
        sb.append(getString(R.string.projector_health_memory, availMb, totalMb)).append('\n');
        sb.append(getString(R.string.projector_health_storage, freeGbTenths / 10 + "." + freeGbTenths % 10)).append('\n');
        sb.append(getString(R.string.projector_health_thermal, thermal)).append('\n');
        sb.append(getString(R.string.projector_health_profile, ProjectorDetector.moduleProfile(this))).append('\n');
        for (Feature f : Feature.values()) {
            sb.append(f.name()).append(": ").append(caps.state(f).name()).append('\n');
        }
        text.setText(sb.toString().trim());
    }
}
