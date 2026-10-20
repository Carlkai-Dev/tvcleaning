# Keep WebView bridge methods used by injected ad-block scripts.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
