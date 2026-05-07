package com.joy.englishtube.ui.bookmark;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.joy.englishtube.R;
import com.joy.englishtube.data.BookmarkEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flat-list RecyclerView adapter that renders bookmarks grouped by
 * video. Each group is visually a single rounded-corner card:
 * <ul>
 *   <li>Header row (rounded top corners) — YT thumbnail, title,
 *       count badge.</li>
 *   <li>One or more bookmark rows — square-cornered middle pieces.
 *       The last bookmark of a group gets a "card bottom" background
 *       so its bottom corners stay rounded.</li>
 * </ul>
 * Header and item live in separate XML files; the bottom-corner
 * swap happens in {@link #onBindViewHolder} based on the next row's
 * type.
 */
class BookmarkAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    interface Listener {
        void onClick(@NonNull BookmarkEntity row);
        void onLongPress(@NonNull BookmarkEntity row);
    }

    /**
     * Either a header (with display label, thumbnail URL, count) or
     * an item (BookmarkEntity). Headers are pure presentation — the
     * raw entities still drive search/long-press.
     */
    static final class Row {
        final boolean isHeader;
        @androidx.annotation.Nullable final String headerLabel;
        @androidx.annotation.Nullable final String headerThumbnailUrl;
        final int headerCount;
        @androidx.annotation.Nullable final BookmarkEntity entity;

        private Row(boolean header, String label, String thumb, int count,
                    BookmarkEntity e) {
            this.isHeader = header;
            this.headerLabel = label;
            this.headerThumbnailUrl = thumb;
            this.headerCount = count;
            this.entity = e;
        }

        static Row header(String label, String thumb, int count) {
            return new Row(true, label, thumb, count, null);
        }
        static Row item(BookmarkEntity e) {
            return new Row(false, null, null, 0, e);
        }
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
     * falls back to the videoId string itself. Thumbnail is derived
     * from videoId via the canonical YT mqdefault URL.
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
            String label = labels.getOrDefault(e.getKey(), e.getKey());
            String thumb = "https://i.ytimg.com/vi/" + e.getKey() + "/mqdefault.jpg";
            out.add(Row.header(label, thumb, e.getValue().size()));
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
            HeaderVH hh = (HeaderVH) vh;
            hh.title.setText(r.headerLabel);
            hh.count.setText(String.valueOf(r.headerCount));
            if (r.headerThumbnailUrl != null) {
                Glide.with(hh.thumbnail.getContext())
                        .load(r.headerThumbnailUrl)
                        .centerCrop()
                        .into(hh.thumbnail);
            }
            return;
        }
        BookmarkEntity b = r.entity;
        if (b == null) return;
        ItemVH h = (ItemVH) vh;

        // Last row of the card gets rounded bottom corners; rows in
        // the middle stay flat so consecutive bookmarks read as one
        // card rather than a stack of pills.
        boolean isLastInGroup = (position == rows.size() - 1)
                || rows.get(position + 1).isHeader;
        h.itemView.setBackgroundResource(isLastInGroup
                ? R.drawable.bg_bookmark_card_bottom
                : R.drawable.bg_bookmark_card_middle);

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
        final TextView count;
        final ImageView thumbnail;
        HeaderVH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.group_title);
            count = itemView.findViewById(R.id.group_count);
            thumbnail = itemView.findViewById(R.id.thumbnail);
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
