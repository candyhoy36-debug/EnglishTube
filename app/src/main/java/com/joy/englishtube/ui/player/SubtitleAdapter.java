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
        h.en.setText(line.textEn);
        if (line.textVi != null && !line.textVi.isEmpty()) {
            h.vi.setText(line.textVi);
            h.vi.setVisibility(View.VISIBLE);
        } else {
            h.vi.setVisibility(View.GONE);
        }

        boolean active = position == activeIndex;
        h.itemView.setBackgroundColor(active
                ? ContextCompat.getColor(h.itemView.getContext(), R.color.subtitle_active_bg)
                : 0x00000000);
        h.en.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);

        h.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onLineClick(h.getBindingAdapterPosition(), line);
        });
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView en;
        final TextView vi;
        VH(View v) {
            super(v);
            en = v.findViewById(R.id.tv_text_en);
            vi = v.findViewById(R.id.tv_text_vi);
        }
    }
}
