package com.gathin.tvhome;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

/** Emulator-only route checks; restores the theme without opening external settings or changing inventory. */
public final class SettingsRoutingSmoke extends Instrumentation {
    private int checks;

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        Context context = getTargetContext();
        File file = new File(context.getExternalFilesDir(null), Config.FILE_NAME);
        byte[] original = null;
        boolean existed = file.exists();
        boolean read = false;
        try {
            if (!android.os.Build.FINGERPRINT.contains("generic")
                    && !android.os.Build.MODEL.contains("Emulator")
                    && !android.os.Build.PRODUCT.contains("sdk"))
                throw new IllegalStateException("Settings routing checks must run on an emulator");
            if (existed) original = Files.readAllBytes(file.toPath());
            read = true;
            Files.write(file.toPath(), "{}".getBytes(StandardCharsets.UTF_8));
            Map<String, String> defaults = Config.load(context).settingsRoutes;
            check(defaults.size() == 11, "All firmware route keys are loaded");
            for (Map.Entry<String, String> entry : defaults.entrySet()) {
                int id = context.getResources().getIdentifier(entry.getKey(), "string", context.getPackageName());
                check(entry.getValue().equals(context.getString(id)), "Defaults match config.xml: " + entry.getKey());
            }
            JSONObject overlay = new JSONObject();
            for (String key : defaults.keySet()) overlay.put(key, " override ");
            Files.write(file.toPath(), overlay.toString().getBytes(StandardCharsets.UTF_8));
            for (String value : Config.load(context).settingsRoutes.values())
                check("override".equals(value), "Theme overrides are loaded and trimmed");
            overlay.put("settings_package", 42);
            overlay.put("quick_audio_output_component", "");
            Files.write(file.toPath(), overlay.toString().getBytes(StandardCharsets.UTF_8));
            Map<String, String> loaded = Config.load(context).settingsRoutes;
            check(defaults.get("settings_package").equals(loaded.get("settings_package")), "Wrong types keep the default");
            check(loaded.get("quick_audio_output_component").isEmpty(), "Empty override disables a route");
            Files.write(file.toPath(), "{broken".getBytes(StandardCharsets.UTF_8));
            check(defaults.equals(Config.load(context).settingsRoutes), "Malformed JSON keeps built-in routes");

            Map<String, String> routes = new HashMap<>();
            ComponentName own = new ComponentName(context, MainActivity.class);
            routes.put("quick_audio_output_component", own.flattenToShortString());
            check(own.equals(QuickSettingsActions.intent(context, R.id.quickAudioOutput, routes).getComponent()),
                    "An exported theme component can replace the audio-output destination");
            routes.put("quick_apps_component", own.flattenToShortString());
            check(own.equals(QuickSettingsActions.intent(context, R.id.quickApps, routes).getComponent()),
                    "An exported theme component can replace the Apps destination");
            routes.put("quick_screensaver_action", Settings.ACTION_SETTINGS);
            check(Settings.ACTION_SETTINGS.equals(QuickSettingsActions.intent(context, R.id.quickScreensaver, routes).getAction()),
                    "A configured supported screensaver action takes precedence");

            for (String unavailable : new String[]{"", "malformed", "com.gathin.missing/.Absent",
                    new ComponentName(context, HomeSettingsActivity.class).flattenToString()}) {
                routes.put("quick_audio_output_component", unavailable);
                Intent target = QuickSettingsActions.intent(context, R.id.quickAudioOutput, routes);
                check(target.getComponent() == null, "Missing or non-exported component falls back: " + unavailable);
                check(Settings.ACTION_SOUND_SETTINGS.equals(target.getAction()) || Settings.ACTION_SETTINGS.equals(target.getAction()),
                        "Audio output falls back to public sound/general settings");
            }
            routes.clear();
            routes.put("settings_package", context.getPackageName());
            routes.put("quick_audio_output_slice_resource", "settings_fallback_component");
            routes.put("settings_slice_action", Settings.ACTION_SETTINGS);
            routes.put("settings_slice_uri_extra", "test_extra");
            check(QuickSettingsActions.intent(context, R.id.quickAudioOutput, routes).getExtras() == null,
                    "Non-content slice values are rejected");
            routes.put("quick_audio_output_slice_resource", "missing_resource");
            check(QuickSettingsActions.intent(context, R.id.quickAudioOutput, routes).getExtras() == null,
                    "Missing slice resources fall back without extras");
            routes.put("settings_package", "com.gathin.missing");
            check(QuickSettingsActions.intent(context, R.id.quickAudioOutput, routes).getExtras() == null,
                    "Missing slice packages fall back safely");

            routes.clear();
            routes.put("settings_fallback_component", own.flattenToString());
            check(Settings.ACTION_WIFI_SETTINGS.equals(QuickSettingsActions.chooseSystemIntent(
                    Settings.ACTION_WIFI_SETTINGS, routes, intent -> true).getAction()), "Public requested page wins");
            check(Settings.ACTION_SETTINGS.equals(QuickSettingsActions.chooseSystemIntent(
                    Settings.ACTION_WIFI_SETTINGS, routes, intent -> Settings.ACTION_SETTINGS.equals(intent.getAction())).getAction()),
                    "General settings wins when a requested page is unavailable");
            for (String requested : new String[]{Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_BLUETOOTH_SETTINGS}) {
                check(Settings.ACTION_WIRELESS_SETTINGS.equals(QuickSettingsActions.chooseSystemIntent(
                        requested, routes, intent -> Settings.ACTION_WIRELESS_SETTINGS.equals(intent.getAction())).getAction()),
                        "Wireless settings supports firmware without a dedicated network page");
                check(requested.equals(QuickSettingsActions.chooseSystemIntent(requested, routes,
                        intent -> true).getAction()), "The dedicated network page still takes precedence");
            }
            for (String appsAction : new String[]{Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS,
                    Settings.ACTION_APPLICATION_SETTINGS}) {
                check(appsAction.equals(QuickSettingsActions.chooseSystemIntent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS,
                        routes, intent -> appsAction.equals(intent.getAction())).getAction()),
                        "Alternative public Apps pages work before general settings");
            }
            check(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS.equals(QuickSettingsActions.chooseSystemIntent(
                    Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, routes,
                    intent -> !Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS.equals(intent.getAction())).getAction()),
                    "All-app management takes precedence over the less-specific Apps page");
            check(own.equals(QuickSettingsActions.chooseSystemIntent(Settings.ACTION_WIFI_SETTINGS, routes,
                    intent -> own.equals(intent.getComponent())).getComponent()), "Configured fallback supports firmware without public settings");
            check(Settings.ACTION_WIFI_SETTINGS.equals(QuickSettingsActions.chooseSystemIntent(
                    Settings.ACTION_WIFI_SETTINGS, routes, intent -> false).getAction()), "No handlers leaves an intent for the caller's unavailable message");
            routes.put("settings_fallback_component", "invalid");
            check(QuickSettingsActions.chooseSystemIntent(Settings.ACTION_WIFI_SETTINGS, routes,
                    intent -> false).getComponent() == null, "Malformed final fallback is ignored");

            ActivityInfo info = new ActivityInfo();
            info.enabled = true; info.exported = true;
            info.applicationInfo = new ApplicationInfo(); info.applicationInfo.enabled = true;
            check(QuickSettingsActions.launchable(context, info), "Enabled exported destination is allowed");
            info.enabled = false;
            check(!QuickSettingsActions.launchable(context, info), "Disabled activity is rejected");
            info.enabled = true; info.applicationInfo.enabled = false;
            check(!QuickSettingsActions.launchable(context, info), "Disabled package is rejected");
            info.applicationInfo.enabled = true; info.exported = false;
            check(!QuickSettingsActions.launchable(context, info), "Private activity is rejected");
            info.exported = true; info.permission = "android.permission.BIND_ACCESSIBILITY_SERVICE";
            check(!QuickSettingsActions.launchable(context, info), "Unheld permission is rejected");
            check(!QuickSettingsActions.launchable(context, null), "Absent activity is rejected");

            ResolveInfo firstVendor = handler("firmware.alpha.settings", ApplicationInfo.FLAG_SYSTEM);
            ResolveInfo secondVendor = handler("firmware.beta.settings", ApplicationInfo.FLAG_UPDATED_SYSTEM_APP);
            ResolveInfo duplicate = handler("firmware.alpha.settings", ApplicationInfo.FLAG_SYSTEM);
            ResolveInfo thirdParty = handler("user.settings.lookalike", 0);
            ResolveInfo disabledVendor = handler("firmware.disabled.settings", ApplicationInfo.FLAG_SYSTEM);
            disabledVendor.activityInfo.enabled = false;
            ResolveInfo privateVendor = handler("firmware.private.settings", ApplicationInfo.FLAG_SYSTEM);
            privateVendor.activityInfo.exported = false;
            ResolveInfo protectedVendor = handler("firmware.protected.settings", ApplicationInfo.FLAG_SYSTEM);
            protectedVendor.activityInfo.permission = "android.permission.BIND_ACCESSIBILITY_SERVICE";
            List<ResolveInfo> handlers = Arrays.asList(firstVendor, secondVendor, duplicate, thirdParty,
                    disabledVendor, privateVendor, protectedVendor, null, new ResolveInfo());
            check(QuickSettingsActions.settingsPackages("configured.settings", handlers,
                    activityInfo -> QuickSettingsActions.launchable(context, activityInfo)).equals(Arrays.asList(
                    "configured.settings", "firmware.alpha.settings", "firmware.beta.settings")),
                    "Discover different system vendor packages in order, skipping duplicates and inaccessible/lookalike apps");
            check(QuickSettingsActions.settingsPackages("firmware.alpha.settings", handlers,
                    activityInfo -> QuickSettingsActions.launchable(context, activityInfo)).equals(Arrays.asList(
                    "firmware.alpha.settings", "firmware.beta.settings")), "Configured package is not queried twice");
            check(QuickSettingsActions.settingsPackages("configured.settings", handlers,
                    activityInfo -> false).equals(Arrays.asList("configured.settings")),
                    "Unavailable discovery retains the configured destination");
            result.putString("stream", "PASS: " + checks + " settings routing checks.\n");
            // Restore before reporting completion, including failed runs.
        } catch (Throwable failure) {
            result.putString("stream", "FAIL after " + checks + " settings routing checks: "
                    + android.util.Log.getStackTraceString(failure));
        } finally {
            if (read) try {
                if (existed) Files.write(file.toPath(), original);
                else Files.deleteIfExists(file.toPath());
            } catch (Exception restoreFailure) {
                result.putString("stream", "FAIL restoring original theme: " + restoreFailure);
            }
        }
        finish(result.getString("stream", "").startsWith("PASS:") ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private void check(boolean success, String message) {
        if (!success) throw new AssertionError(message);
        checks++;
    }

    private ResolveInfo handler(String pkg, int flags) {
        ResolveInfo result = new ResolveInfo();
        result.activityInfo = new ActivityInfo();
        result.activityInfo.packageName = pkg;
        result.activityInfo.enabled = true;
        result.activityInfo.exported = true;
        result.activityInfo.applicationInfo = new ApplicationInfo();
        result.activityInfo.applicationInfo.enabled = true;
        result.activityInfo.applicationInfo.flags = flags;
        return result;
    }
}
