package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * link_tiles: tiles that open a link in another app instead of its start screen, such as a TV channel straight in a
 * broadcaster's app (VTV1 in VTVgo: an older owner presses OK and the channel plays). Each entry is
 * "Label|package|uri|#colour". A tile acts as an app named {@link #PREFIX} plus the label in lower case, so dock_apps,
 * hidden_apps, the saved order and Remove from Home work on it unchanged.
 */
final class LinkTiles {
    static final String PREFIX = "link.tile.";
    private static final int MAX = 12;

    static final class Link {
        final String label, target, uri, pkg;
        final int color;
        /** link-<name>.jpg beside config.json, or null to draw the label on the colour. */
        Drawable picture;

        Link(String label, String target, String uri, int color) {
            this.label = label;
            this.target = target;
            this.uri = uri;
            this.color = color;
            this.pkg = PREFIX + name(label);
        }
    }

    private LinkTiles() {
    }

    /** The tile's name: the label in lower case with anything but a-z and 0-9 dropped ("VTV 1" is vtv1). */
    static String name(String label) {
        return label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Whether a theme file name is a link tile's picture (ThemeFiles takes it next to config.json). */
    static boolean isPictureName(String file) {
        return file.matches("link-[a-z0-9]{1,24}\\.jpg");
    }

    /** One entry, or null when it is malformed: a label up to 24 characters, a package, a uri with a scheme. */
    static Link parse(String entry) {
        if (entry == null) return null;
        String[] p = entry.split("\\|", -1);
        if (p.length < 3 || p.length > 4) return null;
        String label = p[0].trim(), target = p[1].trim(), uri = p[2].trim();
        if (label.isEmpty() || label.length() > 24 || name(label).isEmpty() || !target.matches("[A-Za-z0-9_.]+")
                || !uri.matches("[A-Za-z][A-Za-z0-9+.-]*:.+")) return null;
        int color = 0xFF3A3A44;
        // "#RRGGBB" only, read here rather than by Color.parseColor so the JVM tests can call parse.
        if (p.length == 4 && p[3].trim().matches("#[0-9A-Fa-f]{6}")) color = 0xFF000000 | Integer.parseInt(p[3].trim().substring(1), 16);
        return new Link(label, target, uri, color);
    }

    /** The configured tiles whose app is installed, first 12, with their pictures. */
    static List<Link> load(Context ctx, List<String> entries) {
        List<Link> out = new ArrayList<>();
        if (entries.isEmpty()) return out;
        File dir = Config.dir(ctx);
        for (String entry : entries) {
            if (out.size() >= MAX) break;
            Link l = parse(entry);
            if (l == null) continue;
            try {
                ctx.getPackageManager().getPackageInfo(l.target, 0);
            } catch (Exception notInstalled) {
                continue;
            }
            File pic = dir != null ? new File(dir, "link-" + name(l.label) + ".jpg") : null;
            if (pic != null && pic.isFile() && pic.length() < 512 * 1024) {
                Bitmap b = BitmapFactory.decodeFile(pic.getPath());
                if (b != null) l.picture = new BitmapDrawable(ctx.getResources(), b);
            }
            out.add(l);
        }
        return out;
    }

    /** The tile face without a picture: the label in large bold type on a gradient of the link's colour. */
    static final class Face extends Drawable {
        private final String text;
        private final int color;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint type = new Paint(Paint.ANTI_ALIAS_FLAG);

        Face(String text, int color) {
            this.text = text;
            this.color = color;
            type.setColor(Color.WHITE);
            type.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            type.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onBoundsChange(Rect b) {
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            hsv[2] *= 0.62f;
            fill.setShader(new LinearGradient(0, b.top, 0, b.bottom, color, Color.HSVToColor(hsv), Shader.TileMode.CLAMP));
            float size = b.height() * 0.38f;
            type.setTextSize(size);
            // A long label shrinks to the card's width, with a margin either side.
            float w = type.measureText(text);
            if (w > b.width() * 0.84f) type.setTextSize(size * b.width() * 0.84f / w);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            c.drawRect(b, fill);
            Paint.FontMetrics m = type.getFontMetrics();
            c.drawText(text, b.exactCenterX(), b.exactCenterY() - (m.ascent + m.descent) / 2f, type);
        }

        @Override
        public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            type.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            fill.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }

        @Override
        public int getIntrinsicWidth() {
            return 320;
        }

        @Override
        public int getIntrinsicHeight() {
            return 180;
        }
    }
}
