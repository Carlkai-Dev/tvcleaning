package com.patilweb.streambrowser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

public class BrowserActivity extends Activity {

    private WebView webView;
    private ProgressBar progress;
    private View menuBar;
    private boolean aggressive;
    private boolean menuVisible;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);

        String url = getIntent().getStringExtra(HomeActivity.EXTRA_URL);
        aggressive = getIntent().getBooleanExtra(HomeActivity.EXTRA_AGGRESSIVE, false);
        if (url == null || url.isEmpty()) {
            finish();
            return;
        }

        webView = findViewById(R.id.webview);
        progress = findViewById(R.id.progress);
        menuBar = findViewById(R.id.menu_bar);

        Button btnBack = findViewById(R.id.btn_back_page);
        Button btnForward = findViewById(R.id.btn_forward);
        Button btnReload = findViewById(R.id.btn_reload);
        Button btnHome = findViewById(R.id.btn_home);

        btnBack.setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
            hideMenu();
        });
        btnForward.setOnClickListener(v -> {
            if (webView.canGoForward()) webView.goForward();
            hideMenu();
        });
        btnReload.setOnClickListener(v -> {
            webView.reload();
            hideMenu();
        });
        btnHome.setOnClickListener(v -> finish());

        setupWebView();
        webView.loadUrl(url);
        webView.requestFocus();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        // Desktop UA so sites serve full players instead of broken mobile layouts on TV.
        s.setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return false;
                String u = request.getUrl().toString();
                if (AdBlocker.shouldBlock(u)) {
                    return true;
                }
                // Block off-site popup-style redirects that aren't http(s)
                String scheme = request.getUrl().getScheme();
                if (scheme != null && !scheme.equals("http") && !scheme.equals("https")) {
                    return true;
                }
                return false;
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
                progress.setVisibility(View.VISIBLE);
                progress.setProgress(5);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                injectCleanup();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                if (newProgress > 40) injectCleanup();
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                Toast.makeText(BrowserActivity.this, R.string.blocked_popup, Toast.LENGTH_SHORT).show();
                return false;
            }
        });
    }

    private void injectCleanup() {
        String js = AdBlocker.cleanupScript(aggressive);
        webView.evaluateJavascript(js, null);
    }

    private void toggleMenu() {
        if (menuVisible) hideMenu();
        else showMenu();
    }

    private void showMenu() {
        menuVisible = true;
        menuBar.setVisibility(View.VISIBLE);
        View first = findViewById(R.id.btn_back_page);
        first.requestFocus();
    }

    private void hideMenu() {
        menuVisible = false;
        menuBar.setVisibility(View.GONE);
        webView.requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (menuVisible) {
                hideMenu();
                return true;
            }
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
                return true;
            }
            finish();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU
                || keyCode == KeyEvent.KEYCODE_INFO
                || keyCode == KeyEvent.KEYCODE_TV_CONTENTS_MENU
                || keyCode == KeyEvent.KEYCODE_GUIDE) {
            toggleMenu();
            return true;
        }
        // D-pad: scroll page when WebView has focus and no in-page focused element handles it.
        if (webView != null && webView.hasFocus() && !menuVisible) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                webView.pageDown(false);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                webView.pageUp(false);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.stopLoading();
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) parent.removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
