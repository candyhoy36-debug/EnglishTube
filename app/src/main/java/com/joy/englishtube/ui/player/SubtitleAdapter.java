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

    private List<SubtitleLine> lines = Collections.emptyList();
    private int activeIndex = -1;
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
        h.en.setText(line.textEn);
        if (line.textVi != null && !line.textVi.isEmpty()) {
            h.vi.setText(line.textVi);
            h.vi.setVisibility(View.VISIBLE);
        } else {
            h.vi.setVisibility(View.GONE);
        }

        boolean active = position == activeIndex;
        if (active) {
            h.itemView.setBackgroundResource(R.drawable.bg_subtitle_item_active);
            h.en.setTextColor(ContextCompat.getColor(h.itemView.getContext(),
                    R.color.brand_primary_dark));
            h.en.setTypeface(null, Typeface.BOLD);
        } else {
            h.itemView.setBackgroundColor(0x00000000);
            h.en.setTextColor(ContextCompat.getColor(h.itemView.getContext(),
                    R.color.text_primary));
            h.en.setTypeface(null, Typeface.NORMAL);
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
