package com.joy.englishtube.model;

/**
 * One subtitle cue. Times in milliseconds relative to video start.
 * {@code textVi} is null until the translation pass populates it.
 */
public class SubtitleLine {
    public final long startMs;
    public final long endMs;
    public final String textEn;
    public String textVi;

    public SubtitleLine(long startMs, long endMs, String textEn) {
        this.startMs = startMs;
        this.endMs = endMs;
        this.textEn = textEn;
    }

    public boolean contains(long currentMs) {
        return currentMs >= startMs && currentMs < endMs;
    }

    public long durationMs() {
        return endMs - startMs;
    }
}
