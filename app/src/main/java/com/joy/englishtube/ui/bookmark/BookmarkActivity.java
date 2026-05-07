package com.joy.englishtube.ui.bookmark;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
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
import com.joy.englishtube.data.BookmarkDao;
import com.joy.englishtube.data.BookmarkEntity;
import com.joy.englishtube.ui.player.PlayerActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Lists every saved bookmark, grouped by video. Within a group, rows
 * are ordered by start timestamp ascending so they read like a script.
 *
 * <p>Tap a row → reopen the source video (PlayerActivity plays from
 * the start; seeking to the bookmarked sentence is out of scope for
 * Sprint 6 since we'd need to wire a deep-link extra through). Long-
 * press → edit-note dialog or delete (action choice dialog).
 *
 * <p>Search is client-side, case-insensitive over textEn / textVi /
 * note / videoTitle so the user can find a bookmark even when they
 * don't remember which video it came from.
 */
public class BookmarkActivity extends AppCompatActivity {

    private BookmarkAdapter adapter;
    private RecyclerView list;
    private TextView emptyState;
    private EditText searchBox;

    private List<BookmarkEntity> all = new ArrayList<>();
    private String filter = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmark);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_clear_all) {
                confirmClearAll();
                return true;
            }
            return false;
        });

        list = findViewById(R.id.list_bookmark);
        emptyState = findViewById(R.id.empty_state);
        searchBox = findViewById(R.id.search_box);

        adapter = new BookmarkAdapter(new BookmarkAdapter.Listener() {
            @Override public void onClick(@NonNull BookmarkEntity row) {
                startActivity(PlayerActivity.intent(BookmarkActivity.this, row.videoId));
            }
            @Override public void onLongPress(@NonNull BookmarkEntity row) {
                showActionMenu(row);
            }
        });
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                filter = s == null ? "" : s.toString().trim();
                applyFilter();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        EnglishTubeApp.get().getDbExecutor().execute(() -> {
            BookmarkDao dao = EnglishTubeApp.get().getDatabase().bookmarkDao();
            final List<BookmarkEntity> rows = dao.getAll();
            runOnUiThread(() -> {
                all = rows != null ? rows : Collections.emptyList();
                applyFilter();
            });
        });
    }

    private void applyFilter() {
        List<BookmarkEntity> matched;
        if (filter.isEmpty()) {
            matched = all;
        } else {
            String needle = filter.toLowerCase(Locale.getDefault());
            matched = new ArrayList<>();
            for (BookmarkEntity b : all) {
                if (matches(b, needle)) matched.add(b);
            }
        }
        adapter.submit(BookmarkAdapter.group(matched));
        boolean empty = matched.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private static boolean matches(@NonNull BookmarkEntity b, @NonNull String needle) {
        return contains(b.textEn, needle)
                || contains(b.textVi, needle)
                || contains(b.note, needle)
                || contains(b.videoTitle, needle);
    }

    private static boolean contains(@Nullable String haystack, @NonNull String needle) {
        return haystack != null
                && haystack.toLowerCase(Locale.getDefault()).contains(needle);
    }

    private void showActionMenu(@NonNull BookmarkEntity row) {
        CharSequence[] actions = {
                getString(R.string.bookmark_edit_note),
                getString(R.string.action_delete)
        };
        new AlertDialog.Builder(this)
                .setItems(actions, (d, which) -> {
                    if (which == 0) showEditNoteDialog(row);
                    else confirmDelete(row);
                })
                .show();
    }

    private void showEditNoteDialog(@NonNull BookmarkEntity row) {
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_note, null);
        TextView textEn = view.findViewById(R.id.text_en);
        EditText input = view.findViewById(R.id.note_input);
        textEn.setText(row.textEn);
        input.setText(row.note != null ? row.note : "");

        new AlertDialog.Builder(this)
                .setTitle(R.string.bookmark_edit_note)
                .setView(view)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    String newNote = input.getText().toString().trim();
                    row.note = newNote.isEmpty() ? null : newNote;
                    EnglishTubeApp.get().getDbExecutor().execute(() -> {
                        EnglishTubeApp.get().getDatabase().bookmarkDao().update(row);
                        runOnUiThread(this::reload);
                    });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmDelete(@NonNull BookmarkEntity row) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.bookmark_delete_confirm)
                .setPositiveButton(R.string.action_delete, (d, w) -> {
                    EnglishTubeApp.get().getDbExecutor().execute(() -> {
                        EnglishTubeApp.get().getDatabase().bookmarkDao().delete(row);
                        runOnUiThread(this::reload);
                    });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.bookmark_clear_confirm)
                .setPositiveButton(R.string.action_delete, (d, w) -> {
                    EnglishTubeApp.get().getDbExecutor().execute(() -> {
                        EnglishTubeApp.get().getDatabase().bookmarkDao().clear();
                        runOnUiThread(this::reload);
                    });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
