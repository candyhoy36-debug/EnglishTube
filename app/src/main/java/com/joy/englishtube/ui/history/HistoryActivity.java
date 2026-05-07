package com.joy.englishtube.ui.history;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.joy.englishtube.EnglishTubeApp;
import com.joy.englishtube.R;
import com.joy.englishtube.data.HistoryDao;
import com.joy.englishtube.data.HistoryEntity;
import com.joy.englishtube.ui.player.PlayerActivity;

import java.util.Collections;
import java.util.List;

/**
 * Lists every video the user has watched, newest first. Tap → reopen
 * the video in {@link PlayerActivity} (plays from the start; resume
 * position is intentionally out of scope for Sprint 6). Long-press
 * to delete a single row; toolbar overflow has "Xóa tất cả".
 */
public class HistoryActivity extends AppCompatActivity {

    private HistoryAdapter adapter;
    private RecyclerView list;
    private TextView emptyState;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_clear_all) {
                confirmClearAll();
                return true;
            }
            return false;
        });

        list = findViewById(R.id.list_history);
        emptyState = findViewById(R.id.empty_state);
        adapter = new HistoryAdapter(new HistoryAdapter.Listener() {
            @Override public void onClick(@NonNull HistoryEntity row) {
                startActivity(PlayerActivity.intent(HistoryActivity.this, row.videoId));
            }
            @Override public void onLongPress(@NonNull HistoryEntity row) {
                confirmDelete(row);
            }
        });
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        EnglishTubeApp.get().getDbExecutor().execute(() -> {
            HistoryDao dao = EnglishTubeApp.get().getDatabase().historyDao();
            final List<HistoryEntity> rows = dao.getAll();
            runOnUiThread(() -> {
                adapter.submit(rows != null ? rows : Collections.emptyList());
                emptyState.setVisibility(rows == null || rows.isEmpty() ? View.VISIBLE : View.GONE);
                list.setVisibility(rows == null || rows.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });
    }

    private void confirmDelete(@NonNull HistoryEntity row) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.history_delete_confirm)
                .setPositiveButton(R.string.action_delete, (d, w) -> {
                    EnglishTubeApp.get().getDbExecutor().execute(() -> {
                        EnglishTubeApp.get().getDatabase().historyDao()
                                .deleteById(row.videoId);
                        runOnUiThread(this::reload);
                    });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.history_clear_confirm)
                .setPositiveButton(R.string.action_delete, (d, w) -> {
                    EnglishTubeApp.get().getDbExecutor().execute(() -> {
                        EnglishTubeApp.get().getDatabase().historyDao().clear();
                        runOnUiThread(this::reload);
                    });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
