package com.joy.englishtube.util;

/**
 * Extracts the word at a character offset within a string. Used by
 * {@link com.joy.englishtube.ui.player.SubtitleAdapter} to translate a
 * long-press touch into the word the user actually pressed on so the
 * Sprint 4 Dictionary BottomSheet can look it up.
 *
 * "Word" semantics:
 *   • Letters and digits are word chars.
 *   • Apostrophes ({@code '} and {@code \u2019}) and hyphens stay inside a
 *     word, so "don't", "it's" and "well-known" come back intact.
 *   • Punctuation, spaces and quotes are boundaries.
 *   • Trailing apostrophes/hyphens (e.g. ' at the end of a contraction
 *     fragment) are trimmed off the result.
 */
public final class WordExtractor {

    private WordExtractor() {}

    public static String wordAt(String text, int offset) {
        if (text == null || text.isEmpty()) return null;
        if (offset < 0) return null;

        // Clamp the offset so a tap past the end (which {@code
        // TextView.getOffsetForPosition} can return) still resolves to the
        // last word in the string.
        int probe = Math.min(offset, text.length() - 1);
        if (!isWordChar(text.charAt(probe))) {
            // Tap landed on punctuation / whitespace — try the previous
            // character so a tap right after a word still resolves it.
            if (probe > 0 && isWordChar(text.charAt(probe - 1))) {
                probe = probe - 1;
            } else if (probe + 1 < text.length()
                    && isWordChar(text.charAt(probe + 1))) {
                probe = probe + 1;
            } else {
                return null;
            }
        }

        int start = probe;
        while (start > 0 && isWordChar(text.charAt(start - 1))) start--;
        int end = probe + 1;
        while (end < text.length() && isWordChar(text.charAt(end))) end++;

        // Trim apostrophes / hyphens that bleed onto the edges of the match
        // because they're allowed mid-word but shouldn't be in the result.
        while (start < end && isEdgeTrim(text.charAt(start))) start++;
        while (end > start && isEdgeTrim(text.charAt(end - 1))) end--;

        if (start >= end) return null;
        return text.substring(start, end);
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c)
                || c == '\''
                || c == '\u2019' /* ’ */
                || c == '-';
    }

    private static boolean isEdgeTrim(char c) {
        return c == '\'' || c == '\u2019' || c == '-';
    }
}
