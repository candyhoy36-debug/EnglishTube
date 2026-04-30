package com.joy.englishtube.util;

import com.joy.englishtube.model.SubtitleLine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Groups consecutive cues into sentence-level {@link SubtitleLine}s by ending
 * a sentence whenever a cue's English text terminates with one of
 * {@code .?!…} (closing quotes/parentheses are skipped before checking).
 *
 * Used by the Sprint 4 "Ghép câu" mode so the user reads/loops at the sentence
 * level instead of at the raw caption-cue level. Behavior:
 *   • Time range of a sentence = first cue's start ↔ last cue's end.
 *   • English text = all cue texts joined with single spaces, trimmed.
 *   • Vietnamese text = joined the same way, but only when EVERY cue in the
 *     group has a non-empty {@code textVi} — otherwise we leave it null so
 *     the adapter falls back to EN-only rendering (mixing partial Vi inside
 *     a sentence reads as broken).
 *   • Tail cues without a sentence terminator are still flushed as a final
 *     "sentence" so no cue is dropped.
 */
public final class SentenceJoiner {

    private SentenceJoiner() {}

    public static List<SubtitleLine> join(List<SubtitleLine> cues) {
        if (cues == null || cues.isEmpty()) return Collections.emptyList();

        List<SubtitleLine> out = new ArrayList<>();
        StringBuilder en = new StringBuilder();
        StringBuilder vi = new StringBuilder();
        long startMs = -1L;
        long endMs = -1L;
        boolean allHaveVi = true;
        boolean anyContent = false;

        for (SubtitleLine cue : cues) {
            if (cue == null) continue;
            String e = cue.textEn == null ? "" : cue.textEn.trim();
            String v = cue.textVi == null ? "" : cue.textVi.trim();

            // Carry timing even if the cue text is empty — keeps the sentence
            // span accurate when YouTube emits empty stub cues for music etc.
            if (startMs < 0L) startMs = cue.startMs;
            endMs = cue.endMs;

            if (!e.isEmpty()) {
                if (en.length() > 0) en.append(' ');
                en.append(e);
                anyContent = true;
            }
            if (!v.isEmpty()) {
                if (vi.length() > 0) vi.append(' ');
                vi.append(v);
            } else if (!e.isEmpty()) {
                // EN content present but no VI yet — sentence isn't fully
                // translated, so we won't surface a partial VI string.
                allHaveVi = false;
            }

            if (endsSentence(e)) {
                flush(out, startMs, endMs, en, vi, allHaveVi && anyContent);
                startMs = -1L;
                endMs = -1L;
                allHaveVi = true;
                anyContent = false;
            }
        }

        // Only flush a trailing fragment if it actually carries text — empty
        // tail cues (YouTube emits them for trailing silence) shouldn't
        // produce a phantom empty sentence row.
        if (en.length() > 0) {
            flush(out, startMs, endMs, en, vi, allHaveVi);
        }
        return out;
    }

    /**
     * Map a cue index back to the index of the sentence it belongs to. Used
     * when the user toggles combine mode while a cue is highlighted so the
     * equivalent sentence stays highlighted. Returns -1 if {@code cueIndex}
     * is out of range.
     */
    public static int sentenceIndexForCue(List<SubtitleLine> cues, int cueIndex) {
        if (cues == null || cueIndex < 0 || cueIndex >= cues.size()) return -1;
        int sentence = 0;
        for (int i = 0; i < cueIndex; i++) {
            SubtitleLine cue = cues.get(i);
            String e = cue == null || cue.textEn == null ? "" : cue.textEn.trim();
            if (endsSentence(e)) sentence++;
        }
        return sentence;
    }

    /**
     * Inverse of {@link #sentenceIndexForCue}: the index of the first cue
     * inside a sentence. Used when toggling combine mode off so the user
     * lands on the start of the cue group they were reading.
     */
    public static int firstCueIndexForSentence(List<SubtitleLine> cues, int sentenceIndex) {
        if (cues == null || sentenceIndex < 0) return -1;
        int sentence = 0;
        boolean atStart = true;
        for (int i = 0; i < cues.size(); i++) {
            if (atStart) {
                if (sentence == sentenceIndex) return i;
                atStart = false;
            }
            SubtitleLine cue = cues.get(i);
            String e = cue == null || cue.textEn == null ? "" : cue.textEn.trim();
            if (endsSentence(e)) {
                sentence++;
                atStart = true;
            }
        }
        return -1;
    }

    private static void flush(List<SubtitleLine> out, long startMs, long endMs,
                              StringBuilder en, StringBuilder vi, boolean allVi) {
        SubtitleLine line = new SubtitleLine(
                Math.max(0L, startMs),
                Math.max(startMs, endMs),
                en.toString());
        if (allVi && vi.length() > 0) {
            line.textVi = vi.toString();
        }
        out.add(line);
        en.setLength(0);
        vi.setLength(0);
    }

    /**
     * A cue terminates a sentence when, after stripping trailing closing
     * quotes / parentheses, its last character is one of {@code .?!…}. The
     * extra closing-mark stripping handles cases like {@code "He said
     * hello."} where the actual terminator hides behind a quote.
     */
    static boolean endsSentence(String text) {
        if (text == null || text.isEmpty()) return false;
        int i = text.length() - 1;
        while (i >= 0) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'' || c == ')' || c == ']' || c == '}'
                    || c == '\u201D' /* ” */
                    || c == '\u2019' /* ’ */
                    || c == '\u00BB' /* » */) {
                i--;
            } else {
                break;
            }
        }
        if (i < 0) return false;
        char c = text.charAt(i);
        return c == '.' || c == '?' || c == '!' || c == '\u2026' /* … */;
    }
}
