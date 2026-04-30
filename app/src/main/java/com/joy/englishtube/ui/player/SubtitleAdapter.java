package com.joy.englishtube.ui.player;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.joy.englishtube.R;
import com.joy.englishtube.model.SubtitleLine;
import com.joy.englishtube.util.WordExtractor;

import java.util.Collections;
import java.util.List;

/**
 * Renders the bilingual subtitle list. Sprint 2 only populates the EN row;
 * the VI row is wired up but stays {@code GONE} until Sprint 3 fills in
 * translations.
 *
 * The active line is highlighted via {@link #setActiveIndex(int)}. We notify
 * only the previous + new positions to keep scrolling cheap.
 */
public class SubtitleAdapter extends RecyclerView.Adapter<SubtitleAdapter.VH> {

    public interface OnLineClickListener {
        void onLineClick(int position, SubtitleLine line);
    }

    /** Sprint 4: long-press a word in a row to look it up. */
    public interface OnWordLongPressListener {
        void onWordLongPressed(@NonNull String word);
    }

    /**
     * Which language tracks to render. EN shows only the English row;
     * VI shows only the Vietnamese row (English row hidden); BOTH shows
     * both stacked.
     */
    public enum LangMode { EN, VI, BOTH }

    private List<SubtitleLine> lines = Collections.emptyList();
    private int activeIndex = -1;
    private LangMode langMode = LangMode.EN;
    @androidx.annotation.Nullable
    private OnLineClickListener clickListener;
    @androidx.annotation.Nullable
    private OnWordLongPressListener wordLongPressListener;

    public void submit(List<SubtitleLine> newLines) {
        this.lines = newLines == null ? Collections.emptyList() : newLines;
        this.activeIndex = -1;
        notifyDataSetChanged();
    }

    public void setOnLineClickListener(@androidx.annotation.Nullable OnLineClickListener l) {
        this.clickListener = l;
    }

    public void setOnWordLongPressListener(
            @androidx.annotation.Nullable OnWordLongPressListener l) {
        this.wordLongPressListener = l;
    }

    public void setActiveIndex(int newActive) {
        if (newActive == activeIndex) return;
        int prev = activeIndex;
        activeIndex = newActive;
        if (prev >= 0 && prev < lines.size()) notifyItemChanged(prev);
        if (newActive >= 0 && newActive < lines.size()) notifyItemChanged(newActive);
    }

    public int getActiveIndex() {
        return activeIndex;
    }

    public void setLangMode(@NonNull LangMode mode) {
        if (mode == langMode) return;
        langMode = mode;
        notifyDataSetChanged();
    }

    /**
     * Refresh after a translation pass finishes, so the cached cues
     * pick up their new {@code textVi}. We can't use
     * {@link #notifyItemRangeChanged(int, int)} alone because the
     * caller may have replaced the entire list with translated copies.
     */
    public void refreshTranslations() {
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subtitle, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        SubtitleLine line = lines.get(position);
        h.timestamp.setText(formatTimestamp(line.startMs));

        boolean hasVi = line.textVi != null && !line.textVi.isEmpty();
        // VI-only mode falls back to EN if the translation hasn't landed
        // yet — empty rows would feel broken.
        boolean showEn = langMode == LangMode.EN
                || langMode == LangMode.BOTH
                || !hasVi;
        boolean showVi = langMode != LangMode.EN && hasVi;

        if (showEn) {
            h.en.setText(line.textEn);
            h.en.setVisibility(View.VISIBLE);
        } else {
            h.en.setVisibility(View.GONE);
        }

        if (showVi) {
            h.vi.setText(line.textVi);
            h.vi.setVisibility(View.VISIBLE);
        } else {
            h.vi.setVisibility(View.GONE);
        }

        // Active row gets ONLY a background swap — no bold / italic / color
        // changes. Re-typesetting on every cue tick is visually jarring and
        // makes EN and VI hard to tell apart. The background drawable plus
        // the static text colors are enough to spot the active row.
        boolean active = position == activeIndex;
        int normalColor = ContextCompat.getColor(h.itemView.getContext(),
                R.color.text_primary);
        if (active) {
            h.itemView.setBackgroundResource(R.drawable.bg_subtitle_item_active);
        } else {
            h.itemView.setBackgroundColor(0x00000000);
        }
        h.en.setTextColor(normalColor);
        h.en.setTypeface(null, Typeface.NORMAL);
        h.vi.setTextColor(ContextCompat.getColor(h.itemView.getContext(),
                R.color.text_secondary));
        h.vi.setTypeface(null, Typeface.NORMAL);

        // Sprint 4: long-press anywhere on the row to look up the word the
        // user pressed on. Touch coords are tracked on the item view so
        // onLongClick can map them back into the correct TextView (EN or VI)
        // and call {@link WordExtractor} for the word. Single-tap behaviour
        // (seek-to-cue) is unchanged because the touch helper returns false.
        WordLongPressHelper helper = new WordLongPressHelper(h.en, h.vi,
                word -> {
                    if (wordLongPressListener != null) {
                        wordLongPressListener.onWordLongPressed(word);
                    }
                });
        h.itemView.setOnTouchListener(helper);
        h.itemView.setOnLongClickListener(helper);
        h.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onLineClick(h.getBindingAdapterPosition(), line);
        });
    }

    /**
     * Records the latest touch coordinates on a row so the row's long-click
     * can figure out which word in which TextView the user actually pressed
     * on. We attach to the item view (not the TextViews) so the row's
     * OnClickListener for seek-to-cue keeps working unchanged.
     */
    private static class WordLongPressHelper
            implements View.OnTouchListener, View.OnLongClickListener {
        interface Sink { void accept(@NonNull String word); }

        private final TextView en;
        private final TextView vi;
        private final Sink sink;
        private float lastRowX;
        private float lastRowY;

        WordLongPressHelper(@NonNull TextView en,
                            @NonNull TextView vi,
                            @NonNull Sink sink) {
            this.en = en;
            this.vi = vi;
            this.sink = sink;
        }

        @Override
        public boolean onTouch(View row, MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN
                    || e.getAction() == MotionEvent.ACTION_MOVE) {
                lastRowX = e.getX();
                lastRowY = e.getY();
            }
            // Don't consume — the row still needs to fire click + long-click.
            return false;
        }

        @Override
        public boolean onLongClick(View row) {
            TextView target = pickTarget(row);
            if (target == null) return false;
            CharSequence cs = target.getText();
            if (cs == null || cs.length() == 0) return false;
            int[] offsetXY = relativeOffset(target, row);
            float localX = lastRowX - offsetXY[0];
            float localY = lastRowY - offsetXY[1];
            int offset = target.getOffsetForPosition(localX, localY);
            String word = WordExtractor.wordAt(cs.toString(), offset);
            if (word == null) return false;
            sink.accept(word);
            return true;
        }

        @androidx.annotation.Nullable
        private TextView pickTarget(@NonNull View row) {
            // Use the touched Y to choose between the visible EN row and
            // (when shown) the VI row directly underneath it. EN/VI live
            // inside a nested vertical LinearLayout so their coordinates
            // need walking up to the row's coord space.
            TextView[] candidates = { en, vi };
            for (TextView tv : candidates) {
                if (tv.getVisibility() != View.VISIBLE) continue;
                int[] xy = relativeOffset(tv, row);
                int yTop = xy[1];
                int yBottom = yTop + tv.getHeight();
                if (lastRowY >= yTop && lastRowY <= yBottom) return tv;
            }
            // Fallback: if neither vertical band matched, prefer EN if it
            // is visible — typical case is the touch hitting padding above
            // the EN line.
            if (en.getVisibility() == View.VISIBLE) return en;
            if (vi.getVisibility() == View.VISIBLE) return vi;
            return null;
        }

        /** {x, y} offset of {@code child} inside {@code ancestor}. */
        private static int[] relativeOffset(@NonNull View child, @NonNull View ancestor) {
            int x = 0;
            int y = 0;
            View v = child;
            while (v != null && v != ancestor) {
                x += v.getLeft();
                y += v.getTop();
                if (v.getParent() instanceof View) {
                    v = (View) v.getParent();
                } else {
                    break;
                }
            }
            return new int[] { x, y };
        }
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    /**
     * Format a cue start time as either "M:SS" or "H:MM:SS" so very long
     * videos still read naturally. Always pads seconds to two digits and
     * minutes to two digits when an hour component is present.
     */
    private static String formatTimestamp(long startMs) {
        long totalSec = Math.max(0, startMs / 1000L);
        long hours = totalSec / 3600L;
        long minutes = (totalSec % 3600L) / 60L;
        long seconds = totalSec % 60L;
        if (hours > 0) {
            return String.format(java.util.Locale.US, "%d:%02d:%02d",
                    hours, minutes, seconds);
        }
        return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds);
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView timestamp;
        final TextView en;
        final TextView vi;
        VH(View v) {
            super(v);
            timestamp = v.findViewById(R.id.tv_timestamp);
            en = v.findViewById(R.id.tv_text_en);
            vi = v.findViewById(R.id.tv_text_vi);
        }
    }
}
