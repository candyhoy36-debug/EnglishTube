package com.joy.englishtube.service.impl;

import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Last-resort track lookup. Fetches the watch HTML page for a videoId and parses
 * the {@code playerCaptionsTracklistRenderer} JSON block to find a caption track
 * URL we can then download.
 *
 * Strategy:
 *  1. Locate the marker {@code "captionTracks":[}.
 *  2. Take the array contents up to the matching {@code ]}.
 *  3. For each track object pull {@code baseUrl}, {@code languageCode}, {@code kind}.
 *  4. Pick: prefer manual EN (no kind), then auto-EN ({@code kind:"asr"}).
 *
 * The JSON parsing is regex-based intentionally — we don't want to pull a heavy
 * JSON parser just to read 3 fields, and the format from the HTML page is stable.
 */
public final class CaptionsTrackResolver {

    public enum TrackKind { MANUAL, ASR }

    public static final class Track {
        public final String baseUrl;
        public final String languageCode;
        public final TrackKind kind;

        public Track(String baseUrl, String languageCode, TrackKind kind) {
            this.baseUrl = baseUrl;
            this.languageCode = languageCode;
            this.kind = kind;
        }
    }

    private CaptionsTrackResolver() {}

    private static final Pattern CAPTION_TRACKS_MARKER = Pattern.compile(
            "\"captionTracks\":\\s*\\[");

    private static final Pattern BASE_URL = Pattern.compile(
            "\"baseUrl\"\\s*:\\s*\"([^\"]+)\"");

    private static final Pattern LANG_CODE = Pattern.compile(
            "\"languageCode\"\\s*:\\s*\"([^\"]+)\"");

    private static final Pattern KIND = Pattern.compile(
            "\"kind\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * @return baseUrl of the chosen English track, or null if no track is present.
     *         Manual EN is preferred over auto-generated.
     */
    @Nullable
    public static Track findEnglishTrack(String html) {
        if (html == null || html.isEmpty()) return null;

        Matcher marker = CAPTION_TRACKS_MARKER.matcher(html);
        if (!marker.find()) return null;
        int arrayStart = marker.end();

        // Find the matching closing bracket of the JSON array.
        int depth = 1;
        int i = arrayStart;
        boolean inString = false;
        boolean escape = false;
        while (i < html.length() && depth > 0) {
            char c = html.charAt(i);
            if (escape) { escape = false; i++; continue; }
            if (c == '\\' && inString) { escape = true; i++; continue; }
            if (c == '"') { inString = !inString; i++; continue; }
            if (!inString) {
                if (c == '[') depth++;
                else if (c == ']') depth--;
            }
            i++;
        }
        if (depth != 0) return null;
        String arrayBody = html.substring(arrayStart, i - 1);

        Track manual = null;
        Track asr = null;

        for (String obj : splitTopLevelObjects(arrayBody)) {
            Matcher bm = BASE_URL.matcher(obj);
            if (!bm.find()) continue;
            String baseUrl = unescape(bm.group(1));

            Matcher lm = LANG_CODE.matcher(obj);
            String lang = lm.find() ? lm.group(1) : "";
            if (!lang.toLowerCase(java.util.Locale.ROOT).startsWith("en")) continue;

            Matcher km = KIND.matcher(obj);
            TrackKind kind = (km.find() && "asr".equalsIgnoreCase(km.group(1)))
                    ? TrackKind.ASR : TrackKind.MANUAL;

            Track t = new Track(baseUrl, lang, kind);
            if (kind == TrackKind.MANUAL && manual == null) manual = t;
            else if (kind == TrackKind.ASR && asr == null) asr = t;
        }
        return manual != null ? manual : asr;
    }

    /**
     * Split a JSON array body (without the surrounding brackets) into its
     * top-level object substrings. Tracks string state and brace depth so
     * nested objects (eg. {@code "name":{"simpleText":"…"}}) don't break it.
     */
    static java.util.List<String> splitTopLevelObjects(String arrayBody) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int depth = 0;
        int objStart = -1;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < arrayBody.length(); i++) {
            char c = arrayBody.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\' && inString) { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') {
                if (depth == 0) objStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    out.add(arrayBody.substring(objStart, i + 1));
                    objStart = -1;
                }
            }
        }
        return out;
    }

    /** Reverse the JSON unicode/escaped sequences embedded in baseUrl. */
    static String unescape(String s) {
        if (s == null || s.indexOf('\\') < 0) return s;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                switch (n) {
                    case '"': out.append('"'); i++; continue;
                    case '\\': out.append('\\'); i++; continue;
                    case '/': out.append('/'); i++; continue;
                    case 'n': out.append('\n'); i++; continue;
                    case 't': out.append('\t'); i++; continue;
                    case 'r': out.append('\r'); i++; continue;
                    case 'u':
                        if (i + 5 < s.length()) {
                            try {
                                int cp = Integer.parseInt(s.substring(i + 2, i + 6), 16);
                                out.append((char) cp);
                                i += 5;
                                continue;
                            } catch (NumberFormatException ignored) { }
                        }
                        break;
                    default: break;
                }
            }
            out.append(c);
        }
        return out.toString();
    }
}
