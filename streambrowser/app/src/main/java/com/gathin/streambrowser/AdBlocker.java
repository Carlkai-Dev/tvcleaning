package com.gathin.streambrowser;

import android.net.Uri;
import android.text.TextUtils;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Blocks common ad / tracker hosts and strips popup / overlay junk via injected JS.
 * Cinegram gets the aggressive path (extra hosts + DOM cleanup).
 */
final class AdBlocker {

    private static final WebResourceResponse EMPTY =
            new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));

    private static final Set<String> BLOCKED_HOSTS = new HashSet<>();
    private static final Set<String> BLOCKED_PATH_HINTS = new HashSet<>();

    static {
        String[] hosts = {
                // major networks
                "doubleclick.net", "googleadservices.com", "googlesyndication.com",
                "googletagmanager.com", "googletagservices.com", "google-analytics.com",
                "pagead2.googlesyndication.com", "adservice.google.com",
                "facebook.net", "connect.facebook.net", "scorecardresearch.com",
                "outbrain.com", "taboola.com", "criteo.com", "criteo.net",
                "adnxs.com", "adsrvr.org", "adform.net", "adsymptotic.com",
                "advertising.com", "adcolony.com", "admob.com", "mopub.com",
                "pubmatic.com", "rubiconproject.com", "openx.net", "casalemedia.com",
                "smartadserver.com", "yieldmo.com", "media.net", "quantserve.com",
                "exoclick.com", "exosrv.com", "juicyads.com", "popads.net",
                "popcash.net", "propellerads.com", "propellerclick.com",
                "trafficjunky.net", "trafficfactory.biz", "adsterra.com",
                "hilltopads.com", "clickadu.com", "mgid.com", "revcontent.com",
                "adskeeper.com", "bidvertiser.com", "adcash.com",
                // pop / redirect / interstitial
                "popunderjs.com", "onclickads.net", "onclickmega.com",
                "adexchangegate.com", "adexchangecloud.com", "adexchangescript.com",
                "tsyndicate.com", "trafficstars.com", "ero-advertising.com",
                "adserver.adtechus.com", "adtech.com", "advertising.com",
                "serving-sys.com", "moatads.com", "amazon-adsystem.com",
                "aaxads.com", "ads.yahoo.com", "ads.twitter.com",
                "static.ads-twitter.com", "ads-twitter.com",
                // video / player ads
                "imasdk.googleapis.com", "fwmrm.net", "stickyadstv.com",
                "spotxchange.com", "spotx.tv", "teads.tv",
                // analytics / fingerprint
                "hotjar.com", "clarity.ms", "fullstory.com", "mixpanel.com",
                "segment.io", "segment.com", "amplitude.com",
                // cinegram-heavy / common stream-site junk
                "histats.com", "statcounter.com", "addthis.com", "sharethis.com",
                "disqus.com", "zergnet.com", "contentabc.com", "adtng.com",
                "stripchat.com", "livejasmin.com", "chaturbate.com",
                "exoclick.com", "realsrv.com", "natsdk.com", "pushanotify.com",
                "pushengage.com", "onesignal.com", "pusher.com",
                "browser-update.org", "cookiebot.com", "cookielaw.org",
                "onetrust.com", "trustarc.com",
                // cloudflare challenge helpers sometimes wrap ads — leave cloudflare.com alone
                "affiliatefuture.com", "clickbank.net", "digidip.net",
                "admicro.vn", "adbro.me", "ad-maven.com", "admaven.com",
                "yandex.ru", "yandexadexchange.net", "mc.yandex.ru",
                "ads.msn.com", "bing.com", "bat.bing.com"
        };
        for (String h : hosts) {
            BLOCKED_HOSTS.add(h.toLowerCase(Locale.US));
        }

        String[] hints = {
                "/ads/", "/ad/", "/advert", "adsense", "adserver", "popunder",
                "pop.js", "popup.js", "banner.js", "prebid", "gpt.js",
                "doubleclick", "pagead", "adsbygoogle", "taboola", "outbrain",
                "propeller", "exoclick", "juicyads", "popads", "popcash",
                "push-notification", "onesignal", "fingerprint", "fp.js"
        };
        for (String h : hints) {
            BLOCKED_PATH_HINTS.add(h.toLowerCase(Locale.US));
        }
    }

    private AdBlocker() {}

    static boolean shouldBlock(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String lower = url.toLowerCase(Locale.US);
        Uri uri;
        try {
            uri = Uri.parse(url);
        } catch (Exception e) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.US);
        if (host.startsWith("www.")) host = host.substring(4);

        if (isBlockedHost(host)) return true;

        String path = uri.getPath();
        String query = uri.getQuery();
        String hay = (path == null ? "" : path) + "?" + (query == null ? "" : query);
        hay = hay.toLowerCase(Locale.US);
        for (String hint : BLOCKED_PATH_HINTS) {
            if (hay.contains(hint) || lower.contains(hint)) return true;
        }
        return false;
    }

    static boolean shouldBlock(WebResourceRequest request) {
        if (request == null || request.getUrl() == null) return false;
        return shouldBlock(request.getUrl().toString());
    }

    static WebResourceResponse emptyResponse() {
        return EMPTY;
    }

    private static boolean isBlockedHost(String host) {
        if (BLOCKED_HOSTS.contains(host)) return true;
        for (String blocked : BLOCKED_HOSTS) {
            if (host.endsWith("." + blocked)) return true;
        }
        return false;
    }

    /** JS that kills popups, overlays, fixed banners, and auto-redirect traps. */
    static String cleanupScript(boolean aggressive) {
        StringBuilder sb = new StringBuilder();
        sb.append("(function(){try{");
        // Kill window.open / alert / confirm spam
        sb.append("window.open=function(){return null;};");
        sb.append("window.alert=function(){};window.confirm=function(){return true;};");
        sb.append("window.prompt=function(){return null;};");
        // Block notification / push permission spam
        sb.append("if(window.Notification){try{Notification.requestPermission=function(){return Promise.resolve('denied');};}catch(e){}}");
        // Stop history.replaceState redirect loops used by some interstitial scripts
        sb.append("document.addEventListener('click',function(e){");
        sb.append("var t=e.target;while(t&&t!==document){");
        sb.append("if(t.tagName==='A'&&t.target==='_blank'){t.target='_self';}");
        sb.append("t=t.parentElement;}},true);");

        // Remove known ad DOM nodes
        sb.append("function scrub(){");
        sb.append("var sels=[");
        sb.append("'iframe[src*=\"doubleclick\"]','iframe[src*=\"ads\"]','iframe[id*=\"google_ads\"]',");
        sb.append("'iframe[src*=\"pop\"]','iframe[src*=\"banner\"]','iframe[src*=\"exoclick\"]',");
        sb.append("'iframe[src*=\"juicy\"]','iframe[src*=\"propeller\"]',");
        sb.append("'[id*=\"ad-\"],[id*=\"ads-\"],[id^=\"ad_\"],[class*=\"ad-banner\"]',");
        sb.append("'[class*=\"adsbox\"],[class*=\"ad-container\"],[class*=\"adcontainer\"]',");
        sb.append("'[class*=\"popup\"],[class*=\"pop-up\"],[id*=\"popup\"],[id*=\"overlay\"]',");
        sb.append("'[class*=\"overlay\"],[class*=\"modal-ad\"],[class*=\"interstitial\"]',");
        sb.append("'div[style*=\"z-index: 999\"],div[style*=\"z-index:999\"],div[style*=\"z-index: 214748\"]',");
        sb.append("'a[href*=\"pop\"]','ins.adsbygoogle','.adsbygoogle'");
        if (aggressive) {
            sb.append(",'[onclick*=\"window.open\"]','[onclick*=\"location\"]',");
            sb.append("'div[style*=\"position: fixed\"][style*=\"inset\"]',");
            sb.append("'div[style*=\"position:fixed\"][style*=\"width:100%\"]',");
            sb.append("'div[style*=\"position: fixed\"][style*=\"width: 100%\"]'");
        }
        sb.append("];");
        sb.append("sels.forEach(function(s){try{document.querySelectorAll(s).forEach(function(n){");
        // Don't remove video players / main content wrappers by mistake when fixed fullscreen
        sb.append("if(n.closest&&(n.closest('video')||n.closest('.jwplayer')||n.closest('#player')||n.closest('.plyr')||n.tagName==='VIDEO'))return;");
        sb.append("n.remove();});}catch(e){}});");
        // Unlock scroll / body overflow locked by overlays
        sb.append("try{document.documentElement.style.overflow='auto';document.body.style.overflow='auto';");
        sb.append("document.body.style.pointerEvents='auto';}catch(e){}");
        sb.append("}");
        sb.append("scrub();");
        sb.append("var mo=new MutationObserver(function(){scrub();});");
        sb.append("mo.observe(document.documentElement,{childList:true,subtree:true});");
        sb.append("setInterval(scrub,").append(aggressive ? "800" : "1500").append(");");
        sb.append("}catch(e){}})();");
        return sb.toString();
    }
}
