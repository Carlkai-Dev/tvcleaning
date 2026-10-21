package io.github.epochcoder.homelab;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLSocketFactory;

/**
 * Homepage (gethomepage.dev) full screen. Homepage refreshes its own widgets, so this is a WebView pointed at
 * the configured URL, made for a remote:
 *
 * - Up and Down scroll the page (most of a dashboard has nothing focusable); Menu, or OK on the error screen,
 *   loads it again. A hint says so after the first load, then fades.
 * - A loading screen until the page is drawn, and an error screen that says what failed (unreachable, an HTTP
 *   error, a certificate) with Try again and Settings, instead of a blank or a browser error page.
 * - Navigation stays on Homepage's host: a service link would open a web app the remote cannot drive.
 * - Coming back after a while reloads, so a dashboard left in the background is not stale.
 * - Optionally keeps the screen on, for a dashboard left on the wall.
 * - A certificate from the configured CA (Caddy's internal one) is accepted after {@link Tls#sameCertificate}
 *   has checked it, since the WebView only knows the system CAs.
 */
public class DashboardActivity extends Activity {
    private static final String TAG = "Homelab.Dashboard";
    /** CSS pixels per Up/Down press. */
    private static final int SCROLL_STEP = 320;
    /** Back after this long in the background reloads the page. */
    private static final long STALE_MS = 10 * 60 * 1000;
    private static final long HINT_MS = 5000;

    private WebView web;
    private View loading;
    private View error;
    private TextView errorMessage;
    private View hint;
    private String url;
    private String host;
    private SSLSocketFactory tls;
    private ExecutorService io;
    private long pausedAt;
    private boolean hinted;
    /** The main page failed; the WebView's own error page stays hidden behind ours. */
    private boolean failed;
    /**
     * Host and certificate pairs already checked this session, so each is verified once, not per request. Keyed
     * by both: a certificate checked for one host proves nothing about another.
     */
    private final Set<String> trusted = new HashSet<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_dashboard);
        web = findViewById(R.id.web);
        loading = findViewById(R.id.loading);
        error = findViewById(R.id.error);
        errorMessage = findViewById(R.id.error_message);
        hint = findViewById(R.id.hint);
        findViewById(R.id.retry).setOnClickListener(v -> load());
        findViewById(R.id.open_settings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
        });

        Config config = Config.load(this);
        Config.Homepage cfg = config.homepage;
        if (!cfg.configured()) {
            showError(getString(R.string.not_configured, "Homepage"));
            ((TextView) findViewById(R.id.error_title)).setText(R.string.dashboard_name);
            findViewById(R.id.retry).setVisibility(View.GONE);
            findViewById(R.id.open_settings).requestFocus();
            return;
        }
        if (cfg.keepAwake) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        url = cfg.url;
        host = Uri.parse(cfg.url).getHost();
        tls = Tls.socketFactory(config.caPem);
        io = Executors.newSingleThreadExecutor();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);    // Homepage is a Next.js app; nothing renders without it
        s.setDomStorageEnabled(true);
        s.setTextZoom(cfg.textZoom);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setWebViewClient(new Client());
        load();
    }

    private void load() {
        if (url == null) return;
        failed = false;
        error.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
        web.loadUrl(url);
        web.requestFocus();
    }

    private void showError(String message) {
        failed = true;
        loading.setVisibility(View.GONE);
        errorMessage.setText(message);
        error.setVisibility(View.VISIBLE);
        findViewById(R.id.retry).requestFocus();
    }

    private final class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri u = request.getUrl();
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            boolean ours = (scheme.equals("http") || scheme.equals("https"))
                    && host != null && host.equalsIgnoreCase(u.getHost());
            if (!ours) Toast.makeText(DashboardActivity.this, getString(R.string.blocked_link, host), Toast.LENGTH_SHORT).show();
            return !ours;
        }

        @Override
        public void onPageFinished(WebView view, String finishedUrl) {
            if (failed) return;
            loading.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                loading.setVisibility(View.GONE);
                loading.setAlpha(1f);
            }).start();
            if (!hinted) {
                hinted = true;
                hint.setVisibility(View.VISIBLE);
                hint.postDelayed(() -> hint.animate().alpha(0f).setDuration(600)
                        .withEndAction(() -> hint.setVisibility(View.GONE)).start(), HINT_MS);
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError err) {
            if (!request.isForMainFrame()) return;
            showError(getString(R.string.dash_unreachable, host, String.valueOf(err.getDescription())));
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
            if (!request.isForMainFrame()) return;
            showError(getString(R.string.dash_http, response.getStatusCode(), host));
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError sslError) {
            byte[] leaf = Tls.encoded(sslError.getCertificate());
            if (tls == null || leaf == null) {
                Log.w(TAG, "TLS error " + sslError.getPrimaryError() + " on " + sslError.getUrl());
                handler.cancel();
                showError(getString(R.string.dash_tls, host));
                return;
            }
            String failing = sslError.getUrl();
            String key = Uri.parse(failing).getHost() + " " + Base64.encodeToString(leaf, Base64.NO_WRAP);
            if (trusted.contains(key)) {
                handler.proceed();
                return;
            }
            io.execute(() -> {
                boolean ok = Tls.sameCertificate(failing, tls, leaf);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    if (ok) {
                        trusted.add(key);
                        handler.proceed();
                    } else {
                        Log.w(TAG, "certificate for " + failing + " does not chain to the configured CA");
                        handler.cancel();
                        showError(getString(R.string.dash_tls, host));
                    }
                });
            });
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean showing = url != null && error.getVisibility() != View.VISIBLE;
        if (showing && (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP)) {
            int dy = keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? SCROLL_STEP : -SCROLL_STEP;
            web.evaluateJavascript("window.scrollBy({top:" + dy + ",behavior:'smooth'})", null);
            return true;
        }
        if (url != null && (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            load();
            return true;
        }
        if (showing && keyCode == KeyEvent.KEYCODE_BACK && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        if (url != null && pausedAt > 0 && SystemClock.elapsedRealtime() - pausedAt > STALE_MS) load();
    }

    @Override
    protected void onPause() {
        pausedAt = SystemClock.elapsedRealtime();
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (io != null) io.shutdownNow();
        web.destroy();
        super.onDestroy();
    }
}
