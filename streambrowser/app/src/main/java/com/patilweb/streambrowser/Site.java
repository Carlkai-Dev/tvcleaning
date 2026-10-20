package com.patilweb.streambrowser;

final class Site {
    final String id;
    final String title;
    final String description;
    final String url;
    final int accentColor;
    final boolean aggressiveAdBlock;
    /** Built-in sites can be hidden; custom ones can be deleted. */
    final boolean custom;

    Site(String id, String title, String description, String url, int accentColor,
         boolean aggressiveAdBlock, boolean custom) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.url = url;
        this.accentColor = accentColor;
        this.aggressiveAdBlock = aggressiveAdBlock;
        this.custom = custom;
    }

    Site(String id, String title, String description, String url, int accentColor, boolean aggressiveAdBlock) {
        this(id, title, description, url, accentColor, aggressiveAdBlock, false);
    }
}
