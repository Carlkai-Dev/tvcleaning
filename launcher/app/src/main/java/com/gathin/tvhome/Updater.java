package com.gathin.tvhome;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Self-update from the latest GitHub release of config.xml update_repo. A release's tag ends in the
 * versionCode it was built with (launcher-1001, .github/workflows/release.yml) and carries the APK.
 * Android itself refuses an APK signed with another key, so only the repo owner's builds install.
 */
final class Updater {
    private static final String ACTION = "com.gathin.tvhome.UPDATE_STATUS";
    private static boolean running;

    private Updater() {
    }

    /** Trailing digits of a release tag, or -1. */
    static long versionOf(String tag) {
        int i = tag.length();
        while (i > 0 && Character.isDigit(tag.charAt(i - 1))) i--;
        return i == tag.length() || tag.length() - i > 18 ? -1 : Long.parseLong(tag.substring(i));
    }

    static void check(Activity activity, String repo) {
        if (running) return;
        running = true;
        Context app = activity.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        toast(app, app.getString(R.string.update_checking));
        new Thread(() -> {
            String message;
            try {
                JSONObject release = new JSONObject(new String(get(
                        "https://api.github.com/repos/" + repo + "/releases/latest"), "UTF-8"));
                String tag = release.getString("tag_name");
                PackageInfo self = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
                long available = versionOf(tag);
                String apk = null;
                JSONArray assets = release.getJSONArray("assets");
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    if (asset.getString("name").endsWith(".apk")) apk = asset.getString("browser_download_url");
                }
                if (available <= self.versionCode || apk == null) {
                    message = app.getString(R.string.update_none, self.versionName + " (" + self.versionCode + ")");
                } else {
                    main.post(() -> toast(app, app.getString(R.string.update_downloading, tag)));
                    install(app, get(apk));
                    message = null;
                }
            } catch (Exception e) {
                message = app.getString(R.string.update_failed, String.valueOf(e.getMessage()));
            }
            final String done = message;
            main.post(() -> {
                if (done != null) {
                    running = false;
                    toast(app, done);
                }
            });
        }).start();
    }

    private static byte[] get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode() + " " + url);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[65536];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    /** Hands the APK to the system installer; it asks the person to confirm, then restarts Home. */
    private static void install(Context app, byte[] apk) throws Exception {
        PackageInstaller installer = app.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(app.getPackageName());
        int id = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(id)) {
            try (OutputStream out = session.openWrite("update.apk", 0, apk.length)) {
                out.write(apk);
                session.fsync(out);
            }
            BroadcastReceiver status = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    int code = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                    if (code == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                        if (confirm != null) context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        return;
                    }
                    context.unregisterReceiver(this);
                    running = false;
                    // On success Android kills this process before the broadcast, so this is a failure or a cancel.
                    toast(context, context.getString(R.string.update_failed,
                            String.valueOf(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))));
                }
            };
            // The flag is ignored before Android 13; the broadcast comes only from our own PendingIntent.
            app.registerReceiver(status, new IntentFilter(ACTION), Context.RECEIVER_NOT_EXPORTED);
            // Mutable: the installer adds the status extras to it.
            int flags = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
            session.commit(PendingIntent.getBroadcast(app, id,
                    new Intent(ACTION).setPackage(app.getPackageName()), flags).getIntentSender());
        }
    }

    private static void toast(Context context, String text) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show();
    }
}
