package io.github.epochcoder.homelab;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Recent Immich photos in the manner of Google Photos' ambient mode, on the views in layout/slideshow.xml:
 * a crossfade every few seconds, a slow zoom, two portrait photos side by side, a soft blurred copy of the
 * photo behind it instead of black bars, and the album, date, place and people bottom left with the clock
 * bottom right. Used by the screensaver ({@link PhotoDream}) and by {@link SlideshowActivity}.
 *
 * Threads: one background thread, running a {@link Worker} that owns the photo list and every request, prepares
 * the next slide while the current one is on screen, so a slide changes on time however slow the server. The
 * main thread only shows what is ready. Each start gets a new Worker, so a request still finishing from an
 * earlier run can only touch that run's state, and its result is dropped. At most about five decoded photos are alive: the slide on screen, the one fading out, the next
 * one ready, and one kept back when a portrait found no portrait partner.
 */
final class Slideshow {
    private static final String TAG = "Homelab.Slideshow";
    private static final long FADE_MS = 1500;
    private static final long CAPTION_FADE_MS = 500;
    private static final long RETRY_MS = 60_000;
    /** Consecutive failures before the error goes on screen instead of only into logcat. */
    private static final int SHOW_ERROR_AFTER = 3;
    /** How far the slow zoom goes over one slide; small enough to read as calm, not as movement. */
    private static final float ZOOM = 1.06f;
    /** Albums looked up per photo shown, remembered for the next round. */
    private static final int ALBUM_CACHE = 512;

    /** A fetched and decoded photo. */
    private static final class Decoded {
        final Immich.Photo photo;
        final Bitmap bitmap;

        Decoded(Immich.Photo photo, Bitmap bitmap) {
            this.photo = photo;
            this.bitmap = bitmap;
        }

        boolean portrait() {
            return isPortrait(bitmap.getWidth(), bitmap.getHeight());
        }
    }

    /** Everything one slide shows, prepared off the main thread. */
    private static final class Slide {
        final Decoded first;
        final Decoded second;   // the right-hand portrait, or null
        final Bitmap backdrop;
        final Caption caption;

        Slide(Decoded first, Decoded second, Bitmap backdrop, Caption caption) {
            this.first = first;
            this.second = second;
            this.backdrop = backdrop;
            this.caption = caption;
        }
    }

    private final Context ctx;
    private final View[] slides;
    private final ObjectAnimator[] zooms = new ObjectAnimator[2];
    private final View scrim;
    private final View captionBox;
    private final TextView title;
    private final TextView subtitle;
    private final TextView people;
    private final View clock;
    private final TextClock clockDate;
    private final TextView status;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    // Main thread.
    private ExecutorService io;
    private Worker worker;
    private Config.Immich cfg;
    private boolean running;
    private int front;
    private Slide ready;
    /** The current slide's time is up and the next one is not ready yet: show it the moment it is. */
    private boolean due;
    /** Bumped on every start and stop, so a callback from an earlier run does nothing. */
    private int generation;

    // Set on the main thread before the background thread starts, read there after.
    private String datePattern;
    private Locale locale;
    private int screenW;
    private int screenH;

    Slideshow(View root) {
        ctx = root.getContext();
        slides = new View[]{root.findViewById(R.id.slide_a), root.findViewById(R.id.slide_b)};
        scrim = root.findViewById(R.id.scrim);
        captionBox = root.findViewById(R.id.caption);
        title = root.findViewById(R.id.title);
        subtitle = root.findViewById(R.id.subtitle);
        people = root.findViewById(R.id.people);
        clock = root.findViewById(R.id.clock);
        clockDate = root.findViewById(R.id.clock_date);
        status = root.findViewById(R.id.status);
    }

    void start() {
        if (running) return;
        running = true;
        generation++;
        Config config = Config.load(ctx);
        cfg = config.immich;
        captionBox.setVisibility(View.GONE);
        clock.setVisibility(cfg.showClock ? View.VISIBLE : View.GONE);
        scrim.setVisibility(cfg.showClock || cfg.details ? View.VISIBLE : View.GONE);
        if (!cfg.configured()) {
            showStatus(ctx.getString(R.string.not_configured, "Immich"));
            return;
        }
        locale = Locale.getDefault();
        datePattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMMyyyy");
        String clockPattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM");
        clockDate.setFormat12Hour(clockPattern);
        clockDate.setFormat24Hour(clockPattern);
        DisplayMetrics screen = ctx.getResources().getDisplayMetrics();
        screenW = screen.widthPixels;
        screenH = screen.heightPixels;
        worker = new Worker(cfg, new Immich(cfg, Tls.socketFactory(config.caPem)), generation);
        due = true;
        io = Executors.newSingleThreadExecutor();
        io.execute(worker::prepare);
    }

    void stop() {
        running = false;
        generation++;
        main.removeCallbacksAndMessages(null);
        if (io != null) io.shutdownNow();
        io = null;
        worker = null;
        ready = null;
        for (int i = 0; i < slides.length; i++) clear(i);
    }

    /** One run's background state and work, on the background thread only. */
    private final class Worker {
        private final Config.Immich cfg;
        private final Immich api;
        /** The run this worker belongs to: what it posts is dropped once that run has stopped. */
        private final int gen;
        private List<Immich.Photo> photos = Collections.emptyList();
        private int next;
        /** A decoded photo kept for the next slide: a portrait whose neighbour was not a portrait. */
        private Decoded spare;
        private int failures;
        private final Map<String, String> albums = new LinkedHashMap<String, String>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > ALBUM_CACHE;
            }
        };

        Worker(Config.Immich cfg, Immich api, int gen) {
            this.cfg = cfg;
            this.api = api;
            this.gen = gen;
        }

        /** Prepares the next slide, fetching the list again once every photo has been shown. */
        private void prepare() {
            try {
                if (spare == null && next >= photos.size()) {
                    List<Immich.Photo> fresh = api.recentPhotos(System.currentTimeMillis());
                    if (cfg.shuffle) Collections.shuffle(fresh);
                    photos = fresh;
                    next = 0;
                    if (photos.isEmpty()) {
                        post(gen, () -> {
                            showStatus(ctx.getString(R.string.no_photos));
                            retry(RETRY_MS * 10);
                        });
                        return;
                    }
                }
                Decoded first = spare != null ? spare : decodeNext();
                spare = null;
                Decoded second = null;
                if (cfg.pairPortraits && first.portrait() && next < photos.size()) {
                    // A photo that fails here is skipped like any other; the slide just shows one portrait.
                    Decoded candidate = decodeNextOrNull();
                    if (candidate != null && candidate.portrait()) second = candidate;
                    else spare = candidate;
                }
                Caption caption = cfg.details
                        ? Caption.of(first.photo, albumOf(first.photo.id), datePattern, locale) : null;
                Slide slide = new Slide(first, second, backdrop(first.bitmap), caption);
                failures = 0;
                post(gen, () -> onPrepared(slide));
            } catch (Exception e) {
                int f = ++failures;
                String msg = String.valueOf(e.getMessage());
                Log.w(TAG, "slide: " + e);
                post(gen, () -> {
                    if (f >= SHOW_ERROR_AFTER) showStatus(ctx.getString(R.string.error, "Immich", msg));
                    retry(f >= SHOW_ERROR_AFTER ? RETRY_MS : 1000);
                });
            }
        }

        private Decoded decodeNext() throws IOException {
            Immich.Photo p = photos.get(next++);
            Bitmap b = decode(api.preview(p.id), screenW, screenH);
            if (b == null) throw new IOException("not an image: " + p.id);
            return new Decoded(p, b);
        }

        private Decoded decodeNextOrNull() {
            try {
                return decodeNext();
            } catch (IOException e) {
                Log.w(TAG, "photo: " + e);
                return null;
            }
        }

        /** An album the photo is in, "" if none or when the lookup fails: the caption then leads with the place. */
        private String albumOf(String id) {
            String known = albums.get(id);
            if (known != null) return known;
            try {
                String name = api.albumOf(id);
                albums.put(id, name);
                return name;
            } catch (IOException e) {
                Log.w(TAG, "album of " + id + ": " + e);
                return "";
            }
        }
    }

    // Main thread.

    /** Runs {@code r} on the main thread if run {@code g} is still the current one. */
    private void post(int g, Runnable r) {
        main.post(() -> {
            if (running && g == generation) r.run();
        });
    }

    private void retry(long delayMs) {
        int g = generation;
        main.postDelayed(() -> {
            if (running && g == generation && io != null) io.execute(worker::prepare);
        }, delayMs);
    }

    private void onPrepared(Slide s) {
        if (due) {
            due = false;
            show(s);
        } else {
            ready = s;
        }
    }

    /** The current slide's time is up: show the next one now, or as soon as it is ready. */
    private void tick() {
        if (ready != null) {
            Slide s = ready;
            ready = null;
            show(s);
        } else {
            due = true;
        }
    }

    private void show(Slide s) {
        status.setVisibility(View.GONE);
        int in = 1 - front;
        int out = front;
        bind(slides[in], s);
        slides[in].animate().alpha(1f).setDuration(FADE_MS).start();
        if (cfg.motion) zoom(in);
        slides[out].animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> clear(out)).start();
        front = in;
        caption(s.caption);
        if (io != null) io.execute(worker::prepare);
        main.postDelayed(this::tick, cfg.intervalSeconds * 1000L);
    }

    private void bind(View slide, Slide s) {
        ImageView photo = slide.findViewById(R.id.photo);
        View pair = slide.findViewById(R.id.pair);
        ((ImageView) slide.findViewById(R.id.backdrop)).setImageBitmap(s.backdrop);
        if (s.second != null) {
            ((ImageView) slide.findViewById(R.id.left)).setImageBitmap(s.first.bitmap);
            ((ImageView) slide.findViewById(R.id.right)).setImageBitmap(s.second.bitmap);
            pair.setVisibility(View.VISIBLE);
            photo.setVisibility(View.GONE);
        } else {
            Bitmap b = s.first.bitmap;
            photo.setScaleType(fills(b.getWidth(), b.getHeight(), screenW, screenH, cfg.crop)
                    ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
            photo.setImageBitmap(s.first.bitmap);
            photo.setVisibility(View.VISIBLE);
            pair.setVisibility(View.GONE);
        }
        slide.setAlpha(0f);
        slide.setScaleX(1f);
        slide.setScaleY(1f);
        slide.bringToFront();
        // The text and the clock stay above the slides.
        scrim.bringToFront();
        captionBox.bringToFront();
        clock.bringToFront();
        status.bringToFront();
    }

    /** A slow zoom towards a random point near the middle, lasting the whole time the slide is visible. */
    private void zoom(int i) {
        View v = slides[i];
        v.setPivotX(screenW * (0.3f + 0.4f * random.nextFloat()));
        v.setPivotY(screenH * (0.3f + 0.4f * random.nextFloat()));
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, ZOOM),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, ZOOM));
        a.setDuration(cfg.intervalSeconds * 1000L + 2 * FADE_MS);
        a.setInterpolator(new LinearInterpolator());
        a.start();
        zooms[i] = a;
    }

    private void clear(int i) {
        if (zooms[i] != null) zooms[i].cancel();
        zooms[i] = null;
        View slide = slides[i];
        slide.animate().cancel();
        slide.setAlpha(0f);
        for (int id : new int[]{R.id.backdrop, R.id.photo, R.id.left, R.id.right}) {
            ((ImageView) slide.findViewById(id)).setImageDrawable(null);
        }
    }

    /** The caption fades out, changes and fades back in, a little behind the photo. */
    private void caption(Caption c) {
        if (c == null) {
            captionBox.setVisibility(View.GONE);
            return;
        }
        Runnable fill = () -> {
            setLine(title, c.title);
            setLine(subtitle, c.subtitle);
            setLine(people, c.people);
            captionBox.animate().alpha(1f).setDuration(CAPTION_FADE_MS * 2).start();
        };
        if (captionBox.getVisibility() != View.VISIBLE) {
            captionBox.setAlpha(0f);
            captionBox.setVisibility(View.VISIBLE);
            fill.run();
        } else {
            captionBox.animate().alpha(0f).setDuration(CAPTION_FADE_MS).withEndAction(fill).start();
        }
    }

    private static void setLine(TextView v, String text) {
        v.setText(text);
        v.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showStatus(String text) {
        status.setText(text);
        status.setVisibility(View.VISIBLE);
        status.bringToFront();
    }

    /**
     * The photo shrunk to a few dozen pixels: the screen scales it back up with filtering, which reads as a
     * strong blur of the photo's own colours behind it, without a blur pass the TV's GPU would have to run.
     */
    private static Bitmap backdrop(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        Bitmap mid = Bitmap.createScaledBitmap(src, 96, Math.max(1, Math.round(96f * h / w)), true);
        Bitmap small = Bitmap.createScaledBitmap(mid, 24, Math.max(1, Math.round(24f * h / w)), true);
        if (mid != src && mid != small) mid.recycle();
        return small;
    }

    /**
     * Whether a photo fills the screen (cropping its edges) rather than fitting over its blurred backdrop.
     * A landscape photo close to the screen's shape fills, so a 3:2 camera shot on 16:9 loses a sliver instead
     * of gaining bars; with "Fill the screen" every landscape photo does. A portrait never fills: cropping it to
     * 16:9 cuts off heads.
     */
    static boolean fills(int w, int h, int screenW, int screenH, boolean always) {
        if (w <= 0 || h <= 0 || screenW <= 0 || screenH <= 0 || isPortrait(w, h) || h > w) return false;
        if (always) return true;
        float photo = (float) w / h;
        float screen = (float) screenW / screenH;
        return Math.abs(photo - screen) / screen <= 0.2f;
    }

    /** Taller than wide by a margin, so a square-ish photo still gets the screen to itself. */
    static boolean isPortrait(int w, int h) {
        return h > w * 1.1f;
    }

    /** Decoded no larger than the screen needs; previews are about 1440 px, so usually at full size. */
    static Bitmap decode(byte[] data, int maxW, int maxH) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        o.inSampleSize = sampleSize(o.outWidth, o.outHeight, maxW, maxH);
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(data, 0, data.length, o);
    }

    /** The largest power of two that keeps the image at least as large as the screen on both sides. */
    static int sampleSize(int w, int h, int maxW, int maxH) {
        int s = 1;
        while (w / (s * 2) >= maxW && h / (s * 2) >= maxH) s *= 2;
        return s;
    }
}
