package com.joy.englishtube.ui.player;

import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

/**
 * Bridges the YouTube mobile web {@code <video>} element inside a {@link WebView}
 * back to native code so PlayerActivity can:
 *   • Receive periodic {@code currentTime} ticks for subtitle highlighting.
 *   • Seek to a specific second when the user taps a subtitle line.
 *
 * The native side calls {@link #install(WebView)} once after the page has
 * finished loading. The injected JavaScript polls the video at ~4Hz and posts
 * timestamps via {@link #onTime(float)}; the {@link Callback} listener receives
 * them on the main thread.
 *
 * Sprint 2 architecture pivot: we no longer use the YouTube IFrame Player API
 * because too many videos disable embedded playback. Instead we let the
 * WebView play youtube.com natively and overlay our own UI.
 */
public class WebViewPlayerBridge {

    /** Posts on the main thread. */
    public interface Callback {
        @UiThread void onReady();
        @UiThread void onTime(float seconds);

        /**
         * Fired when the WebView's {@code location.href} changes between
         * two YouTube watch URLs without firing a full page load. m.youtube.com
         * is a single-page app, so tapping a related video usually leaves
         * {@code WebViewClient.onPageFinished} silent.
         */
        @UiThread void onLocation(@NonNull String url);

        /**
         * Reports the bottom edge (in CSS pixels relative to the WebView
         * viewport) of the &lt;video&gt; element. Lets the native side anchor
         * the subtitle panel exactly under the video frame regardless of
         * how the YouTube page chrome (header, comments, suggestions)
         * is laid out around it.
         */
        @UiThread void onVideoBottom(float cssPx);
    }

    /** Name exposed to JS — referenced by {@link #JS_INSTALL}. */
    public static final String NAME = "EnglishTubeBridge";

    /**
     * Self-installing JS payload. Idempotent — calling install() multiple
     * times will only attach one polling loop. Falls back gracefully if the
     * &lt;video&gt; element isn't on the page yet (eg. ad pre-roll).
     */
    public static final String JS_INSTALL = ""
            + "(function() {"
            + "  if (window.__EnglishTubeBridgeInstalled) return;"
            + "  window.__EnglishTubeBridgeInstalled = true;"
            + "  function findVideo() {"
            + "    return document.querySelector('video.video-stream')"
            + "        || document.querySelector('video');"
            + "  }"
            + "  var lastTime = -1;"
            + "  var notifiedReady = false;"
            + "  var lastHref = location.href;"
            + "  var lastBottom = -1;"
            + "  if (window." + "NAME_PLACEHOLDER" + " && window." + "NAME_PLACEHOLDER" + ".onLocation) {"
            + "    window." + "NAME_PLACEHOLDER" + ".onLocation(lastHref);"
            + "  }"
            + "  setInterval(function() {"
            + "    try {"
            + "      var href = location.href;"
            + "      if (href !== lastHref) {"
            + "        lastHref = href;"
            + "        notifiedReady = false;"
            + "        lastTime = -1;"
            + "        lastBottom = -1;"
            + "        if (window." + "NAME_PLACEHOLDER" + " && window." + "NAME_PLACEHOLDER" + ".onLocation) {"
            + "          window." + "NAME_PLACEHOLDER" + ".onLocation(href);"
            + "        }"
            + "      }"
            + "      var v = findVideo();"
            + "      if (!v) return;"
            + "      if (!notifiedReady) {"
            + "        notifiedReady = true;"
            + "        if (window." + "NAME_PLACEHOLDER" + " && window." + "NAME_PLACEHOLDER" + ".onReady) {"
            + "          window." + "NAME_PLACEHOLDER" + ".onReady();"
            + "        }"
            + "      }"
            + "      var rect = v.getBoundingClientRect();"
            + "      if (rect && rect.bottom > 0) {"
            + "        var b = rect.bottom;"
            + "        if (Math.abs(b - lastBottom) > 0.5) {"
            + "          lastBottom = b;"
            + "          if (window." + "NAME_PLACEHOLDER" + " && window." + "NAME_PLACEHOLDER" + ".onVideoBottom) {"
            + "            window." + "NAME_PLACEHOLDER" + ".onVideoBottom(b);"
            + "          }"
            + "        }"
            + "      }"
            + "      var t = v.currentTime;"
            + "      if (typeof t === 'number' && !isNaN(t) && t !== lastTime) {"
            + "        lastTime = t;"
            + "        if (window." + "NAME_PLACEHOLDER" + " && window." + "NAME_PLACEHOLDER" + ".onTime) {"
            + "          window." + "NAME_PLACEHOLDER" + ".onTime(t);"
            + "        }"
            + "      }"
            + "    } catch (e) {}"
            + "  }, 250);"
            + "})();";

    private final Callback callback;
    private final Handler main = new Handler(Looper.getMainLooper());

    public WebViewPlayerBridge(@NonNull Callback callback) {
        this.callback = callback;
    }

    /**
     * Installs the bridge onto the given WebView. Must be called from the UI
     * thread, after {@code addJavascriptInterface}. Safe to call repeatedly —
     * the JS guard {@code __EnglishTubeBridgeInstalled} prevents duplicate
     * polling loops.
     */
    @UiThread
    public void install(@NonNull WebView webView) {
        webView.evaluateJavascript(JS_INSTALL.replace("NAME_PLACEHOLDER", NAME), null);
    }

    /**
     * Asks the JS-side video element to seek. {@code seconds} can be a fraction
     * — the YouTube player accepts non-integer seek targets.
     */
    @UiThread
    public static void seekTo(@NonNull WebView webView, double seconds) {
        String js = "(function(){"
                + "var v = document.querySelector('video.video-stream') "
                + "        || document.querySelector('video');"
                + "if (v) { v.currentTime = " + seconds + "; v.play && v.play(); }"
                + "})();";
        webView.evaluateJavascript(js, null);
    }

    // --- @JavascriptInterface methods (called on JS thread) ------------------

    @JavascriptInterface
    @WorkerThread
    public void onReady() {
        main.post(callback::onReady);
    }

    @JavascriptInterface
    @WorkerThread
    public void onTime(float seconds) {
        main.post(() -> callback.onTime(seconds));
    }

    @JavascriptInterface
    @WorkerThread
    public void onLocation(@NonNull String url) {
        // Always copy the string out of the JS thread context before
        // hopping onto the main looper.
        final String captured = url;
        main.post(() -> callback.onLocation(captured));
    }

    @JavascriptInterface
    @WorkerThread
    public void onVideoBottom(float cssPx) {
        main.post(() -> callback.onVideoBottom(cssPx));
    }
}
