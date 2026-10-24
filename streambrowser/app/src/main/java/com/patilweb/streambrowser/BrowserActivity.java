package com.patilweb.streambrowser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

public class BrowserActivity extends Activity {

    private WebView webView;
    private TvCursorView cursor;
    private ProgressBar progress;
    private View menuBar;
    private FrameLayout fullscreenContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private boolean aggressive;
    private boolean menuVisible;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Ask HTML5 / common players to enter fullscreen when playback starts. */
    private static final String AUTO_FULLSCREEN_JS =
            "(function(){"
                    + "if(window.__stvFs)return;window.__stvFs=1;"
                    + "function goFs(el){try{"
                    + "if(!el)return;"
                    + "var r=el.requestFullscreen||el.webkitRequestFullscreen||el.webkitEnterFullscreen"
                    + "||el.mozRequestFullScreen||el.msRequestFullscreen;"
                    + "if(r)r.call(el);"
                    + "}catch(e){}}"
                    + "function wire(v){if(!v||v.__stvWired)return;v.__stvWired=1;"
                    + "v.addEventListener('play',function(){goFs(v);},true);"
                    + "v.addEventListener('playing',function(){goFs(v);},true);"
                    + "}"
                    + "function scan(){document.querySelectorAll('video').forEach(wire);}"
                    + "scan();"
                    + "new MutationObserver(scan).observe(document.documentElement||document.body,"
                    + "{childList:true,subtree:true});"
                    + "})();";

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
        cursor = findViewById(R.id.tv_cursor);
        progress = findViewById(R.id.progress);
        menuBar = findViewById(R.id.menu_bar);
        fullscreenContainer = findViewById(R.id.fullscreen_container);
        cursor.attach(webView);

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
        cursor.requestFocus();
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
                if (AdBlocker.shouldBlock(u)) return true;
                String scheme = request.getUrl().getScheme();
                if (scheme != null && !scheme.equals("http") && !scheme.equals("https")) {
                    return true;
                }
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (AdBlocker.shouldBlock(request)) return AdBlocker.emptyResponse();
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
                injectPageScripts();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                if (newProgress > 40) injectPageScripts();
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                         android.os.Message resultMsg) {
                Toast.makeText(BrowserActivity.this, R.string.blocked_popup, Toast.LENGTH_SHORT).show();
                return false;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                enterFullscreen(view, callback);
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }
        });
    }

    private void injectPageScripts() {
        webView.evaluateJavascript(AdBlocker.cleanupScript(aggressive), null);
        webView.evaluateJavascript(AUTO_FULLSCREEN_JS, null);
    }

    private void enterFullscreen(View view, WebChromeClient.CustomViewCallback callback) {
        if (view == null) return;
        if (customView != null) {
            if (callback != null) callback.onCustomViewHidden();
            return;
        }
        customView = view;
        customViewCallback = callback;
        hideMenu();
        if (cursor != null) cursor.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);
        fullscreenContainer.setVisibility(View.VISIBLE);
        fullscreenContainer.removeAllViews();
        fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fullscreenContainer.setBackgroundColor(Color.BLACK);
        fullscreenContainer.requestFocus();
        applyImmersiveUi(true);
    }

    private void exitFullscreen() {
        if (customView == null) return;
        applyImmersiveUi(false);
        fullscreenContainer.removeAllViews();
        fullscreenContainer.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        if (cursor != null) {
            cursor.setVisibility(View.VISIBLE);
            cursor.requestFocus();
        }
        if (customViewCallback != null) {
            try {
                customViewCallback.onCustomViewHidden();
            } catch (Exception ignored) { }
        }
        customView = null;
        customViewCallback = null;
    }

    private boolean isFullscreen() {
        return customView != null;
    }

    private void applyImmersiveUi(boolean on) {
        View decor = getWindow().getDecorView();
        if (on) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private void toggleMenu() {
        if (isFullscreen()) return;
        if (menuVisible) hideMenu();
        else showMenu();
    }

    private void showMenu() {
        menuVisible = true;
        if (cursor != null) cursor.setVisibility(View.INVISIBLE);
        menuBar.setVisibility(View.VISIBLE);
        View first = findViewById(R.id.btn_back_page);
        first.requestFocus();
    }

    private void hideMenu() {
        menuVisible = false;
        menuBar.setVisibility(View.GONE);
        if (!isFullscreen() && cursor != null) {
            cursor.setVisibility(View.VISIBLE);
            cursor.requestFocus();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isFullscreen()) {
                // Prefer player exit; also tear down our container.
                webView.evaluateJavascript(
                        "(function(){try{if(document.fullscreenElement||document.webkitFullscreenElement)"
                                + "{(document.exitFullscreen||document.webkitExitFullscreen).call(document);}}"
                                + "catch(e){}})();",
                        null);
                exitFullscreen();
                return true;
            }
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
        if (isFullscreen()) applyImmersiveUi(true);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (isFullscreen()) exitFullscreen();
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
