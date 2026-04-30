package com.joy.englishtube.service.impl;

import androidx.annotation.Nullable;

import com.joy.englishtube.model.SubtitleLine;
import com.joy.englishtube.service.PlayerSyncController;

import java.util.Collections;
import java.util.List;

/**
 * Default implementation. Uses binary search on line start times so even
 * very long subtitle tracks (TED talks &gt; 1000 lines) lookup in O(log n)
 * per tick.
 *
 * Returned activeIndex is -1 when {@code currentMs} is outside any cue, e.g.
 * silent intro / outro / between-line gaps.
 */
public class PlayerSyncControllerImpl implements PlayerSyncController {

    private List<SubtitleLine> lines = Collections.emptyList();
    private int lastIndex = -2; // sentinel different from any real value
    @Nullable
    private Listener listener;

    @Override
    public void attach(List<SubtitleLine> lines) {
        this.lines = lines == null ? Collections.emptyList() : lines;
        this.lastIndex = -2;
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onTick(long currentMs) {
        int idx = binarySearch(currentMs);
        if (idx == lastIndex) return;
        lastIndex = idx;
        if (listener != null) listener.onActiveLineChanged(idx);
    }

    @Override
    public void detach() {
        this.lines = Collections.emptyList();
        this.listener = null;
        this.lastIndex = -2;
    }

    int binarySearch(long currentMs) {
        if (lines.isEmpty()) return -1;
        int lo = 0;
        int hi = lines.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            SubtitleLine line = lines.get(mid);
            if (currentMs < line.startMs) hi = mid - 1;
            else if (currentMs >= line.endMs) lo = mid + 1;
            else return mid;
        }
        return -1;
    }
}
