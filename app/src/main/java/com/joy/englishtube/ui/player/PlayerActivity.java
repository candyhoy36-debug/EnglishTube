package com.joy.englishtube.ui.player;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.joy.englishtube.EnglishTubeApp;
import com.joy.englishtube.R;
import com.joy.englishtube.data.SubtitleCacheDao;
import com.joy.englishtube.data.SubtitleCacheEntity;
import com.joy.englishtube.model.SubtitleLine;
import com.joy.englishtube.service.PlayerSyncController;
import com.joy.englishtube.service.SubtitleService;
import com.joy.englishtube.service.impl.PlayerSyncControllerImpl;
import com.joy.englishtube.service.impl.YouTubeSubtitleService;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;

/**
 * Sprint 2 (architecture pivot): host the YouTube mobile web page in a
 * WebView so we can play any video — including those whose owner has disabled
 * the IFrame embed. Our subtitle UI is overlaid via a Material bottom sheet
 * triggered by a FloatingActionButton; time sync between the &lt;video&gt; and
 * the subtitle list is handled by {@link WebViewPlayerBridge}.
 *
 * Sprint 5 will reuse this same WebView to render the bilingual overlay when
 * the user goes fullscreen + landscape.
 */
public class PlayerActivity extends AppCompatActivity
        implements WebViewPlayerBridge.Callback {

    public static final String EXTRA_VIDEO_ID = "extra_video_id";

    private static final String TAG = "PlayerActivity";
    private static final String SUBTITLE_LANG_EN = "en";
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

    // Bottom-sheet-owned views; nullable when the sheet has not been shown yet.
    @Nullable private BottomSheetDialog subtitleSheet;
    @Nullable private SubtitleAdapter adapter;
    @Nullable private LinearLayoutManager layoutManager;
    @Nullable private ProgressBar subtitleProgress;
    @Nullable private View bannerNoSubtitle;
    @Nullable private TextView bannerMessage;
    @Nullable private Button btnRetryFetch;

    private final PlayerSyncController syncController = new PlayerSyncControllerImpl();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final OkHttpClient http = new OkHttpClient();
    private long lastTickMs = 0L;

    private enum SubtitleState { LOADING, READY, NO_SUBTITLE, FETCH_FAILED }
    private SubtitleState state = SubtitleState.LOADING;
    private List<SubtitleLine> latestLines = Collections.emptyList();

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

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        webView = findViewById(R.id.webview_player);
        webProgress = findViewById(R.id.web_progress);
        fabSubtitles = findViewById(R.id.fab_subtitles);

        configureWebView();
        webView.loadUrl("https://m.youtube.com/watch?v=" + videoId);

        fabSubtitles.setOnClickListener(v -> showSubtitleSheet());

        syncController.setListener(this::onActiveLineChanged);
        loadSubtitlesAsync(videoId);
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
                // If the user tapped a different video inside the WebView
                // (related video, autoplay queue, search result), the URL
                // carries a new ?v=ID. Re-fetch subtitles so the bottom
                // sheet matches the video that's actually playing.
                handleNavigation(url);
            }
        });
    }

    /**
     * Detect in-WebView navigation to a different video and reload
     * subtitles. Triggered from both onPageFinished (initial + reloads) and
     * the polling JS bridge (SPA transitions on m.youtube.com that don't fire
     * a full page load).
     */
    private void handleNavigation(@Nullable String url) {
        String newId = extractVideoId(url);
        if (newId == null) return;
        if (newId.equals(videoId)) return;

        Log.d(TAG, "WebView navigated videoId " + videoId + " -> " + newId);
        videoId = newId;
        latestLines = Collections.emptyList();
        state = SubtitleState.LOADING;
        syncController.attach(Collections.emptyList());
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

    // --- Subtitle bottom sheet ----------------------------------------------

    private void showSubtitleSheet() {
        if (subtitleSheet == null) buildSubtitleSheet();
        // Re-apply current state so the sheet opens with the right view.
        applyStateToSheet();
        if (subtitleSheet != null) subtitleSheet.show();
    }

    private void buildSubtitleSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View content = getLayoutInflater().inflate(R.layout.sheet_subtitle_panel, null);
        dialog.setContentView(content);

        RecyclerView rv = content.findViewById(R.id.recycler_subtitles);
        subtitleProgress = content.findViewById(R.id.subtitle_progress);
        bannerNoSubtitle = content.findViewById(R.id.banner_no_subtitle);
        bannerMessage = content.findViewById(R.id.tv_banner_message);
        btnRetryFetch = content.findViewById(R.id.btn_retry_fetch);

        adapter = new SubtitleAdapter();
        layoutManager = new LinearLayoutManager(this);
        rv.setLayoutManager(layoutManager);
        rv.setAdapter(adapter);
        adapter.setOnLineClickListener((position, line) -> {
            WebViewPlayerBridge.seekTo(webView, line.startMs / 1000.0);
        });

        btnRetryFetch.setOnClickListener(v -> {
            state = SubtitleState.LOADING;
            applyStateToSheet();
            if (videoId != null) loadSubtitlesAsync(videoId);
        });

        subtitleSheet = dialog;
    }

    /** Pushes the current {@link #state} onto whatever sheet views exist. */
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
                adapter.submit(latestLines);
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
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastTickMs < ACTIVE_LINE_TICK_MS) return;
        lastTickMs = nowMs;
        try {
            syncController.onTick((long) (seconds * 1000f));
        } catch (RuntimeException e) {
            Log.w(TAG, "sync onTick failed", e);
        }
    }

    @Override
    public void onLocation(@NonNull String url) {
        handleNavigation(url);
    }

    private void onActiveLineChanged(int newIndex) {
        if (adapter == null || layoutManager == null) return;
        adapter.setActiveIndex(newIndex);
        if (newIndex < 0) return;
        if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;

        CenterSmoothScroller scroller = new CenterSmoothScroller(this);
        scroller.setTargetPosition(newIndex);
        layoutManager.startSmoothScroll(scroller);
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
        state = SubtitleState.READY;
        syncController.attach(lines);
        applyStateToSheet();
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

        // Also surface the choice dialog the SRS demands the first time we
        // hit a fetch failure. The user can come back to "Retry" from the
        // banner inside the sheet later.
        if (subtitleSheet == null || !subtitleSheet.isShowing()) {
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
        syncController.detach();
        io.shutdownNow();
        if (subtitleSheet != null) {
            subtitleSheet.dismiss();
            subtitleSheet = null;
        }
        if (webView != null) {
            webView.removeJavascriptInterface(WebViewPlayerBridge.NAME);
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
