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

    // -- Strict mode (Ghép câu 2) -------------------------------------------
    //
    // Walks each cue's text character by character and flushes a sentence
    // whenever a {@code .?!…} terminator is followed by whitespace or the
    // end of the cue text. Unlike {@link #join}, this splits sentences in
    // the middle of a cue ("...sweets, right? And the whole purpose"), so
    // the user gets short, terminator-aligned sentences even when the
    // SRT's cue boundaries don't line up with actual sentence endings.
    //
    // Vietnamese alignment: a sentence whose cue group is "clean" (every
    // contributing cue had its full text consumed by this one sentence
    // and a non-empty {@code textVi}) keeps its joined VI; if any cue is
    // split mid-text or lacks VI, the sentence's {@code textVi} is
    // dropped to {@code null} so the UI shows EN-only rather than a
    // broken half-translation.

    public static List<SubtitleLine> joinStrict(List<SubtitleLine> cues) {
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

            if (startMs < 0L) startMs = cue.startMs;
            endMs = cue.endMs;

            if (e.isEmpty()) continue;

            int n = e.length();
            int searchFrom = 0;
            boolean cueSplit = false;
            while (searchFrom < n) {
                int sentEnd = findSentenceEnd(e, searchFrom);
                boolean hasTerm = sentEnd > 0;
                int chunkEnd = hasTerm ? sentEnd : n;
                String chunk = e.substring(searchFrom, chunkEnd).trim();
                if (!chunk.isEmpty()) {
                    if (en.length() > 0) en.append(' ');
                    en.append(chunk);
                    anyContent = true;
                }

                boolean wholeCueConsumed = (searchFrom == 0 && chunkEnd == n);
                if (wholeCueConsumed && !cueSplit) {
                    if (!v.isEmpty()) {
                        if (vi.length() > 0) vi.append(' ');
                        vi.append(v);
                    } else {
                        allHaveVi = false;
                    }
                } else {
                    // Sentence boundary fell inside this cue — VI for the
                    // partial chunk can't be aligned, drop VI for the
                    // whole sentence.
                    allHaveVi = false;
                }

                if (!hasTerm) break;

                flush(out, startMs, endMs, en, vi, allHaveVi && anyContent);
                allHaveVi = true;
                anyContent = false;
                cueSplit = true;

                searchFrom = sentEnd;
                while (searchFrom < n && Character.isWhitespace(e.charAt(searchFrom))) {
                    searchFrom++;
                }
                if (searchFrom < n) {
                    // Next sentence starts inside the same cue; reuse its
                    // span as a coarse approximation.
                    startMs = cue.startMs;
                    endMs = cue.endMs;
                } else {
                    startMs = -1L;
                    endMs = -1L;
                }
            }
        }

        if (en.length() > 0) {
            flush(out, startMs, endMs, en, vi, allHaveVi && anyContent);
        }
        return out;
    }

    public static int sentenceIndexForCueStrict(List<SubtitleLine> cues, int cueIndex) {
        if (cues == null || cueIndex < 0 || cueIndex >= cues.size()) return -1;
        int sentence = 0;
        for (int i = 0; i < cueIndex; i++) {
            SubtitleLine cue = cues.get(i);
            String e = cue == null || cue.textEn == null ? "" : cue.textEn;
            sentence += countTerminators(e);
        }
        return sentence;
    }

    public static int firstCueIndexForSentenceStrict(List<SubtitleLine> cues, int sentenceIndex) {
        if (cues == null || sentenceIndex < 0) return -1;
        int sentence = 0;
        for (int i = 0; i < cues.size(); i++) {
            SubtitleLine cue = cues.get(i);
            String trimmed = cue == null || cue.textEn == null ? "" : cue.textEn.trim();
            if (trimmed.isEmpty()) continue;
            int terms = countTerminators(trimmed);
            // Range of sentence indices this cue contributes to:
            //   [sentence, sentence + terms - (endsClean ? 1 : 0)]
            // so that a self-contained cue ("First.") covers exactly one
            // sentence and a spillover cue with terminators inside covers
            // one more (the trailing fragment).
            boolean endsClean = endsSentence(trimmed);
            int rangeEnd = sentence + terms - (endsClean ? 1 : 0);
            if (rangeEnd < sentence) rangeEnd = sentence;
            if (sentenceIndex >= sentence && sentenceIndex <= rangeEnd) return i;
            sentence += terms;
        }
        return -1;
    }

    /**
     * Returns the exclusive end index (just past the terminator + any
     * trailing closing quotes/brackets) of the next sentence in
     * {@code text} starting at {@code from}; -1 if no terminator is
     * found before end of string. A {@code .?!…} only counts when the
     * next non-quote/-bracket character is whitespace or end-of-string,
     * so {@code "3.14"} doesn't split mid-token.
     */
    private static int findSentenceEnd(String text, int from) {
        int n = text.length();
        for (int i = from; i < n; i++) {
            char c = text.charAt(i);
            if (c == '.' || c == '?' || c == '!' || c == '\u2026') {
                int j = i + 1;
                while (j < n) {
                    char d = text.charAt(j);
                    if (d == '"' || d == '\'' || d == ')' || d == ']' || d == '}'
                            || d == '\u201D' || d == '\u2019' || d == '\u00BB') {
                        j++;
                    } else {
                        break;
                    }
                }
                if (j >= n || Character.isWhitespace(text.charAt(j))) {
                    return j;
                }
            }
        }
        return -1;
    }

    private static int countTerminators(String text) {
        if (text == null || text.isEmpty()) return 0;
        int count = 0;
        int from = 0;
        while (true) {
            int end = findSentenceEnd(text, from);
            if (end < 0) break;
            count++;
            from = end;
        }
        return count;
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
