package com.joy.englishtube.ui.history;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.joy.englishtube.R;
import com.joy.englishtube.data.HistoryEntity;
import com.joy.englishtube.util.RelativeTime;

import java.util.ArrayList;
import java.util.List;

class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

    interface Listener {
        void onClick(@NonNull HistoryEntity row);
        void onLongPress(@NonNull HistoryEntity row);
    }

    private final List<HistoryEntity> items = new ArrayList<>();
    private final Listener listener;

    HistoryAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    void submit(@NonNull List<HistoryEntity> rows) {
        items.clear();
        items.addAll(rows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        HistoryEntity row = items.get(position);
        h.title.setText(row.title != null && !row.title.isEmpty()
                ? row.title : row.videoId);
        h.subtitle.setText(RelativeTime.format(h.itemView.getContext(),
                row.lastWatchedAt));
        Glide.with(h.thumb.getContext())
                .load(row.thumbnailUrl)
                .centerCrop()
                .into(h.thumb);
        h.itemView.setOnClickListener(v -> listener.onClick(row));
        h.itemView.setOnLongClickListener(v -> {
            listener.onLongPress(row);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView title;
        final TextView subtitle;

        VH(@NonNull View itemView) {
            super(itemView);
            thumb = itemView.findViewById(R.id.thumb);
            title = itemView.findViewById(R.id.title);
            subtitle = itemView.findViewById(R.id.subtitle);
        }
    }
}
