package com.joy.englishtube.service;

import com.joy.englishtube.model.SubtitleLine;

import java.util.List;

/**
 * Watches the YouTube player's current playback time and resolves the active
 * subtitle line. The actual ticking is driven externally by
 * {@code AbstractYouTubePlayerListener.onCurrentSecond}; this class just
 * does the binary search and notifies a listener when the active index changes.
 *
 * Throttling: the listener is only fired when {@code activeIndex} actually
 * changes, so emission rate is bounded by the line count, not the tick rate.
 */
public interface PlayerSyncController {

    void attach(List<SubtitleLine> lines);

    void setListener(Listener listener);

    /** Pass the current playback time in milliseconds. */
    void onTick(long currentMs);

    void detach();

    interface Listener {
        void onActiveLineChanged(int activeIndex);
    }
}
