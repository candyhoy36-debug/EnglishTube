package com.joy.englishtube.ui.bookmark;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.joy.englishtube.R;
import com.joy.englishtube.data.BookmarkEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flat-list RecyclerView adapter that renders bookmarks grouped by
 * video. Each group's first row is a header showing the video title
 * (or videoId fallback); subsequent rows are the actual bookmarks
 * within that video, ordered by start timestamp.
 */
class BookmarkAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    interface Listener {
        void onClick(@NonNull BookmarkEntity row);
        void onLongPress(@NonNull BookmarkEntity row);
    }

    /** Either a header (videoTitle/videoId) or an item (BookmarkEntity). */
    static final class Row {
        final boolean isHeader;
        @androidx.annotation.Nullable final String headerLabel;
        @androidx.annotation.Nullable final BookmarkEntity entity;

        private Row(boolean header, String label, BookmarkEntity e) {
            this.isHeader = header;
            this.headerLabel = label;
            this.entity = e;
        }

        static Row header(String label) { return new Row(true, label, null); }
        static Row item(BookmarkEntity e) { return new Row(false, null, e); }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Listener listener;

    BookmarkAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    void submit(@NonNull List<Row> next) {
        rows.clear();
        rows.addAll(next);
        notifyDataSetChanged();
    }

    /**
     * Group a flat list of bookmarks (already ordered by videoId,
     * startMs from the DAO) into header + item rows. Header label
     * uses the most-recent non-null videoTitle for that videoId, or
     * falls back to the videoId string itself.
     */
    static List<Row> group(@NonNull List<BookmarkEntity> source) {
        // Preserve insertion order — DAO returns videoId-grouped already.
        Map<String, List<BookmarkEntity>> byVideo = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        for (BookmarkEntity b : source) {
            byVideo.computeIfAbsent(b.videoId, k -> new ArrayList<>()).add(b);
            if (b.videoTitle != null && !b.videoTitle.isEmpty()) {
                labels.put(b.videoId, b.videoTitle);
            } else {
                labels.putIfAbsent(b.videoId, b.videoId);
            }
        }
        List<Row> out = new ArrayList<>();
        for (Map.Entry<String, List<BookmarkEntity>> e : byVideo.entrySet()) {
            out.add(Row.header(labels.getOrDefault(e.getKey(), e.getKey())));
            for (BookmarkEntity item : e.getValue()) out.add(Row.item(item));
        }
        return out;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).isHeader ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View v = inflater.inflate(R.layout.item_bookmark_header, parent, false);
            return new HeaderVH(v);
        }
        View v = inflater.inflate(R.layout.item_bookmark, parent, false);
        return new ItemVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vh, int position) {
        Row r = rows.get(position);
        if (vh instanceof HeaderVH) {
            ((HeaderVH) vh).title.setText(r.headerLabel);
            return;
        }
        BookmarkEntity b = r.entity;
        if (b == null) return;
        ItemVH h = (ItemVH) vh;
        h.timestamp.setText(formatMs(b.startMs));
        h.textEn.setText(b.textEn);
        if (b.textVi != null && !b.textVi.isEmpty()) {
            h.textVi.setVisibility(View.VISIBLE);
            h.textVi.setText(b.textVi);
        } else {
            h.textVi.setVisibility(View.GONE);
        }
        if (b.note != null && !b.note.isEmpty()) {
            h.note.setVisibility(View.VISIBLE);
            h.note.setText("📝 " + b.note);
        } else {
            h.note.setVisibility(View.GONE);
        }
        h.itemView.setOnClickListener(v -> listener.onClick(b));
        h.itemView.setOnLongClickListener(v -> {
            listener.onLongPress(b);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private static String formatMs(long ms) {
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) return String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, sec);
        return String.format(java.util.Locale.US, "%d:%02d", m, sec);
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView title;
        HeaderVH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.group_title);
        }
    }

    static class ItemVH extends RecyclerView.ViewHolder {
        final TextView timestamp, textEn, textVi, note;
        ItemVH(@NonNull View itemView) {
            super(itemView);
            timestamp = itemView.findViewById(R.id.timestamp);
            textEn = itemView.findViewById(R.id.text_en);
            textVi = itemView.findViewById(R.id.text_vi);
            note = itemView.findViewById(R.id.note);
        }
    }
}
