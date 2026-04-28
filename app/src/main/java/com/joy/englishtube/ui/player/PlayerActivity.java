package com.joy.englishtube.ui.player;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.joy.englishtube.R;

/**
 * Sprint 1 placeholder. Receives a videoId from MainActivity and displays it.
 * Sprints 2–5 will replace this body with YouTube IFrame Player + bilingual
 * subtitle list + landscape overlay.
 */
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_ID = "extra_video_id";

    @NonNull
    public static Intent intent(@NonNull Context ctx, @NonNull String videoId) {
        Intent i = new Intent(ctx, PlayerActivity.class);
        i.putExtra(EXTRA_VIDEO_ID, videoId);
        return i;
    }

    @Nullable
    private String videoId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        videoId = getIntent().getStringExtra(EXTRA_VIDEO_ID);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView idLabel = findViewById(R.id.tv_video_id);
        idLabel.setText(getString(R.string.extra_video_id_label,
                videoId == null ? "(none)" : videoId));

        MaterialButton openInYouTube = findViewById(R.id.btn_open_in_youtube);
        openInYouTube.setOnClickListener(v -> openInExternalYouTube());
        openInYouTube.setEnabled(videoId != null);
    }

    private void openInExternalYouTube() {
        if (videoId == null) return;
        Uri uri = Uri.parse("https://www.youtube.com/watch?v=" + videoId);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage("com.google.android.youtube");
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            // YouTube app not installed — fall back to default browser.
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException nope) {
                Toast.makeText(this, R.string.error_no_youtube_app, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
