package io.github.epochcoder.homelab;

import android.net.Uri;
import android.net.http.SslError;
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
 * Homepage (gethomepage.dev) in a card under the tabs. Homepage refreshes its own widgets, so this
 * is a WebView pointed at the configured URL, made for a remote:
 *
 * <p>- Down from the tab goes into the page; Up and Down then scroll it (most of a dashboard has
 * nothing focusable), and Up at the top returns to the tabs. Menu, or OK on the error screen, loads
 * it again. A hint says so after the first load, then fades. - It loads the first time the tab is
 * shown, not when the app starts on another tab. - A loading screen until the page is drawn, and an
 * error screen that says what failed (unreachable, an HTTP error, a certificate) with Try again and
 * Settings, instead of a blank or a browser error page. - Navigation stays on Homepage's host: a
 * service link would open a web app the remote cannot drive. - Coming back after a while reloads,
 * so a dashboard left in the background is not stale. - Optionally keeps the screen on while the
 * tab is shown, for a dashboard left on the wall. - A certificate from the configured CA (Caddy's
 * internal one) is accepted after {@link Tls#sameCertificate} has checked it, since the WebView
 * only knows the system CAs.
 */
final class DashboardPage extends Page {
    private static final String TAG = "Homelab.Dashboard";

    /** CSS pixels per Up/Down press. */
    private static final int SCROLL_STEP = 320;

    /** Back after this long in the background, or on another tab, reloads the page. */
    private static final long STALE_MS = 10 * 60 * 1000;

    private static final long HINT_MS = 5000;

    private final WebView web;
    private final View loading;
    private final View error;
    private final View hint;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private String url;

    /** Homepage's host name, the only one navigation may go to. */
    private String server;

    private SSLSocketFactory tls;
    private boolean keepAwake;

    /** Set when the URL or the certificates changed; the next show loads afresh. */
    private boolean dirty = true;

    private long hiddenAt;
    private boolean hinted;

    /** The main page failed; the WebView's own error page stays hidden behind ours. */
    private boolean failed;

    /**
     * Host and certificate pairs already checked, so each is verified once, not per request. Keyed
     * by both: a certificate checked for one host proves nothing about another.
     */
    private final Set<String> trusted = new HashSet<>();

    DashboardPage(MainActivity host) {
        super(host, R.layout.page_dashboard);
        web = view.findViewById(R.id.web);
        loading = view.findViewById(R.id.loading);
        error = view.findViewById(R.id.error);
        hint = view.findViewById(R.id.hint);
        view.findViewById(R.id.card).setClipToOutline(true);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true); // Homepage is a Next.js app; nothing renders without it
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setWebViewClient(new Client());
    }

    @Override
    void onShow() {
        if (dirty) {
            dirty = false;
            configure();
        } else if (url != null
                && hiddenAt > 0
                && SystemClock.elapsedRealtime() - hiddenAt > STALE_MS) {
            load();
        }
        web.onResume();
        if (keepAwake) {
            host.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    void onHide() {
        hiddenAt = SystemClock.elapsedRealtime();
        web.onPause();
        host.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    void onResume() {
        onShow();
    }

    @Override
    void onPause() {
        onHide();
    }

    @Override
    void onConfigChanged() {
        dirty = true;
    }

    @Override
    void onDestroy() {
        io.shutdownNow();
        web.destroy();
    }

    @Override
    View focusTarget() {
        if (error.getVisibility() == View.VISIBLE) {
            return view.findViewById(R.id.empty_primary);
        }
        return url == null ? null : web;
    }

    private void configure() {
        Config.Homepage cfg = host.config.homepage;
        trusted.clear();
        if (!cfg.configured()) {
            url = null;
            web.loadUrl("about:blank");
            showError(
                    R.drawable.ic_tab_dashboard,
                    R.string.empty_dashboard_title,
                    getString(R.string.empty_dashboard),
                    false);
            return;
        }
        keepAwake = cfg.keepAwake;
        url = cfg.url;
        server = Uri.parse(cfg.url).getHost();
        tls = host.tls;
        web.getSettings().setTextZoom(cfg.textZoom);
        load();
    }

    private void load() {
        if (url == null) {
            return;
        }
        failed = false;
        error.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
        web.loadUrl(url);
    }

    private void showError(String message) {
        showError(R.drawable.ic_cloud_off, R.string.dash_error_title, message, true);
    }

    /** The shared empty state inside the card; Try again only when there is something to retry. */
    private void showError(int icon, int title, String message, boolean retry) {
        failed = true;
        loading.setVisibility(View.GONE);
        error.setVisibility(View.VISIBLE);
        empty(icon, title, message);
        TextView second = view.findViewById(R.id.empty_secondary);
        second.setVisibility(retry ? View.VISIBLE : View.GONE);
        second.setText(R.string.dash_retry);
        second.setOnClickListener(v -> load());
        // empty() hides the "content" view, which this layout does not have; the WebView stays
        // under the card.
        if (web.hasFocus()) {
            (retry ? second : view.findViewById(R.id.empty_primary)).requestFocus();
        }
    }

    private final class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri u = request.getUrl();
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            boolean ours =
                    (scheme.equals("http") || scheme.equals("https"))
                            && server != null
                            && server.equalsIgnoreCase(u.getHost());
            if (!ours) {
                Toast.makeText(host, getString(R.string.blocked_link, server), Toast.LENGTH_SHORT)
                        .show();
            }
            return !ours;
        }

        @Override
        public void onPageFinished(WebView view, String finishedUrl) {
            if (failed || url == null) {
                return;
            }
            loading.animate()
                    .alpha(0f)
                    .setDuration(250)
                    .withEndAction(
                            () -> {
                                loading.setVisibility(View.GONE);
                                loading.setAlpha(1f);
                            })
                    .start();
            if (!hinted) {
                hinted = true;
                hint.setVisibility(View.VISIBLE);
                hint.postDelayed(
                        () ->
                                hint.animate()
                                        .alpha(0f)
                                        .setDuration(600)
                                        .withEndAction(() -> hint.setVisibility(View.GONE))
                                        .start(),
                        HINT_MS);
            }
        }

        @Override
        public void onReceivedError(
                WebView view, WebResourceRequest request, WebResourceError err) {
            if (!request.isForMainFrame() || url == null) {
                return;
            }
            showError(
                    getString(
                            R.string.dash_unreachable,
                            server,
                            String.valueOf(err.getDescription())));
        }

        @Override
        public void onReceivedHttpError(
                WebView view, WebResourceRequest request, WebResourceResponse response) {
            if (!request.isForMainFrame() || url == null) {
                return;
            }
            showError(getString(R.string.dash_http, response.getStatusCode(), server));
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError sslError) {
            byte[] leaf = Tls.encoded(sslError.getCertificate());
            if (tls == null || leaf == null) {
                Log.w(TAG, "TLS error " + sslError.getPrimaryError() + " on " + sslError.getUrl());
                handler.cancel();
                showError(getString(R.string.dash_tls, server));
                return;
            }
            String failing = sslError.getUrl();
            String key =
                    Uri.parse(failing).getHost()
                            + " "
                            + Base64.encodeToString(leaf, Base64.NO_WRAP);
            if (trusted.contains(key)) {
                handler.proceed();
                return;
            }
            SSLSocketFactory factory = tls;
            io.execute(
                    () -> {
                        boolean ok = Tls.sameCertificate(failing, factory, leaf);
                        host.runOnUiThread(
                                () -> {
                                    if (host.isDestroyed()) {
                                        return;
                                    }
                                    if (ok) {
                                        trusted.add(key);
                                        handler.proceed();
                                    } else {
                                        Log.w(
                                                TAG,
                                                "certificate for "
                                                        + failing
                                                        + " does not chain to the configured CA");
                                        handler.cancel();
                                        showError(getString(R.string.dash_tls, server));
                                    }
                                });
                    });
        }
    }

    @Override
    boolean onKey(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }
        int code = event.getKeyCode();
        if (url != null
                && (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            load();
            return true;
        }
        if (!web.hasFocus()) {
            return false;
        }
        if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
            scroll(SCROLL_STEP);
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP) {
            // At the top, Up is the way back to the tabs; only the page knows where its top is.
            scroll(-SCROLL_STEP);
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return false;
    }

    /**
     * Scrolls whatever the page scrolls. Homepage does not scroll the window: its content sits in a
     * fixed, full-size container with its own scrollbar (so the background image stays put), and
     * window.scrollBy moves nothing. So the script picks the largest element that can scroll,
     * falling back to the document, and answers "top" when Up finds it already at the top.
     */
    private void scroll(int dy) {
        web.evaluateJavascript(
                SCROLL_JS + "(" + dy + ")",
                answer -> {
                    if (dy < 0 && "\"top\"".equals(answer) && web.hasFocus()) {
                        host.focusTab();
                    }
                });
    }

    private static final String SCROLL_JS =
            "(function(dy){function can(e){var o=getComputedStyle(e).overflowY;return"
                + " (o=='auto'||o=='scroll'||o=='overlay')&&e.scrollHeight>e.clientHeight+1;}var"
                + " t=window.__homelabScroller;if(!t||!t.isConnected||!can(t)){t=null;var area=0;"
                + "document.querySelectorAll('*').forEach(function(e){"
                + "if(can(e)&&e.clientWidth*e.clientHeight>area){t=e;area=e.clientWidth*e.clientHeight;}});"
                + "t=t||document.scrollingElement||document.documentElement;"
                + "window.__homelabScroller=t;}if(dy<0&&t.scrollTop<=0)return"
                + " 'top';t.scrollBy({top:dy,behavior:'smooth'});return 'ok';})";
}
