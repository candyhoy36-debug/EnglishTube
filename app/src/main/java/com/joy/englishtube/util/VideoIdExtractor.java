package com.joy.englishtube.util;

import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the 11-character YouTube videoId from common URL forms.
 * Matches:
 *   https://(m|www|music)?.youtube.com/watch?v=&lt;id&gt;...
 *   https://youtu.be/&lt;id&gt;
 *   https://(m|www).youtube.com/shorts/&lt;id&gt;
 *   https://(m|www).youtube.com/embed/&lt;id&gt;
 *   https://(m|www).youtube.com/v/&lt;id&gt;
 *
 * Returns null if no match.
 *
 * Sprint 1 only routes /watch URLs to PlayerActivity. The matcher recognizes
 * the other forms so future sprints can support them without rewriting the regex.
 */
public final class VideoIdExtractor {

    private VideoIdExtractor() {}

    /** YouTube videoIds are exactly 11 characters: A-Z, a-z, 0-9, '-', '_'. */
    private static final String ID = "([A-Za-z0-9_-]{11})";

    private static final Pattern PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.|m\\.|music\\.)?(?:"
                    + "youtube\\.com/(?:watch\\?(?:.*&)?v=|shorts/|embed/|v/|live/)"
                    + "|youtu\\.be/"
                    + ")"
                    + ID
                    + "(?:[?&#].*)?$",
            Pattern.CASE_INSENSITIVE);

    @Nullable
    public static String extract(@Nullable String url) {
        if (url == null || url.isEmpty()) return null;
        Matcher m = PATTERN.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    public static boolean isWatchUrl(@Nullable String url) {
        return extract(url) != null;
    }

    /**
     * Stricter matcher used by Sprint 1 routing: only the standard
     * {@code (m|www).youtube.com/watch?...v=ID} form. Shorts / embed / youtu.be
     * are recognized by {@link #extract(String)} for future use, but are NOT
     * intercepted in Sprint 1 (they use a different player layout).
     */
    @Nullable
    public static String extractStandardWatch(@Nullable String url) {
        if (url == null || url.isEmpty()) return null;
        Matcher m = STANDARD_WATCH.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private static final Pattern STANDARD_WATCH = Pattern.compile(
            "^https?://(?:www\\.|m\\.)?youtube\\.com/watch\\?(?:.*&)?v="
                    + ID
                    + "(?:[?&#].*)?$",
            Pattern.CASE_INSENSITIVE);
}
