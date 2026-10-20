package com.gathin.streambrowser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HomeActivity extends Activity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_AGGRESSIVE = "aggressive";

    private static final long CAROUSEL_INTERVAL_MS = 5500L;
    private static final int TILES_PER_ROW_HINT = 4;

    private SiteStore store;
    private List<Site> sites = new ArrayList<>();
    private final List<WebView> bannerWebViews = new ArrayList<>();
    private final List<ProgressBar> bannerLoaders = new ArrayList<>();
    private final List<View> dots = new ArrayList<>();
    private final Set<Integer> loadedSlides = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ViewFlipper flipper;
    private View bannerFrame;
    private LinearLayout tileRows;
    private LinearLayout dotsRow;
    private int currentIndex;
    private boolean carouselPaused;

    private final Runnable advanceCarousel = new Runnable() {
        @Override
        public void run() {
            if (carouselPaused || flipper == null || flipper.getChildCount() == 0) return;
            showSlide((currentIndex + 1) % flipper.getChildCount(), true);
            handler.postDelayed(this, CAROUSEL_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        store = new SiteStore(this);

        flipper = findViewById(R.id.banner_flipper);
        bannerFrame = findViewById(R.id.banner_frame);
        tileRows = findViewById(R.id.tile_rows);
        dotsRow = findViewById(R.id.banner_dots);

        findViewById(R.id.btn_add_site).setOnClickListener(v -> showAddSiteDialog());

        bannerFrame.setOnClickListener(v -> {
            if (!sites.isEmpty()) openSite(sites.get(currentIndex));
        });
        bannerFrame.setOnFocusChangeListener((v, hasFocus) -> {
            carouselPaused = hasFocus;
            if (hasFocus) {
                handler.removeCallbacks(advanceCarousel);
                v.animate().scaleX(1.01f).scaleY(1.01f).setDuration(120).start();
            } else {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                scheduleCarousel();
            }
        });
        bannerFrame.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN || sites.isEmpty()) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                showSlide((currentIndex - 1 + sites.size()) % sites.size(), true);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                showSlide((currentIndex + 1) % sites.size(), true);
                return true;
            }
            return false;
        });

        rebuildUi();
        bannerFrame.requestFocus();
    }

    private void rebuildUi() {
        destroyBanners();
        sites = store.allVisible();
        currentIndex = 0;
        loadedSlides.clear();
        setupCarousel();
        setupDots();
        setupTiles();
        if (!sites.isEmpty()) ensureSlideLoaded(0);
        scheduleCarousel();
    }

    private void setupTiles() {
        tileRows.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int tileW = Math.max((int) (140 * density),
                (screenW - (int) (96 * density)) / Math.min(TILES_PER_ROW_HINT, Math.max(1, sites.size())));
        int tileH = (int) (120 * density);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Site site : sites) {
            View tile = inflater.inflate(R.layout.item_site_tile, tileRows, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, tileH);
            lp.setMarginEnd((int) (10 * density));
            tile.setLayoutParams(lp);
            bindTile(tile, site);
            tileRows.addView(tile);
        }
        if (tileRows.getChildCount() > 0) {
            bannerFrame.setNextFocusDownId(View.NO_ID);
            tileRows.getChildAt(0).setNextFocusUpId(R.id.banner_frame);
        }
    }

    private void bindTile(View tile, Site site) {
        TextView title = tile.findViewById(R.id.tile_title);
        TextView desc = tile.findViewById(R.id.tile_desc);
        View accent = tile.findViewById(R.id.tile_accent);
        title.setText(site.title);
        desc.setText(site.description);

        GradientDrawable bar = new GradientDrawable();
        bar.setCornerRadius(8f);
        bar.setColor(site.accentColor);
        accent.setBackground(bar);

        tile.setOnClickListener(v -> openSite(site));
        tile.setOnLongClickListener(v -> {
            confirmRemove(site);
            return true;
        });
        tile.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? 1.05f : 1f;
            v.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
        });
        tile.setNextFocusUpId(R.id.banner_frame);
    }

    private void showAddSiteDialog() {
        View form = getLayoutInflater().inflate(R.layout.dialog_add_site, null);
        EditText name = form.findViewById(R.id.input_title);
        EditText url = form.findViewById(R.id.input_url);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(form)
                .setPositiveButton(R.string.add_site_ok, null)
                .setNegativeButton(R.string.add_site_cancel, null)
                .create();
        dialog.setOnShowListener(d -> {
            Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            ok.setOnClickListener(v -> {
                String u = url.getText() != null ? url.getText().toString() : "";
                if (SiteStore.normalizeUrl(u) == null) {
                    Toast.makeText(this, R.string.add_site_bad_url, Toast.LENGTH_SHORT).show();
                    return;
                }
                String t = name.getText() != null ? name.getText().toString() : "";
                if (!store.addCustom(t, u)) {
                    Toast.makeText(this, R.string.add_site_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.dismiss();
                rebuildUi();
                if (!sites.isEmpty()) {
                    showSlide(sites.size() - 1, false);
                }
            });
            url.requestFocus();
        });
        dialog.show();
    }

    private void confirmRemove(Site site) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.remove_site_confirm, site.title))
                .setPositiveButton(R.string.remove_site, (d, w) -> {
                    store.remove(site.id);
                    Toast.makeText(this, R.string.site_removed, Toast.LENGTH_SHORT).show();
                    rebuildUi();
                    bannerFrame.requestFocus();
                })
                .setNegativeButton(R.string.add_site_cancel, null)
                .show();
    }

    private void setupCarousel() {
        flipper.removeAllViews();
        bannerWebViews.clear();
        bannerLoaders.clear();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Site site : sites) {
            View slide = inflater.inflate(R.layout.item_banner_slide, flipper, false);
            TextView label = slide.findViewById(R.id.banner_label);
            TextView title = slide.findViewById(R.id.banner_title);
            TextView desc = slide.findViewById(R.id.banner_desc);
            ProgressBar loading = slide.findViewById(R.id.banner_loading);
            WebView web = slide.findViewById(R.id.banner_webview);

            label.setText(R.string.banner_preview);
            title.setText(site.title);
            desc.setText(site.description);
            configurePreviewWebView(web, site, loading);
            bannerWebViews.add(web);
            bannerLoaders.add(loading);
            flipper.addView(slide);
        }
        flipper.setInAnimation(this, android.R.anim.fade_in);
        flipper.setOutAnimation(this, android.R.anim.fade_out);
        if (flipper.getChildCount() > 0) flipper.setDisplayedChild(0);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configurePreviewWebView(WebView web, Site site, ProgressBar loading) {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setAllowFileAccess(false);
        s.setLoadsImagesAutomatically(true);
        s.setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        web.setInitialScale(42);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setOnTouchListener((v, event) -> true);
        web.setTag(site);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (AdBlocker.shouldBlock(request)) {
                    return AdBlocker.emptyResponse();
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                loading.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                loading.setVisibility(View.GONE);
                Site s = (Site) view.getTag();
                view.evaluateJavascript(AdBlocker.cleanupScript(s != null && s.aggressiveAdBlock), null);
                view.evaluateJavascript(
                        "(function(){try{"
                                + "document.documentElement.style.overflow='hidden';"
                                + "document.body.style.overflow='hidden';"
                                + "window.scrollTo(0,0);"
                                + "}catch(e){}})();",
                        null);
            }
        });
        web.setWebChromeClient(new WebChromeClient());
    }

    private void ensureSlideLoaded(int index) {
        if (index < 0 || index >= sites.size() || loadedSlides.contains(index)) return;
        loadedSlides.add(index);
        WebView web = bannerWebViews.get(index);
        bannerLoaders.get(index).setVisibility(View.VISIBLE);
        web.loadUrl(sites.get(index).url);
    }

    private void setupDots() {
        dotsRow.removeAllViews();
        dots.clear();
        int size = (int) (8 * getResources().getDisplayMetrics().density);
        int gap = (int) (6 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < sites.size(); i++) {
            ImageView dot = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            if (i > 0) lp.setMarginStart(gap);
            dot.setLayoutParams(lp);
            dot.setImageResource(i == 0 ? R.drawable.dot_active : R.drawable.dot_inactive);
            dotsRow.addView(dot);
            dots.add(dot);
        }
    }

    private void showSlide(int index, boolean animate) {
        if (index < 0 || index >= flipper.getChildCount()) return;
        currentIndex = index;
        ensureSlideLoaded(index);
        if (sites.size() > 1) ensureSlideLoaded((index + 1) % sites.size());

        if (!animate) {
            flipper.setInAnimation(null);
            flipper.setOutAnimation(null);
            flipper.setDisplayedChild(index);
            flipper.setInAnimation(this, android.R.anim.fade_in);
            flipper.setOutAnimation(this, android.R.anim.fade_out);
        } else {
            flipper.setDisplayedChild(index);
        }
        for (int i = 0; i < dots.size(); i++) {
            ((ImageView) dots.get(i)).setImageResource(
                    i == index ? R.drawable.dot_active : R.drawable.dot_inactive);
        }
        for (int i = 0; i < bannerWebViews.size(); i++) {
            WebView w = bannerWebViews.get(i);
            if (i == index) w.onResume();
            else w.onPause();
        }
    }

    private void scheduleCarousel() {
        handler.removeCallbacks(advanceCarousel);
        if (!carouselPaused && sites.size() > 1) {
            handler.postDelayed(advanceCarousel, CAROUSEL_INTERVAL_MS);
        }
    }

    private void openSite(Site site) {
        Intent i = new Intent(this, BrowserActivity.class);
        i.putExtra(EXTRA_URL, site.url);
        i.putExtra(EXTRA_TITLE, site.title);
        i.putExtra(EXTRA_AGGRESSIVE, site.aggressiveAdBlock);
        startActivity(i);
    }

    private void destroyBanners() {
        handler.removeCallbacks(advanceCarousel);
        for (WebView w : bannerWebViews) {
            w.stopLoading();
            ViewGroup parent = (ViewGroup) w.getParent();
            if (parent != null) parent.removeView(w);
            w.destroy();
        }
        bannerWebViews.clear();
        bannerLoaders.clear();
        if (flipper != null) flipper.removeAllViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!bannerWebViews.isEmpty()) showSlide(currentIndex, false);
        scheduleCarousel();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(advanceCarousel);
        for (WebView w : bannerWebViews) w.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        destroyBanners();
        super.onDestroy();
    }
}
