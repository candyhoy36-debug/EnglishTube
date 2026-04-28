package com.joy.englishtube.service;

import com.joy.englishtube.model.SubtitleLine;

import java.util.List;

/**
 * Sprint 2 will implement this. Listens to YouTubePlayer onCurrentSecond
 * and emits the {@code activeIndex} of the matching SubtitleLine to the UI.
 * Throttled to ~4Hz to balance smoothness and battery (NFR-05).
 */
public interface PlayerSyncController {

    void attach(List<SubtitleLine> lines);

    void onTick(long currentMs);

    void detach();

    interface Listener {
        void onActiveLineChanged(int activeIndex);
    }
}
