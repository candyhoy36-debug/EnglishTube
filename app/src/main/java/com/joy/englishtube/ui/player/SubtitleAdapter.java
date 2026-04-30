package com.joy.englishtube.ui.player;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.joy.englishtube.R;
import com.joy.englishtube.model.SubtitleLine;

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

    public void submit(List<SubtitleLine> newLines) {
        this.lines = newLines == null ? Collections.emptyList() : newLines;
        this.activeIndex = -1;
        notifyDataSetChanged();
    }

    public void setOnLineClickListener(@androidx.annotation.Nullable OnLineClickListener l) {
        this.clickListener = l;
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

        boolean active = position == activeIndex;
        int activeColor = ContextCompat.getColor(h.itemView.getContext(),
                R.color.brand_primary_dark);
        int normalColor = ContextCompat.getColor(h.itemView.getContext(),
                R.color.text_primary);
        if (active) {
            h.itemView.setBackgroundResource(R.drawable.bg_subtitle_item_active);
            h.en.setTextColor(activeColor);
            h.en.setTypeface(null, Typeface.BOLD);
            // Highlight VI too — when EN is hidden in VI-only mode the bold
            // accent must still travel with the active row.
            h.vi.setTextColor(activeColor);
            h.vi.setTypeface(null, Typeface.BOLD);
        } else {
            h.itemView.setBackgroundColor(0x00000000);
            h.en.setTextColor(normalColor);
            h.en.setTypeface(null, Typeface.NORMAL);
            h.vi.setTextColor(ContextCompat.getColor(h.itemView.getContext(),
                    R.color.text_secondary));
            h.vi.setTypeface(null, Typeface.NORMAL);
        }

        h.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onLineClick(h.getBindingAdapterPosition(), line);
        });
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
