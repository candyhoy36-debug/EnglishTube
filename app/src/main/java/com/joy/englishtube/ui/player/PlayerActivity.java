package com.joy.englishtube.ui.player;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
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
import com.joy.englishtube.EnglishTubeApp;
import com.joy.englishtube.R;
import com.joy.englishtube.data.SubtitleCacheDao;
import com.joy.englishtube.data.SubtitleCacheEntity;
import com.joy.englishtube.model.SubtitleLine;
import com.joy.englishtube.service.PlayerSyncController;
import com.joy.englishtube.service.SubtitleService;
import com.joy.englishtube.service.impl.PlayerSyncControllerImpl;
import com.joy.englishtube.service.impl.YouTubeSubtitleService;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import okhttp3.OkHttpClient;

/**
 * Sprint 2: hosts {@link YouTubePlayerView} on top and a bilingual subtitle
 * RecyclerView below. Subtitle text is fetched in the background through
 * {@link YouTubeSubtitleService} and cached in Room.
 *
 * Three states are surfaced to the user (SRS §1.5):
 *   • Has EN subtitles → list visible, auto-scroll to active line.
 *   • No EN subtitles  → warning banner, video plays normally.
 *   • Track exists but fetch fails → dialog asking the user to upload an SRT
 *     (Sprint 6 will implement the upload picker) or watch without subtitles.
 *
 * Translation to VI is NOT done in Sprint 2 — that is Sprint 3's job. The
 * adapter already supports a VI row but it stays {@code GONE} until Sprint 3.
 */
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_ID = "extra_video_id";

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

    private static final String TAG = "PlayerActivity";

    private YouTubePlayerView playerView;
    private SubtitleAdapter adapter;
    private LinearLayoutManager layoutManager;
    private ProgressBar progress;
    private View bannerNoSubtitle;
    private TextView bannerMessage;
    private Button btnOpenInYouTube;

    @Nullable
    private YouTubePlayer player;
    private final PlayerSyncController syncController = new PlayerSyncControllerImpl();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private long lastTickMs = 0L;

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

        playerView = findViewById(R.id.youtube_player_view);
        RecyclerView recyclerView = findViewById(R.id.recycler_subtitles);
        progress = findViewById(R.id.subtitle_progress);
        bannerNoSubtitle = findViewById(R.id.banner_no_subtitle);
        bannerMessage = findViewById(R.id.tv_banner_message);
        btnOpenInYouTube = findViewById(R.id.btn_open_in_youtube);
        btnOpenInYouTube.setOnClickListener(v -> openVideoInYouTube());

        adapter = new SubtitleAdapter();
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        adapter.setOnLineClickListener((position, line) -> {
            if (player != null) player.seekTo(line.startMs / 1000f);
        });

        getLifecycle().addObserver(playerView);
        initializePlayer();

        progress.setVisibility(View.VISIBLE);
        loadSubtitlesAsync(videoId);
    }

    private void initializePlayer() {
        // Custom IFrame options: rel=0 so suggested videos at the end stay limited to channel.
        IFramePlayerOptions options = new IFramePlayerOptions.Builder()
                .controls(1)
                .rel(0)
                .build();

        // Single listener handles onReady, onCurrentSecond, and onError so we
        // never call addListener on the YouTubePlayer instance — that secondary
        // path was implicated in the post-error crash on some video IDs.
        playerView.initialize(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady(@NonNull YouTubePlayer youTubePlayer) {
                player = youTubePlayer;
                if (videoId != null) youTubePlayer.cueVideo(videoId, 0f);
            }

            @Override
            public void onCurrentSecond(@NonNull YouTubePlayer p, float second) {
                long nowMs = System.currentTimeMillis();
                if (nowMs - lastTickMs < ACTIVE_LINE_TICK_MS) return;
                lastTickMs = nowMs;
                try {
                    syncController.onTick((long) (second * 1000f));
                } catch (RuntimeException e) {
                    Log.w(TAG, "sync onTick failed", e);
                }
            }

            @Override
            public void onError(@NonNull YouTubePlayer p,
                                @NonNull PlayerConstants.PlayerError error) {
                Log.w(TAG, "YouTubePlayer error: " + error);
                showPlayerError(error);
            }
        }, /* handleNetworkEvents= */ true, options);

        syncController.setListener(this::onActiveLineChanged);
    }

    /**
     * Surfaces a YouTube IFrame Player error in the warning banner so the user
     * is not stuck on a black screen. For embed-disabled videos we expose the
     * "Mở trên YouTube" button so they can finish watching natively.
     */
    private void showPlayerError(@NonNull PlayerConstants.PlayerError error) {
        progress.setVisibility(View.GONE);
        adapter.submit(Collections.emptyList());
        syncController.attach(Collections.emptyList());

        int messageRes;
        boolean offerOpenInYouTube = false;
        switch (error) {
            case VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER:
                messageRes = R.string.player_error_embed_disabled;
                offerOpenInYouTube = true;
                break;
            case VIDEO_NOT_FOUND:
                messageRes = R.string.player_error_video_not_found;
                break;
            case HTML_5_PLAYER:
                messageRes = R.string.player_error_html5;
                offerOpenInYouTube = true;
                break;
            default:
                bannerMessage.setText(getString(R.string.player_error_unknown, error.name()));
                bannerNoSubtitle.setVisibility(View.VISIBLE);
                btnOpenInYouTube.setVisibility(View.VISIBLE);
                return;
        }
        bannerMessage.setText(messageRes);
        bannerNoSubtitle.setVisibility(View.VISIBLE);
        btnOpenInYouTube.setVisibility(offerOpenInYouTube ? View.VISIBLE : View.GONE);
    }

    private void openVideoInYouTube() {
        if (videoId == null) return;
        Uri uri = Uri.parse("https://www.youtube.com/watch?v=" + videoId);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage("com.google.android.youtube");
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException notInstalled) {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        }
    }

    private void onActiveLineChanged(int newIndex) {
        adapter.setActiveIndex(newIndex);
        if (newIndex < 0) return;
        if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;

        CenterSmoothScroller scroller = new CenterSmoothScroller(this);
        scroller.setTargetPosition(newIndex);
        layoutManager.startSmoothScroll(scroller);
    }

    /** Reads cache first, falls back to live fetch on a worker thread. */
    private void loadSubtitlesAsync(String videoId) {
        io.execute(() -> {
            try {
                List<SubtitleLine> lines = readCache(videoId);
                if (lines != null && !lines.isEmpty()) {
                    runOnUiThread(() -> applyLines(lines));
                    return;
                }

                SubtitleService service = new YouTubeSubtitleService(new OkHttpClient());
                try {
                    List<SubtitleLine> fetched = service.fetch(videoId);
                    writeCache(videoId, fetched);
                    runOnUiThread(() -> applyLines(fetched));
                } catch (SubtitleService.SubtitleUnavailableException notAvailable) {
                    runOnUiThread(this::showNoSubtitleBanner);
                } catch (SubtitleService.FetchFailedException fetchFail) {
                    runOnUiThread(this::showFetchFailedDialog);
                }
            } catch (RuntimeException unexpected) {
                // Network library / parser regressions must NOT crash the
                // activity. Treat them like a normal fetch failure.
                Log.e(TAG, "Unexpected subtitle fetch failure", unexpected);
                runOnUiThread(this::showFetchFailedDialog);
            }
        });
    }

    private void applyLines(List<SubtitleLine> lines) {
        progress.setVisibility(View.GONE);
        bannerNoSubtitle.setVisibility(View.GONE);
        btnOpenInYouTube.setVisibility(View.GONE);
        adapter.submit(lines);
        syncController.attach(lines);
    }

    private void showNoSubtitleBanner() {
        progress.setVisibility(View.GONE);
        bannerMessage.setText(R.string.no_subtitle_banner);
        bannerNoSubtitle.setVisibility(View.VISIBLE);
        btnOpenInYouTube.setVisibility(View.GONE);
        adapter.submit(Collections.emptyList());
        syncController.attach(Collections.emptyList());
    }

    private void showFetchFailedDialog() {
        progress.setVisibility(View.GONE);
        adapter.submit(Collections.emptyList());
        syncController.attach(Collections.emptyList());

        new AlertDialog.Builder(this)
                .setTitle(R.string.fetch_failed_title)
                .setMessage(R.string.fetch_failed_message)
                .setPositiveButton(R.string.action_retry, (d, w) -> {
                    progress.setVisibility(View.VISIBLE);
                    loadSubtitlesAsync(videoId);
                })
                .setNeutralButton(R.string.action_upload_srt, (d, w) ->
                        Toast.makeText(this, R.string.upload_srt_not_yet, Toast.LENGTH_SHORT).show())
                .setNegativeButton(R.string.action_watch_only, (d, w) -> showNoSubtitleBanner())
                .setCancelable(false)
                .show();
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
    protected void onDestroy() {
        syncController.detach();
        io.shutdownNow();
        if (playerView != null) {
            playerView.release();
            playerView = null;
        }
        super.onDestroy();
    }
}
