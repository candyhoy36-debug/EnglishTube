package com.joy.englishtube.ui.player;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.joy.englishtube.EnglishTubeApp;
import com.joy.englishtube.R;
import com.joy.englishtube.data.BookmarkDao;
import com.joy.englishtube.data.BookmarkEntity;
import com.joy.englishtube.data.SubtitleCacheDao;
import com.joy.englishtube.data.SubtitleCacheEntity;
import com.joy.englishtube.model.SubtitleLine;
import com.joy.englishtube.service.PlayerSyncController;
import com.joy.englishtube.service.SubtitleService;
import com.joy.englishtube.service.TranslationService;
import com.joy.englishtube.service.impl.AutoTranslateService;
import com.joy.englishtube.service.impl.GoogleTranslateService;
import com.joy.englishtube.service.impl.MlKitTranslateService;
import com.joy.englishtube.service.impl.PlayerSyncControllerImpl;
import com.joy.englishtube.service.impl.YouTubeSubtitleService;
import com.joy.englishtube.util.SentenceJoiner;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;

/**
 * Sprint 2: host the YouTube mobile web page in a WebView so we can play
 * any video — including those whose owner has disabled the IFrame embed.
 *
 * Layout split (portrait):
 *   • Top — 16:9 video frame (the WebView).
 *   • Bottom — inline subtitle panel anchored under the video so the user
 *     can read the subtitles without occluding the picture.
 *
 * The FAB toggles the subtitle panel on/off (so the user can fall back to
 * a video-only view). Time sync between the &lt;video&gt; element and the
 * subtitle list is handled by {@link WebViewPlayerBridge}.
 *
 * Sprint 5 will reuse this same WebView to render the bilingual overlay
 * when the user goes fullscreen + landscape.
 */
public class PlayerActivity extends AppCompatActivity
        implements WebViewPlayerBridge.Callback {

    public static final String EXTRA_VIDEO_ID = "extra_video_id";

    private static final String TAG = "PlayerActivity";
    private static final String SUBTITLE_LANG_EN = "en";
    private static final String SUBTITLE_LANG_VI = "vi";
    private static final long ACTIVE_LINE_TICK_MS = 250L; // ~4Hz, NFR-05

    @NonNull
    public static Intent intent(@NonNull Context ctx, @NonNull String videoId) {
        Intent i = new Intent(ctx, PlayerActivity.class);
        i.putExtra(EXTRA_VIDEO_ID, videoId);
        return i;
    }

    @Nullable
    private String videoId;

    private WebView webView;
    private ProgressBar webProgress;
    private FloatingActionButton fabSubtitles;

    // Auxiliary action bar buttons (top of activity, replaces the
    // old MaterialToolbar).
    private TextView btnCombineLines;
    private TextView btnLoopLine;
    private TextView btnBookmarkLine;
    private TextView btnLangMode;
    private TextView btnEnterFullscreen;

    // Inline split-layout subtitle panel (Sprint 2 UI revamp — the
    // panel sits below the video instead of overlaying it via a
    // BottomSheetDialog).
    private View subtitlePanel;
    private SubtitleAdapter adapter;
    private LinearLayoutManager layoutManager;
    private ProgressBar subtitleProgress;
    private View bannerNoSubtitle;
    private TextView bannerMessage;
    private Button btnRetryFetch;

    // Sprint 5 fullscreen overlay state. The container hosts the WebView's
    // HTML5-fullscreen customView; the overlay shows the active EN/VI line
    // on top of the video while fullscreen is active.
    private FrameLayout fullscreenContainer;
    private LinearLayout subtitleOverlay;
    private TextView tvOverlayEn;
    private TextView tvOverlayVi;
    @Nullable
    private View customView;
    @Nullable
    private WebChromeClient.CustomViewCallback customViewCallback;
    // Native "app fullscreen" state — driven by device rotation.
    // Independent of YouTube's HTML5 fullscreen (which may or may not
    // succeed depending on the WebView's user-gesture policy). When this
    // is true we have hidden the action bar / FAB / subtitle panel /
    // system bars and revealed the bilingual overlay.
    private boolean isFullscreen = false;
    // Sub-views we toggle on rotate — cached in onCreate to avoid
    // findViewById on every config change.
    private View playerActionBar;
    private int savedSubtitlePanelVisibility = View.GONE;
    // Sprint 5 follow-up: tap-to-play/pause UI in fullscreen.
    @Nullable
    private FrameLayout fullscreenTapArea;
    @Nullable
    private android.widget.ImageView btnFullscreenPlayPause;
    @Nullable
    private android.widget.ImageView btnExitFullscreen;
    private boolean videoPlaying = true;
    private final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    // --- Auxiliary feature state ---
    private int activeLineIndex = -1;
    private boolean loopLineEnabled = false;
    private long loopStartMs = -1L;
    private long loopEndMs = -1L;
    private LangMode langMode = LangMode.EN;
    // Sprint 4: when true, the panel + sync controller operate on a
    // sentence-grouped view of {@link #latestLines} instead of the raw cues.
    private boolean combineLinesEnabled = false;

    private enum LangMode { EN, VI, BOTH }

    private final PlayerSyncController syncController = new PlayerSyncControllerImpl();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final OkHttpClient http = new OkHttpClient();
    // Auto: Google online (fast, batched) → ML Kit on-device fallback. The
    // service is stateless so a single instance is reused across videos.
    private final TranslationService translationService =
            new AutoTranslateService(
                    new GoogleTranslateService(http),
                    new MlKitTranslateService());
    private long lastTickMs = 0L;

    private enum SubtitleState { LOADING, READY, NO_SUBTITLE, FETCH_FAILED }
    private SubtitleState state = SubtitleState.LOADING;
    private List<SubtitleLine> latestLines = Collections.emptyList();
    // Sentence-grouped projection of {@link #latestLines}. Rebuilt whenever
    // the cue list or its translations change.
    private List<SubtitleLine> sentenceLines = Collections.emptyList();

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        videoId = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        if (videoId == null) {
            Toast.makeText(this, R.string.error_no_youtube_app, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        webView = findViewById(R.id.webview_player);
        webProgress = findViewById(R.id.web_progress);
        fabSubtitles = findViewById(R.id.fab_subtitles);
        fullscreenContainer = findViewById(R.id.fullscreen_container);
        subtitleOverlay = findViewById(R.id.subtitle_overlay);
        tvOverlayEn = findViewById(R.id.tv_overlay_en);
        tvOverlayVi = findViewById(R.id.tv_overlay_vi);
        playerActionBar = findViewById(R.id.player_action_bar);
        fullscreenTapArea = findViewById(R.id.fullscreen_tap_area);
        btnFullscreenPlayPause = findViewById(R.id.btn_fullscreen_play_pause);
        btnExitFullscreen = findViewById(R.id.btn_exit_fullscreen);
        if (fullscreenTapArea != null) {
            fullscreenTapArea.setOnClickListener(v -> onFullscreenTap());
        }
        if (btnFullscreenPlayPause != null) {
            btnFullscreenPlayPause.setOnClickListener(v -> onFullscreenTap());
        }
        if (btnExitFullscreen != null) {
            // Stop the click from propagating up to the tap area, which
            // would otherwise toggle play/pause at the same time.
            btnExitFullscreen.setOnClickListener(v -> requestFullscreenOrientation(false));
        }

        // Use the modern back-press dispatcher so we can intercept Back to
        // exit fullscreen first (instead of letting the activity finish).
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isFullscreen) {
                    exitAppFullscreen();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        // If the user launched the activity already in landscape (e.g. the
        // device is sideways from the start), enter fullscreen immediately.
        if (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            // Defer so initial layout has happened first — otherwise the
            // overlay's bringToFront races with ConstraintLayout's pass.
            getWindow().getDecorView().post(this::enterAppFullscreen);
        }

        bindActionBar();
        bindSubtitlePanel();
        configureWebView();
        webView.loadUrl("https://m.youtube.com/watch?v=" + videoId);

        fabSubtitles.setOnClickListener(v -> toggleSubtitlePanel());

        syncController.setListener(this::onActiveLineChanged);
        applyStateToSheet();
        loadSubtitlesAsync(videoId);
    }

    /**
     * Wire up the four buttons of the auxiliary action bar that sits at the
     * top of the activity (replacing the old toolbar). Loop and Bookmark
     * have real behaviour wired in this sprint; Combine and Lang are UI
     * stubs that announce when the underlying feature lands.
     */
    private void bindActionBar() {
        btnCombineLines = findViewById(R.id.btn_combine_lines);
        btnLoopLine = findViewById(R.id.btn_loop_line);
        btnBookmarkLine = findViewById(R.id.btn_bookmark_line);
        btnLangMode = findViewById(R.id.btn_lang_mode);
        btnEnterFullscreen = findViewById(R.id.btn_enter_fullscreen);

        btnCombineLines.setOnClickListener(v -> toggleCombineLines());

        btnLoopLine.setOnClickListener(v -> toggleLoopLine());

        btnBookmarkLine.setOnClickListener(v -> bookmarkActiveLine());

        btnLangMode.setOnClickListener(v -> cycleLangMode());

        btnEnterFullscreen.setOnClickListener(v -> requestFullscreenOrientation(true));
        applyLangMode();
    }

    /**
     * Wire up the inline subtitle panel that lives directly under the video
     * frame. Replaces the old BottomSheetDialog flow — see the activity
     * layout for the visual split.
     */
    private void bindSubtitlePanel() {
        subtitlePanel = findViewById(R.id.subtitle_panel);
        subtitleProgress = findViewById(R.id.subtitle_progress);
        bannerNoSubtitle = findViewById(R.id.banner_no_subtitle);
        bannerMessage = findViewById(R.id.tv_banner_message);
        btnRetryFetch = findViewById(R.id.btn_retry_fetch);

        RecyclerView rv = findViewById(R.id.recycler_subtitles);
        adapter = new SubtitleAdapter();
        layoutManager = new LinearLayoutManager(this);
        rv.setLayoutManager(layoutManager);
        rv.setAdapter(adapter);
        adapter.setOnLineClickListener((position, line) -> {
            // Tapping a line seeks the video AND — if loop-line is on —
            // moves the loop scope to the newly tapped line so the user
            // can re-anchor the loop without first turning it off.
            WebViewPlayerBridge.seekTo(webView, line.startMs / 1000.0);
            if (loopLineEnabled) {
                loopStartMs = line.startMs;
                loopEndMs = line.endMs;
            }
        });
        adapter.setOnWordLongPressListener(this::openDictionarySheet);

        btnRetryFetch.setOnClickListener(v -> {
            state = SubtitleState.LOADING;
            applyStateToSheet();
            if (videoId != null) loadSubtitlesAsync(videoId);
        });

        // Stub buttons in the subtitle-panel action row. Real implementations
        // land in later sprints — for now each just toasts the planned ETA so
        // the user sees the row reacts.
        findViewById(R.id.btn_search_subtitle).setOnClickListener(v ->
                Toast.makeText(this, R.string.search_subtitle_not_yet,
                        Toast.LENGTH_SHORT).show());
        findViewById(R.id.btn_playback_speed).setOnClickListener(v ->
                Toast.makeText(this, R.string.playback_speed_not_yet,
                        Toast.LENGTH_SHORT).show());
        findViewById(R.id.btn_text_size).setOnClickListener(v ->
                Toast.makeText(this, R.string.text_size_not_yet,
                        Toast.LENGTH_SHORT).show());
        findViewById(R.id.btn_download_srt).setOnClickListener(v ->
                Toast.makeText(this, R.string.download_srt_not_yet,
                        Toast.LENGTH_SHORT).show());
    }

    /** Toggle the inline subtitle panel — user wants to focus on the video. */
    private void toggleSubtitlePanel() {
        if (subtitlePanel == null) return;
        boolean nowVisible = subtitlePanel.getVisibility() != View.VISIBLE;
        subtitlePanel.setVisibility(nowVisible ? View.VISIBLE : View.GONE);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);

        // Native ↔ JS bridge. The interface name MUST match
        // WebViewPlayerBridge.NAME used in the injected JS payload.
        webView.addJavascriptInterface(new WebViewPlayerBridge(this), WebViewPlayerBridge.NAME);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                webProgress.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
                webProgress.setProgress(newProgress);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                mountCustomView(view, callback);
            }

            @Override
            public void onHideCustomView() {
                unmountCustomView();
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                // Stay on m.youtube.com for the player WebView; let everything load.
                return false;
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap favicon) {
                webProgress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                webProgress.setVisibility(View.GONE);
                // Re-install the bridge after every navigation so YouTube's
                // SPA transitions (auto-play next, redirects to login wall, …)
                // still get hooked.
                new WebViewPlayerBridge(PlayerActivity.this).install(v);
                // Sprint 5: hide YT's own fullscreen button. We drive
                // fullscreen from device rotation, and YT's button puts
                // the page into a weird half-fullscreen state where
                // the description and related videos still scroll
                // underneath our overlay. Clean fix is to remove the
                // button so users can't trigger that path.
                hideYouTubeFullscreenButton(v);
                // If the user tapped a different video inside the WebView
                // (related video, autoplay queue, search result), the URL
                // carries a new ?v=ID. Re-fetch subtitles so the bottom
                // sheet matches the video that's actually playing.
                handleNavigation(url);
            }
        });
    }

    /**
     * Inject (idempotently) a stylesheet that 1) hides YT's fullscreen
     * button so users can't trigger its half-broken fullscreen path, and
     * 2) defines a {@code html.__etube_fs} mask that hides everything
     * outside the video player when we toggle that class on. Both rules
     * are kept alive across YT's SPA navigations via a MutationObserver.
     */
    private void hideYouTubeFullscreenButton(@NonNull WebView v) {
        v.evaluateJavascript(
                "(function(){"
                        + "  var fsBtn = '.fullscreen-icon, button.fullscreen-icon,'"
                        + "          + ' .ytp-fullscreen-button,'"
                        + "          + ' button[aria-label*=\"ull screen\"],'"
                        + "          + ' button[aria-label*=\"ull-screen\"],'"
                        + "          + ' button[aria-label*=\"o\\u00e0n m\\u00e0n\"]';"
                        + "  var mask = '.mobile-topbar-header, ytm-mobile-topbar-renderer,'"
                        + "          + ' header.bond-app-bar, .app-bar-renderer,'"
                        + "          + ' ytm-pivot-bar-renderer,'"
                        + "          + ' ytm-watch-metadata-section-renderer,'"
                        + "          + ' ytm-engagement-panel-section-list-renderer,'"
                        + "          + ' ytm-comment-section-renderer,'"
                        + "          + ' ytm-item-section-renderer,'"
                        + "          + ' ytm-related-content-renderer,'"
                        + "          + ' ytm-shorts-shelf-renderer,'"
                        + "          + ' ytm-rich-shelf-renderer,'"
                        + "          + ' ytm-action-bar,'"
                        + "          + ' ytm-watch-info-text,'"
                        + "          + ' ytm-watch-channel-text,'"
                        + "          + ' ytm-promoted-content-renderer,'"
                        + "          + ' .player-info-section, .description-section,'"
                        + "          + ' .related-section'"
                        + "          ;"
                        + "  var stretch = 'ytm-player, ytm-player-container, #player, #player-container,'"
                        + "          + ' .player-container, ytm-watch-player, ytm-watch-flexy,'"
                        + "          + ' #movie_player, .html5-video-container';"
                        + "  var stretchCss = stretch.split(',').map(function(s){"
                        + "    return 'html.__etube_fs ' + s.trim();"
                        + "  }).join(', ') + '{position:fixed !important;top:0 !important;left:0 !important;width:100vw !important;height:100vh !important;max-width:none !important;max-height:none !important;margin:0 !important;padding:0 !important;z-index:1 !important;}';"
                        + "  var css = fsBtn + '{display:none !important;visibility:hidden !important;pointer-events:none !important;}'"
                        + "          + 'html.__etube_fs, html.__etube_fs body{overflow:hidden !important;height:100vh !important;width:100vw !important;margin:0 !important;padding:0 !important;background:#000 !important;}'"
                        + "          + 'html.__etube_fs ' + mask.split(',').join(', html.__etube_fs ') + '{display:none !important;}'"
                        + "          + stretchCss"
                        + "          + 'html.__etube_fs video, html.__etube_fs video.html5-main-video, html.__etube_fs video.video-stream{position:fixed !important;top:0 !important;left:0 !important;right:0 !important;bottom:0 !important;width:100vw !important;height:100vh !important;max-width:none !important;max-height:none !important;margin:0 !important;padding:0 !important;transform:none !important;object-fit:contain !important;background:#000 !important;z-index:1 !important;}';"
                        + "  function inject(){"
                        + "    if (!document.head) return;"
                        + "    if (document.getElementById('__etube_styles')) return;"
                        + "    var s = document.createElement('style');"
                        + "    s.id = '__etube_styles';"
                        + "    s.textContent = css;"
                        + "    document.head.appendChild(s);"
                        + "  }"
                        + "  inject();"
                        + "  if (!window.__etubeMo) {"
                        + "    window.__etubeMo = new MutationObserver(inject);"
                        + "    window.__etubeMo.observe(document.documentElement, {childList:true, subtree:true});"
                        + "  }"
                        + "})();",
                null);
    }

    /**
     * Add / remove the {@code __etube_fs} class on the YT page's root
     * element so the injected stylesheet hides everything outside the
     * video player while we are in app-fullscreen.
     */
    private void setPlayerMask(boolean enabled) {
        if (webView == null) return;
        webView.evaluateJavascript(
                "(function(){"
                        + "  var c = document.documentElement;"
                        + "  if (!c) return;"
                        + "  if (" + (enabled ? "true" : "false") + ") {"
                        + "    c.classList.add('__etube_fs');"
                        + "    window.scrollTo(0, 0);"
                        + "  } else {"
                        + "    c.classList.remove('__etube_fs');"
                        + "  }"
                        + "})();",
                null);
    }

    /**
     * Detect in-WebView navigation to a different video and reload
     * subtitles. Triggered from both onPageFinished (initial + reloads) and
     * the polling JS bridge (SPA transitions on m.youtube.com that don't fire
     * a full page load).
     */
    private void resetAuxiliaryStateForNewVideo() {
        activeLineIndex = -1;
        loopLineEnabled = false;
        loopStartMs = -1L;
        loopEndMs = -1L;
        if (btnLoopLine != null) btnLoopLine.setSelected(false);
        sentenceLines = Collections.emptyList();
    }

    private void handleNavigation(@Nullable String url) {
        String newId = extractVideoId(url);
        if (newId == null) return;
        if (newId.equals(videoId)) return;

        Log.d(TAG, "WebView navigated videoId " + videoId + " -> " + newId);
        videoId = newId;
        latestLines = Collections.emptyList();
        state = SubtitleState.LOADING;
        syncController.attach(Collections.emptyList());
        resetAuxiliaryStateForNewVideo();
        applyStateToSheet();
        loadSubtitlesAsync(newId);
    }

    @Nullable
    private static String extractVideoId(@Nullable String url) {
        if (url == null || url.isEmpty()) return null;
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null) return null;
            if (!host.contains("youtube.com") && !host.equals("youtu.be")) {
                return null;
            }
            // Standard watch URL: youtube.com/watch?v=ID
            String v = uri.getQueryParameter("v");
            if (v != null && !v.isEmpty()) return v;
            // Short link: youtu.be/ID
            if ("youtu.be".equals(host)) {
                String path = uri.getPath();
                if (path != null && path.length() > 1) {
                    return path.substring(1);
                }
            }
            // Shorts URL: youtube.com/shorts/ID
            String path = uri.getPath();
            if (path != null && path.startsWith("/shorts/")) {
                String tail = path.substring("/shorts/".length());
                int slash = tail.indexOf('/');
                return slash > 0 ? tail.substring(0, slash) : tail;
            }
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    // --- Inline subtitle panel state machine --------------------------------

    /** Pushes the current {@link #state} onto the inline panel views. */
    private void applyStateToSheet() {
        if (adapter == null || subtitleProgress == null
                || bannerNoSubtitle == null || bannerMessage == null
                || btnRetryFetch == null) {
            return;
        }
        switch (state) {
            case LOADING:
                subtitleProgress.setVisibility(View.VISIBLE);
                bannerNoSubtitle.setVisibility(View.GONE);
                btnRetryFetch.setVisibility(View.GONE);
                adapter.submit(Collections.emptyList());
                break;
            case READY:
                subtitleProgress.setVisibility(View.GONE);
                bannerNoSubtitle.setVisibility(View.GONE);
                btnRetryFetch.setVisibility(View.GONE);
                adapter.submit(activeLines());
                break;
            case NO_SUBTITLE:
                subtitleProgress.setVisibility(View.GONE);
                bannerMessage.setText(R.string.no_subtitle_banner);
                bannerNoSubtitle.setVisibility(View.VISIBLE);
                btnRetryFetch.setVisibility(View.GONE);
                adapter.submit(Collections.emptyList());
                break;
            case FETCH_FAILED:
                subtitleProgress.setVisibility(View.GONE);
                bannerMessage.setText(R.string.fetch_failed_message);
                bannerNoSubtitle.setVisibility(View.VISIBLE);
                btnRetryFetch.setVisibility(View.VISIBLE);
                adapter.submit(Collections.emptyList());
                break;
        }
    }

    // --- WebViewPlayerBridge.Callback ---------------------------------------

    @Override
    public void onReady() {
        Log.d(TAG, "WebView <video> element ready");
    }

    @Override
    public void onTime(float seconds) {
        long currentMs = (long) (seconds * 1000f);

        // Loop-line: if armed and we've passed the end of the looped cue,
        // seek back to its start. Done before the throttle so we don't
        // miss a window.
        if (loopLineEnabled && loopEndMs > 0 && currentMs > loopEndMs) {
            WebViewPlayerBridge.seekTo(webView, loopStartMs / 1000.0);
            return;
        }

        long nowMs = System.currentTimeMillis();
        if (nowMs - lastTickMs < ACTIVE_LINE_TICK_MS) return;
        lastTickMs = nowMs;
        try {
            syncController.onTick(currentMs);
        } catch (RuntimeException e) {
            Log.w(TAG, "sync onTick failed", e);
        }
    }

    @Override
    public void onLocation(@NonNull String url) {
        handleNavigation(url);
    }

    @Override
    public void onVideoBottom(float cssPx) {
        if (subtitlePanel == null) return;
        // CSS pixels in WebView correspond 1:1 to dp, so multiply by the
        // display density to get layout pixels for the constraint margin.
        float density = getResources().getDisplayMetrics().density;
        int marginPx = Math.max(0, Math.round(cssPx * density));
        ViewGroup.MarginLayoutParams lp =
                (ViewGroup.MarginLayoutParams) subtitlePanel.getLayoutParams();
        if (lp.topMargin != marginPx) {
            lp.topMargin = marginPx;
            subtitlePanel.setLayoutParams(lp);
        }
    }

    @Override
    public void onPlayState(boolean playing) {
        videoPlaying = playing;
        if (btnFullscreenPlayPause == null) return;
        btnFullscreenPlayPause.setImageResource(playing
                ? R.drawable.ic_pause_white
                : R.drawable.ic_play_white);
        if (!isFullscreen) return;
        if (!playing) {
            // While paused, keep the icon visible so the user can find
            // it without tapping the screen first.
            showFullscreenPlayPause(false);
        } else {
            scheduleHideFullscreenPlayPause();
        }
    }

    /** Reveal the fullscreen play/pause button; auto-hide unless paused. */
    private void showFullscreenPlayPause(boolean autoHide) {
        if (btnFullscreenPlayPause == null) return;
        btnFullscreenPlayPause.setVisibility(View.VISIBLE);
        btnFullscreenPlayPause.bringToFront();
        if (autoHide) scheduleHideFullscreenPlayPause();
        else mainHandler.removeCallbacks(hideFullscreenPlayPause);
    }

    private void scheduleHideFullscreenPlayPause() {
        if (btnFullscreenPlayPause == null) return;
        mainHandler.removeCallbacks(hideFullscreenPlayPause);
        // Only auto-hide while playing \u2014 if paused, the icon stays put
        // so the user knows they can resume.
        if (videoPlaying) {
            mainHandler.postDelayed(hideFullscreenPlayPause, 1800L);
        }
    }

    private final Runnable hideFullscreenPlayPause = () -> {
        if (btnFullscreenPlayPause != null && videoPlaying) {
            btnFullscreenPlayPause.setVisibility(View.GONE);
        }
    };

    /**
     * Tap-to-toggle handler for fullscreen mode. Each tap on the video
     * area flips play/pause on the underlying YT &lt;video&gt; element
     * and reveals the central play/pause button briefly.
     */
    private void onFullscreenTap() {
        if (!isFullscreen || webView == null) return;
        WebViewPlayerBridge.togglePlay(webView);
        // Optimistic flip \u2014 the bridge poll will correct us within ~250ms.
        videoPlaying = !videoPlaying;
        if (btnFullscreenPlayPause != null) {
            btnFullscreenPlayPause.setImageResource(videoPlaying
                    ? R.drawable.ic_pause_white
                    : R.drawable.ic_play_white);
        }
        showFullscreenPlayPause(videoPlaying);
    }

    private void onActiveLineChanged(int newIndex) {
        activeLineIndex = newIndex;
        if (adapter == null || layoutManager == null) return;
        adapter.setActiveIndex(newIndex);
        // Sprint 5: keep the fullscreen overlay text in sync. Cheap no-op
        // when overlay is hidden, so we don't gate it on isFullscreen.
        updateOverlayText();
        if (newIndex < 0) return;
        if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;

        // Pin the active line to the top of the panel so the user gets a
        // preview area below for the upcoming cues.
        TopSmoothScroller scroller = new TopSmoothScroller(this);
        scroller.setTargetPosition(newIndex);
        layoutManager.startSmoothScroll(scroller);
    }

    // --- Auxiliary action bar handlers --------------------------------------

    private void toggleLoopLine() {
        if (!loopLineEnabled) {
            SubtitleLine line = currentActiveLine();
            if (line == null) {
                Toast.makeText(this, R.string.loop_on_no_active, Toast.LENGTH_SHORT).show();
                return;
            }
            loopStartMs = line.startMs;
            loopEndMs = line.endMs;
            loopLineEnabled = true;
        } else {
            loopLineEnabled = false;
            loopStartMs = -1L;
            loopEndMs = -1L;
        }
        if (btnLoopLine != null) btnLoopLine.setSelected(loopLineEnabled);
    }

    private void bookmarkActiveLine() {
        SubtitleLine line = currentActiveLine();
        if (line == null || videoId == null) {
            Toast.makeText(this, R.string.bookmark_no_active, Toast.LENGTH_SHORT).show();
            return;
        }
        BookmarkEntity entity = new BookmarkEntity();
        entity.videoId = videoId;
        entity.startMs = line.startMs;
        entity.endMs = line.endMs;
        entity.textEn = line.textEn != null ? line.textEn : "";
        entity.createdAt = System.currentTimeMillis();
        final String capturedVideo = videoId;
        io.execute(() -> {
            try {
                BookmarkDao dao = EnglishTubeApp.get().getDatabase().bookmarkDao();
                dao.insert(entity);
                runOnUiThread(() -> Toast.makeText(this, R.string.bookmark_saved,
                        Toast.LENGTH_SHORT).show());
            } catch (RuntimeException e) {
                Log.w(TAG, "bookmark insert failed for " + capturedVideo, e);
            }
        });
    }

    private void cycleLangMode() {
        switch (langMode) {
            case EN:
                langMode = LangMode.VI;
                break;
            case VI:
                langMode = LangMode.BOTH;
                break;
            case BOTH:
            default:
                langMode = LangMode.EN;
                break;
        }
        applyLangMode();
        // Translation is already running (or done) from applyLines();
        // toggling the mode just affects rendering. If translation is still
        // mid-flight the adapter falls back to EN per cue, so no toast.
    }

    private void applyLangMode() {
        if (btnLangMode == null) return;
        int labelRes;
        SubtitleAdapter.LangMode adapterMode;
        switch (langMode) {
            case VI:
                labelRes = R.string.lang_mode_vi;
                adapterMode = SubtitleAdapter.LangMode.VI;
                break;
            case BOTH:
                labelRes = R.string.lang_mode_both;
                adapterMode = SubtitleAdapter.LangMode.BOTH;
                break;
            case EN:
            default:
                labelRes = R.string.lang_mode_en;
                adapterMode = SubtitleAdapter.LangMode.EN;
                break;
        }
        btnLangMode.setText(labelRes);
        btnLangMode.setSelected(langMode != LangMode.EN);
        if (adapter != null) adapter.setLangMode(adapterMode);
        // Overlay row visibilities track LangMode too — EN-only hides the
        // VI line, VI-only hides the EN line, BOTH stacks them.
        updateOverlayText();
    }

    @Nullable
    private SubtitleLine currentActiveLine() {
        List<SubtitleLine> list = activeLines();
        if (activeLineIndex < 0 || activeLineIndex >= list.size()) return null;
        return list.get(activeLineIndex);
    }

    /** Whichever projection of the subtitle track is currently being shown. */
    @NonNull
    private List<SubtitleLine> activeLines() {
        return combineLinesEnabled ? sentenceLines : latestLines;
    }

    /**
     * Sprint 4: toggle between cue-level and sentence-level views. The active
     * highlight is translated across modes so the user keeps their place,
     * and the loop scope is cancelled because it refers to a specific line
     * in the previous projection.
     */
    private void toggleCombineLines() {
        int prevActive = activeLineIndex;
        boolean wasCombineEnabled = combineLinesEnabled;

        combineLinesEnabled = !combineLinesEnabled;
        if (btnCombineLines != null) btnCombineLines.setSelected(combineLinesEnabled);

        loopLineEnabled = false;
        loopStartMs = -1L;
        loopEndMs = -1L;
        if (btnLoopLine != null) btnLoopLine.setSelected(false);

        int newActive;
        if (!wasCombineEnabled) {
            newActive = SentenceJoiner.sentenceIndexForCue(latestLines, prevActive);
        } else {
            newActive = SentenceJoiner.firstCueIndexForSentence(latestLines, prevActive);
        }

        List<SubtitleLine> next = activeLines();
        syncController.attach(next);
        activeLineIndex = newActive;
        if (adapter != null) {
            adapter.submit(next);
            adapter.setActiveIndex(newActive);
        }
        if (newActive >= 0 && layoutManager != null
                && getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            TopSmoothScroller scroller = new TopSmoothScroller(this);
            scroller.setTargetPosition(newActive);
            layoutManager.startSmoothScroll(scroller);
        }
    }

    // --- Sprint 5 fullscreen overlay ----------------------------------------

    /**
     * Called from {@link WebChromeClient#onShowCustomView} when YouTube's
     * mobile page enters HTML5 fullscreen on the &lt;video&gt; element. We
     * mount the supplied native view over the player chrome, force
     * landscape, and reveal the bilingual overlay so the user can keep
     * reading subtitles while the YouTube UI takes over.
     */
    /**
     * Native app-fullscreen flow (Sprint 5): hides the action bar / FAB /
     * subtitle panel / system bars and shows the bilingual overlay.
     * Triggered by device rotation — we don't depend on YouTube's HTML5
     * fullscreen API succeeding (the WebView often blocks
     * {@code requestFullscreen()} for lack of a user gesture). We still
     * fire the JS request so that, when it does succeed, YouTube's own
     * fullscreen video chrome takes over inside the WebView.
     */
    private void enterAppFullscreen() {
        if (isFullscreen) return;
        isFullscreen = true;

        if (playerActionBar != null) playerActionBar.setVisibility(View.GONE);
        if (webProgress != null) webProgress.setVisibility(View.GONE);
        if (fabSubtitles != null) fabSubtitles.setVisibility(View.GONE);
        if (subtitlePanel != null) {
            savedSubtitlePanelVisibility = subtitlePanel.getVisibility();
            subtitlePanel.setVisibility(View.GONE);
        }

        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        WindowInsetsControllerCompat ctrl =
                WindowCompat.getInsetsController(window, window.getDecorView());
        ctrl.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        ctrl.hide(WindowInsetsCompat.Type.systemBars());
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        subtitleOverlay.setVisibility(View.VISIBLE);
        subtitleOverlay.bringToFront();
        updateOverlayText();

        if (fullscreenTapArea != null) {
            fullscreenTapArea.setVisibility(View.VISIBLE);
            fullscreenTapArea.bringToFront();
            // Force a high elevation so the WebView's hardware video
            // surface (which is at z=0) doesn't punch through and hide
            // these overlays. Without this, ImageView children drawn
            // "above" the WebView in XML are still occluded by the
            // SurfaceView-backed video.
            fullscreenTapArea.setElevation(12f);
        }
        if (btnFullscreenPlayPause != null) {
            btnFullscreenPlayPause.setElevation(16f);
        }
        if (btnExitFullscreen != null) {
            btnExitFullscreen.setVisibility(View.VISIBLE);
            btnExitFullscreen.setElevation(16f);
        }
        // Reveal the play/pause button briefly on entry so the user
        // discovers it; auto-fades while playing, persists when paused.
        showFullscreenPlayPause(videoPlaying);
        // Re-stack the subtitle overlay above the tap area so it stays
        // on top of any other transient UI.
        subtitleOverlay.setElevation(14f);
        subtitleOverlay.bringToFront();

        // Hide the rest of the YT mobile page (header, description,
        // comments, related) so it doesn't bleed in behind the overlay
        // when the user has scrolled or YT's SPA has rerendered.
        if (webView != null) webView.scrollTo(0, 0);
        setPlayerMask(true);

        // Best-effort: ask YouTube to enter HTML5 fullscreen on its <video>
        // element. If it succeeds, mountCustomView() takes over the
        // fullscreen_container; if not, we still have the app-level
        // fullscreen with overlay above.
        requestVideoFullscreen();
    }

    private void exitAppFullscreen() {
        if (!isFullscreen) return;
        isFullscreen = false;

        // Tear down YT customView first so it doesn't briefly flash on
        // top of the restored chrome.
        if (customView != null) unmountCustomView();
        else exitVideoFullscreen();

        // Restore the YT mobile page (description, comments, ...).
        setPlayerMask(false);

        if (playerActionBar != null) playerActionBar.setVisibility(View.VISIBLE);
        if (fabSubtitles != null) fabSubtitles.setVisibility(View.VISIBLE);
        if (subtitlePanel != null) {
            subtitlePanel.setVisibility(savedSubtitlePanelVisibility);
        }

        subtitleOverlay.setVisibility(View.GONE);
        if (fullscreenTapArea != null) fullscreenTapArea.setVisibility(View.GONE);
        if (btnFullscreenPlayPause != null) btnFullscreenPlayPause.setVisibility(View.GONE);
        if (btnExitFullscreen != null) btnExitFullscreen.setVisibility(View.GONE);
        mainHandler.removeCallbacks(hideFullscreenPlayPause);

        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowCompat.setDecorFitsSystemWindows(window, true);
        WindowInsetsControllerCompat ctrl =
                WindowCompat.getInsetsController(window, window.getDecorView());
        ctrl.show(WindowInsetsCompat.Type.systemBars());
    }

    /**
     * Mounts the YouTube-supplied native view on top of the app chrome
     * when YT successfully enters HTML5 fullscreen on the &lt;video&gt;
     * element. Mounting only — the system-bar hiding and overlay reveal
     * happen in {@link #enterAppFullscreen} which is the source of truth
     * for fullscreen state.
     */
    private void mountCustomView(@NonNull View view, @NonNull WebChromeClient.CustomViewCallback callback) {
        if (customView != null) {
            callback.onCustomViewHidden();
            return;
        }
        customView = view;
        customViewCallback = callback;

        fullscreenContainer.removeAllViews();
        fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        fullscreenContainer.setVisibility(View.VISIBLE);
        fullscreenContainer.bringToFront();
        // Keep the overlay above the customView so the user can still
        // read EN/VI subtitles over the YT video.
        if (isFullscreen) {
            subtitleOverlay.bringToFront();
        } else {
            // YT entered fullscreen on its own (user tapped the button).
            // Mirror app fullscreen so chrome is hidden and overlay is up.
            enterAppFullscreen();
            subtitleOverlay.bringToFront();
        }
    }

    private void unmountCustomView() {
        if (customView == null) return;
        fullscreenContainer.setVisibility(View.GONE);
        fullscreenContainer.removeAllViews();

        if (customViewCallback != null) {
            try {
                customViewCallback.onCustomViewHidden();
            } catch (RuntimeException e) {
                Log.w(TAG, "customView onHidden threw", e);
            }
        }
        customView = null;
        customViewCallback = null;
    }

    /**
     * Sprint 5 (rotate-driven): when the device rotates we ask the
     * YouTube page to enter / exit HTML5 fullscreen on the &lt;video&gt;
     * element. Going through requestFullscreen() routes back into our
     * {@link WebChromeClient#onShowCustomView}, which is the same path
     * the manual fullscreen button uses — so we get the customView,
     * the system bar hiding, and the overlay for free.
     *
     * Two fallbacks guard against requestFullscreen() being blocked by
     * the user-gesture policy on certain WebView builds: we also try
     * the iOS-style {@code webkitEnterFullscreen()} and finally a
     * straight click on the player's fullscreen button.
     */
    private void requestVideoFullscreen() {
        if (webView == null) return;
        webView.evaluateJavascript(
                "(function(){"
                        + "  var v = document.querySelector('video.video-stream')"
                        + "        || document.querySelector('video');"
                        + "  if (!v) return 'no-video';"
                        + "  if (v.requestFullscreen) {"
                        + "    try { v.requestFullscreen(); return 'request'; } catch(e) {}"
                        + "  }"
                        + "  if (v.webkitEnterFullscreen) {"
                        + "    try { v.webkitEnterFullscreen(); return 'webkit'; } catch(e) {}"
                        + "  }"
                        + "  var btn = document.querySelector('button.fullscreen-icon')"
                        + "        || document.querySelector('button[aria-label*=\"ull screen\"]')"
                        + "        || document.querySelector('.ytp-fullscreen-button');"
                        + "  if (btn) { btn.click(); return 'btn'; }"
                        + "  return 'none';"
                        + "})();",
                null);
    }

    private void exitVideoFullscreen() {
        if (webView == null) return;
        webView.evaluateJavascript(
                "(function(){"
                        + "  if (document.exitFullscreen) {"
                        + "    try { document.exitFullscreen(); return 'exit'; } catch(e) {}"
                        + "  }"
                        + "  if (document.webkitExitFullscreen) {"
                        + "    try { document.webkitExitFullscreen(); return 'webkit'; } catch(e) {}"
                        + "  }"
                        + "  var btn = document.querySelector('button.fullscreen-icon')"
                        + "        || document.querySelector('button[aria-label*=\"xit\"]')"
                        + "        || document.querySelector('.ytp-fullscreen-button');"
                        + "  if (btn) { btn.click(); return 'btn'; }"
                        + "  return 'none';"
                        + "})();",
                null);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Auto-rotate UX (Sprint 5): rotating the device to landscape
        // hides the app chrome + system bars and reveals the bilingual
        // overlay; rotating back to portrait restores everything.
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            enterAppFullscreen();
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            exitAppFullscreen();
        }
        // If we forced an orientation via the on-screen toggle, release
        // the lock once the system has finished rotating so the user
        // can keep using auto-rotate naturally afterwards.
        mainHandler.postDelayed(this::releaseOrientationLock, 600L);
    }

    /**
     * Force the device into landscape (or portrait) regardless of the
     * user's auto-rotate preference. The OS will fire
     * {@link #onConfigurationChanged} once it finishes rotating, which
     * drives enter/exitAppFullscreen via the same path as a free
     * device rotation.
     */
    private void requestFullscreenOrientation(boolean enter) {
        setRequestedOrientation(enter
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    /** Hand orientation control back to the device sensor / user setting. */
    private void releaseOrientationLock() {
        if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    /**
     * Refresh the overlay's EN/VI TextViews from the current active line.
     * Honours {@link #langMode} (hides the row that the user has switched
     * off) and falls back to EN-only if Vi translation hasn't landed yet,
     * mirroring the inline panel's behaviour.
     */
    private void updateOverlayText() {
        if (subtitleOverlay == null) return;
        SubtitleLine line = currentActiveLine();
        if (line == null) {
            tvOverlayEn.setText("");
            tvOverlayVi.setText("");
            tvOverlayEn.setVisibility(View.GONE);
            tvOverlayVi.setVisibility(View.GONE);
            return;
        }
        boolean hasVi = line.textVi != null && !line.textVi.isEmpty();
        boolean showEn = langMode == LangMode.EN
                || langMode == LangMode.BOTH
                || !hasVi;
        boolean showVi = langMode != LangMode.EN && hasVi;

        if (showEn) {
            tvOverlayEn.setText(line.textEn == null ? "" : line.textEn);
            tvOverlayEn.setVisibility(View.VISIBLE);
        } else {
            tvOverlayEn.setVisibility(View.GONE);
        }
        if (showVi) {
            tvOverlayVi.setText(line.textVi);
            tvOverlayVi.setVisibility(View.VISIBLE);
        } else {
            tvOverlayVi.setVisibility(View.GONE);
        }
    }

    /**
     * Sprint 4: open the dictionary BottomSheet for a long-pressed word.
     * Auto-pauses the underlying video while the sheet is up so the user
     * can read the definition without losing context, and resumes when
     * the sheet is dismissed.
     */
    private void openDictionarySheet(@NonNull String word) {
        if (isFinishing() || isDestroyed()) return;
        if (webView != null) WebViewPlayerBridge.pause(webView);
        DictionaryBottomSheet sheet = DictionaryBottomSheet.newInstance(word);
        sheet.setOnDismissListener(() -> {
            if (!isFinishing() && !isDestroyed() && webView != null) {
                WebViewPlayerBridge.play(webView);
            }
        });
        sheet.show(getSupportFragmentManager(), DictionaryBottomSheet.TAG);
    }

    // --- Subtitle fetch ------------------------------------------------------

    private void loadSubtitlesAsync(@NonNull String requestedId) {
        io.execute(() -> {
            try {
                List<SubtitleLine> lines = readCache(requestedId);
                if (lines != null && !lines.isEmpty()) {
                    runOnUiThread(() -> applyIfStillCurrent(requestedId,
                            () -> applyLines(lines)));
                    return;
                }

                SubtitleService service = new YouTubeSubtitleService(http);
                try {
                    List<SubtitleLine> fetched = service.fetch(requestedId);
                    writeCache(requestedId, fetched);
                    runOnUiThread(() -> applyIfStillCurrent(requestedId,
                            () -> applyLines(fetched)));
                } catch (SubtitleService.SubtitleUnavailableException notAvailable) {
                    runOnUiThread(() -> applyIfStillCurrent(requestedId, this::onNoSubtitle));
                } catch (SubtitleService.FetchFailedException fetchFail) {
                    runOnUiThread(() -> applyIfStillCurrent(requestedId, this::onFetchFailed));
                }
            } catch (RuntimeException unexpected) {
                Log.e(TAG, "Unexpected subtitle fetch failure", unexpected);
                runOnUiThread(() -> applyIfStillCurrent(requestedId, this::onFetchFailed));
            }
        });
    }

    /**
     * Guard against stale fetch results — when the user rapidly switches
     * between videos, an earlier in-flight fetch must not clobber the state
     * for the video that's now actually playing.
     */
    private void applyIfStillCurrent(@NonNull String requestedId, @NonNull Runnable action) {
        if (!requestedId.equals(videoId)) {
            Log.d(TAG, "Dropping stale fetch result for " + requestedId
                    + " (current=" + videoId + ")");
            return;
        }
        action.run();
    }

    private void applyLines(@NonNull List<SubtitleLine> lines) {
        latestLines = lines;
        sentenceLines = SentenceJoiner.join(lines);
        state = SubtitleState.READY;
        syncController.attach(activeLines());
        applyStateToSheet();
        // Kick off (or resume from cache) the EN→VI translation pass so the
        // user can flip to VI / Both via the lang button without waiting.
        if (videoId != null) translateAsync(videoId, lines);
    }

    private void onNoSubtitle() {
        latestLines = Collections.emptyList();
        state = SubtitleState.NO_SUBTITLE;
        syncController.attach(Collections.emptyList());
        applyStateToSheet();
    }

    private void onFetchFailed() {
        latestLines = Collections.emptyList();
        state = SubtitleState.FETCH_FAILED;
        syncController.attach(Collections.emptyList());
        applyStateToSheet();

        // Surface the choice dialog the SRS demands. The inline panel
        // already shows the retry banner; the dialog adds the upload-SRT
        // and watch-only options. We only show it once — if the panel is
        // hidden the dialog still pops, but if the user has already chosen
        // to keep the banner from a previous fetch fail we don't nag.
        if (subtitlePanel == null || subtitlePanel.getVisibility() != View.VISIBLE) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.fetch_failed_title)
                    .setMessage(R.string.fetch_failed_message)
                    .setPositiveButton(R.string.action_retry, (d, w) -> {
                        if (videoId != null) {
                            state = SubtitleState.LOADING;
                            applyStateToSheet();
                            loadSubtitlesAsync(videoId);
                        }
                    })
                    .setNeutralButton(R.string.action_upload_srt, (d, w) ->
                            Toast.makeText(this, R.string.upload_srt_not_yet,
                                    Toast.LENGTH_SHORT).show())
                    .setNegativeButton(R.string.action_watch_only, (d, w) -> { /* keep banner */ })
                    .show();
        }
    }

    // --- Cache ---------------------------------------------------------------

    private static final Type LIST_TYPE = new TypeToken<List<SubtitleLine>>() {}.getType();
    private static final Gson GSON = new Gson();

    @Nullable
    private List<SubtitleLine> readCache(String videoId) {
        SubtitleCacheDao dao = EnglishTubeApp.get().getDatabase().subtitleCacheDao();
        SubtitleCacheEntity row = dao.find(videoId, SUBTITLE_LANG_EN);
        if (row == null || row.payloadJson == null) return null;
        try {
            return GSON.fromJson(row.payloadJson, LIST_TYPE);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void writeCache(String videoId, List<SubtitleLine> lines) {
        if (lines == null || lines.isEmpty()) return;
        SubtitleCacheDao dao = EnglishTubeApp.get().getDatabase().subtitleCacheDao();
        SubtitleCacheEntity row = new SubtitleCacheEntity();
        row.videoId = videoId;
        row.lang = SUBTITLE_LANG_EN;
        row.payloadJson = GSON.toJson(lines);
        row.fetchedAt = System.currentTimeMillis();
        dao.upsert(row);
    }

    /**
     * Read a cached EN→VI translation as a list of strings parallel to the
     * cue list. Returns {@code null} when the cache row is missing or the
     * stored count doesn't match the current cue count (e.g. cue list got
     * re-fetched after the previous translation).
     */
    @Nullable
    private List<String> readTranslationCache(String videoId, int expectedSize) {
        SubtitleCacheDao dao = EnglishTubeApp.get().getDatabase().subtitleCacheDao();
        SubtitleCacheEntity row = dao.find(videoId, SUBTITLE_LANG_VI);
        if (row == null || row.payloadJson == null) return null;
        try {
            String[] arr = GSON.fromJson(row.payloadJson, String[].class);
            if (arr == null || arr.length != expectedSize) return null;
            return java.util.Arrays.asList(arr);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void writeTranslationCache(String videoId, List<String> translations) {
        if (translations == null || translations.isEmpty()) return;
        SubtitleCacheDao dao = EnglishTubeApp.get().getDatabase().subtitleCacheDao();
        SubtitleCacheEntity row = new SubtitleCacheEntity();
        row.videoId = videoId;
        row.lang = SUBTITLE_LANG_VI;
        row.payloadJson = GSON.toJson(translations.toArray(new String[0]));
        row.fetchedAt = System.currentTimeMillis();
        dao.upsert(row);
    }

    // --- Translation ---------------------------------------------------------

    /**
     * Translate the cue list to Vietnamese off the main thread. The cache
     * is consulted first so re-opening a previously-translated video is
     * instant. When the network call succeeds we mutate {@code textVi}
     * in place on the cached cue list and ask the adapter to redraw —
     * the adapter is bound to the same list reference {@link #latestLines}
     * holds, so this is enough to surface the new text.
     */
    private void translateAsync(@NonNull String requestedId, @NonNull List<SubtitleLine> lines) {
        if (lines.isEmpty()) return;

        io.execute(() -> {
            // Cache hit short-circuits everything — most users will reopen
            // the same TED talks repeatedly, and that should never re-burn
            // a Google Translate round trip.
            List<String> cached = readTranslationCache(requestedId, lines.size());
            if (cached != null) {
                runOnUiThread(() -> applyTranslationsIfStillCurrent(
                        requestedId, lines, cached));
                return;
            }

            List<String> sources = new java.util.ArrayList<>(lines.size());
            for (SubtitleLine line : lines) {
                sources.add(line.textEn == null ? "" : line.textEn);
            }

            try {
                List<String> translated = translationService.translateEnToVi(sources);
                writeTranslationCache(requestedId, translated);
                runOnUiThread(() -> applyTranslationsIfStillCurrent(
                        requestedId, lines, translated));
            } catch (TranslationService.TranslationException e) {
                Log.w(TAG, "translation failed for " + requestedId
                        + ": " + e.getMessage());
                // Leave the cue list as English-only; the adapter falls
                // back to EN per cue when textVi is null.
            } catch (RuntimeException unexpected) {
                Log.e(TAG, "translation crashed for " + requestedId, unexpected);
            }
        });
    }

    private void applyTranslationsIfStillCurrent(@NonNull String requestedId,
                                                 @NonNull List<SubtitleLine> lines,
                                                 @NonNull List<String> translations) {
        if (!requestedId.equals(videoId)) return;
        if (lines != latestLines) return; // user navigated to a different cue list
        int n = Math.min(lines.size(), translations.size());
        for (int i = 0; i < n; i++) {
            lines.get(i).textVi = translations.get(i);
        }
        // Sentence projection holds copies of cue text — rebuild it so the
        // joined Vi shows up immediately when the user is in combine mode.
        sentenceLines = SentenceJoiner.join(lines);
        if (adapter == null) return;
        if (combineLinesEnabled) {
            int preserved = activeLineIndex;
            adapter.submit(sentenceLines);
            adapter.setActiveIndex(preserved);
            // Re-attach so the sync controller's binary search references the
            // new SubtitleLine instances (timings unchanged, identity changed).
            syncController.attach(sentenceLines);
        } else {
            adapter.refreshTranslations();
        }
    }

    // --- Lifecycle -----------------------------------------------------------

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
        // Make sure system bars / orientation come back before the activity
        // tears down — otherwise an in-flight fullscreen could leak the
        // landscape-locked rotation onto the next screen.
        if (isFullscreen) exitAppFullscreen();
        syncController.detach();
        io.shutdownNow();
        if (webView != null) {
            webView.removeJavascriptInterface(WebViewPlayerBridge.NAME);
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
