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
            + "  setInterval(function() {"
            + "    try {"
            + "      var v = findVideo();"
            + "      if (!v) return;"
            + "      if (!notifiedReady) {"
            + "        notifiedReady = true;"
            + "        if (window." + "NAME_PLACEHOLDER" + " && window." + "NAME_PLACEHOLDER" + ".onReady) {"
            + "          window." + "NAME_PLACEHOLDER" + ".onReady();"
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
}
