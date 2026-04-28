package com.joy.englishtube.ui.player;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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

    private YouTubePlayerView playerView;
    private SubtitleAdapter adapter;
    private LinearLayoutManager layoutManager;
    private ProgressBar progress;
    private View bannerNoSubtitle;
    private TextView bannerMessage;

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

        playerView.initialize(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady(@NonNull YouTubePlayer youTubePlayer) {
                player = youTubePlayer;
                if (videoId != null) youTubePlayer.cueVideo(videoId, 0f);

                youTubePlayer.addListener(new AbstractYouTubePlayerListener() {
                    @Override
                    public void onCurrentSecond(@NonNull YouTubePlayer p, float second) {
                        long nowMs = System.currentTimeMillis();
                        if (nowMs - lastTickMs < ACTIVE_LINE_TICK_MS) return;
                        lastTickMs = nowMs;
                        syncController.onTick((long) (second * 1000f));
                    }
                });
            }
        }, /* handleNetworkEvents= */ true, options);

        syncController.setListener(this::onActiveLineChanged);
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
        });
    }

    private void applyLines(List<SubtitleLine> lines) {
        progress.setVisibility(View.GONE);
        bannerNoSubtitle.setVisibility(View.GONE);
        adapter.submit(lines);
        syncController.attach(lines);
    }

    private void showNoSubtitleBanner() {
        progress.setVisibility(View.GONE);
        bannerMessage.setText(R.string.no_subtitle_banner);
        bannerNoSubtitle.setVisibility(View.VISIBLE);
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
