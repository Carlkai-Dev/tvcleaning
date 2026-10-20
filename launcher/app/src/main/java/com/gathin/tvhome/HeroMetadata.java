package com.gathin.tvhome;

import java.util.Locale;

public final class HeroMetadata {
    public static class Info {
        public final String badge;
        public final String subtitle;
        public Info(String badge, String subtitle) {
            this.badge = badge;
            this.subtitle = subtitle;
        }
    }

    public static Info getInfo(String pkg, String label) {
        String lower = (pkg + " " + (label == null ? "" : label)).toLowerCase(Locale.US);

        if (lower.contains("moviebox")) {
            return new Info("4K ULTRA HD", "Unlimited Movies, Series & Trending Shows");
        }
        if (lower.contains("netflix")) {
            return new Info("DOLBY VISION", "Originals, Blockbusters & Award-Winning Series");
        }
        if (lower.contains("hotstar") || lower.contains("jiohotstar")) {
            return new Info("LIVE CRICKET & 4K", "T20, World Cup, Disney+ & HBO Hits");
        }
        if (lower.contains("sonyliv") || lower.contains("sony liv")) {
            return new Info("SONY EXCLUSIVES", "Live UEFA, Tennis, Originals & Sony Classics");
        }
        if (lower.contains("smarttube") || lower.contains("youtube")) {
            return new Info("4K 60FPS · NO ADS", "Ad-Free YouTube, SponsorBlock & Music");
        }
        if (lower.contains("cricfy")) {
            return new Info("LIVE SPORTS HD", "Free Worldwide Live Sports & Cricket Matches");
        }
        if (lower.contains("downloader")) {
            return new Info("FAST SIDELOADER", "Direct APK Downloads & Web Browser");
        }
        if (lower.contains("file")) {
            return new Info("STORAGE MANAGER", "Fast Local & USB File Management");
        }
        if (lower.contains("mouse")) {
            return new Info("VIRTUAL MOUSE", "Smooth On-Screen Cursor for Sideloaded Apps");
        }
        if (lower.contains("prime")) {
            return new Info("AMAZON PRIME", "Exclusive Movies, Series & Fast Streaming");
        }

        int cat = SmartUsageManager.getAppCategory(pkg, label);
        if (cat == SmartUsageManager.CATEGORY_MOVIES) {
            return new Info("STREAMING", "Movies & High Definition Shows");
        } else if (cat == SmartUsageManager.CATEGORY_LIVE_SPORTS) {
            return new Info("LIVE TV", "Real-Time Broadcast Channels");
        } else if (cat == SmartUsageManager.CATEGORY_MEDIA) {
            return new Info("MEDIA", "Video & Audio Player");
        }
        return new Info("FAST APP", "Installed Application · CleanYourTV");
    }
}
