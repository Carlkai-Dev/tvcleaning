#!/usr/bin/env python3
"""OnePlus Y1 (Android 11) package classification utility.
Classifies all packages into:
- DEFINITE_BLOAT (Telemetry, ad services, retail demos)
- DISCRETIONARY (Streaming apps, assistant, chromecast, updates - MUST prompt user)
- CRITICAL_CORE (Live TV, tuner, HDMI, remote, Bluetooth, keyboard, Play Store - NEVER touch)
"""
import os
import sys

CRITICAL_CORE = {
    "android",
    "com.mediatek.wwtv.tvcenter",      # Live TV / HDMI input tuner
    "com.mediatek.tvinput",            # Mtk TV Input service
    "com.mediatek.tv.service",          # TV framework service
    "com.mediatek.wwtv.setupwizard",
    "com.mediatek.wwtv.tvcenter.overlay",
    "com.android.providers.tv",         # TvProvider
    "com.android.tv.settings",          # TV Settings
    "com.android.tv.settings.overlay",
    "com.android.systemui",             # System UI
    "com.android.bluetooth",            # Bluetooth
    "com.oneplus.tv.remote",            # OnePlus remote service
    "com.google.android.tv.remote.service",
    "com.google.android.inputmethod.latin",  # On-screen keyboard
    "com.android.vending",              # Google Play Store
    "com.google.android.gms",           # Google Play Services
    "com.google.android.gsf",           # Google Services Framework
    "com.google.android.packageinstaller",
    "com.google.android.permissioncontroller",
    "com.android.providers.settings",
    "com.android.providers.media",
    "com.android.providers.media.module",
    "com.android.inputdevices",
    "com.android.shell",
    "com.android.networkstack.inprocess",
    "com.android.wifi.resources",
}

DEFINITE_BLOAT = {
    "android.autoinstalls.config.skyworth",   # Skyworth auto-install config
    "com.smartdevice.common.demonow",         # Store demo app
    "fusion.android.tv.demo",                 # OnePlus retail display demo
    "com.coocaa.factory",                     # Factory test tool
    "com.Smartdevice.emanual",                # E-manual
    "com.google.android.feedback",            # Google telemetry
    "com.google.android.leanbacklauncher.partnercustomizer", # Partner banner ads
    "com.google.android.tvrecommendations",   # Android TV recommendation ads
    "com.mstar.android.tv.disclaimercustomization",
    "com.airoha.ble.ota",                     # RC OTA demo
    "com.mediatek.testpq",                    # Picture quality test tool
    "com.mediatek.tv.factory",                # Factory command service
    "com.google.android.syncadapters.calendar",
    "com.android.providers.calendar",
    "com.google.android.boot.appsplashscreen",
}

DISCRETIONARY = {
    "com.google.android.katniss": "Google Assistant",
    "com.google.android.apps.mediashell": "Chromecast built-in",
    "com.google.android.backdrop": "Google Backdrop (Chromecast screensaver)",
    "com.google.android.tungsten.setupwraith": "Google Setup / OTA Update helper",
    "com.google.android.videos": "Google TV / Play Movies",
    "com.google.android.youtube.tvmusic": "YouTube Music",
    "com.google.android.play.games": "Google Play Games",
    "com.vodafone.vodafoneplay": "Vi Movies & TV",
    "com.viacom18.tv.voot": "Voot",
    "com.graymatrix.did": "Zee5",
    "com.jio.media.stb.ondemand": "JioCinema",
    "com.sonyliv": "SonyLIV",
    "com.mobisystems.fileman": "File Commander",
    "com.yablio.sendfilestotv": "Send Files to TV",
    "com.apple.atve.androidtv.appletv": "Apple TV app",
    "com.netflix.ninja": "Netflix",
    "com.amazon.amazonvideo.livingroom": "Amazon Prime Video",
    "in.startv.hotstar": "Disney+ Hotstar",
    "com.google.android.youtube.tv": "YouTube",
}


def load_installed_packages(path="backup/packages_all.txt"):
    pkgs = set()
    if not os.path.exists(path):
        alt = os.path.join(os.path.dirname(__file__), "..", "..", "backup", "packages_all.txt")
        if os.path.exists(alt):
            path = alt
        else:
            return pkgs
    with open(path) as f:
        for line in f:
            line = line.strip()
            if "=" in line:
                pkgs.add(line.split("=")[-1])
            elif line.startswith("package:"):
                pkgs.add(line[8:])
    return pkgs


def main():
    pkgs = load_installed_packages()
    print(f"Total installed packages: {len(pkgs)}")
    print("\n--- Definite Bloat (Safe to Disable) ---")
    for p in sorted(DEFINITE_BLOAT.intersection(pkgs)):
        print(f"  [X] {p}")

    print("\n--- Discretionary (Requires User Preference) ---")
    for p in sorted(DISCRETIONARY.keys()):
        if p in pkgs:
            print(f"  [?] {p} ({DISCRETIONARY[p]})")

    print("\n--- Stock Launcher (To be replaced by custom launcher) ---")
    if "com.google.android.tvlauncher" in pkgs:
        print("  [>] com.google.android.tvlauncher (Stock Android TV Launcher)")


if __name__ == "__main__":
    main()
