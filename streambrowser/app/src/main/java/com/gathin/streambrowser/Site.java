package com.gathin.streambrowser;

final class Site {
    final String id;
    final String title;
    final String description;
    final String url;
    final int accentColor;
    final boolean aggressiveAdBlock;

    Site(String id, String title, String description, String url, int accentColor, boolean aggressiveAdBlock) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.url = url;
        this.accentColor = accentColor;
        this.aggressiveAdBlock = aggressiveAdBlock;
    }
}
