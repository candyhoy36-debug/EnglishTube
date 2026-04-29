package com.joy.englishtube.service.impl;

import androidx.annotation.NonNull;

import com.joy.englishtube.model.SubtitleLine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal WebVTT cue parser.
 *
 * <p>NewPipeExtractor returns YouTube subtitles in {@code MediaFormat.VTT}
 * (or sometimes {@code TRANSCRIPT3} which is the same JSON3 layout
 * NewPipe also normalises to VTT internally). We only need the
 * timestamp + cue text — styling, voice tags and positioning are dropped.</p>
 *
 * <p>Format (per <a href="https://www.w3.org/TR/webvtt1/">WebVTT spec</a>):</p>
 * <pre>
 * WEBVTT
 *
 * 00:00:00.000 --> 00:00:03.500
 * Hello world
 *
 * 00:00:03.500 --> 00:00:05.000 align:start position:0%
 * Multiline cue
 * second line
 * </pre>
 *
 * <p>Each cue is separated by a blank line. The cue identifier (the
 * optional line before the timestamp) is ignored. Inline tags such as
 * {@code <c.colorE5E5E5>}, {@code <00:00:01.500>} or {@code <v Speaker>}
 * are stripped. HTML entities are decoded.</p>
 */
public final class VttParser {

    private static final Pattern TIMESTAMP_LINE = Pattern.compile(
            "(\\d{2,}):(\\d{2}):(\\d{2})\\.(\\d{3})\\s*-->\\s*"
                    + "(\\d{2,}):(\\d{2}):(\\d{2})\\.(\\d{3}).*");

    /** Strips WebVTT inline cue tags like {@code <c.color>}, {@code <00:00:01.500>}. */
    private static final Pattern INLINE_TAG = Pattern.compile("<[^>]+>");

    private VttParser() { }

    @NonNull
    public static List<SubtitleLine> parse(String vtt) {
        List<SubtitleLine> result = new ArrayList<>();
        if (vtt == null || vtt.isEmpty()) return result;

        // VTT files sometimes use \r\n line endings; normalise to \n only.
        String[] lines = vtt.replace("\r\n", "\n").replace('\r', '\n').split("\n");

        int i = 0;
        // Skip header (everything until the first blank line). The first
        // non-blank line should start with "WEBVTT" but some YouTube tracks
        // omit it — be lenient.
        while (i < lines.length && !lines[i].isEmpty()) {
            i++;
        }

        while (i < lines.length) {
            // Skip blank lines between cues.
            while (i < lines.length && lines[i].trim().isEmpty()) {
                i++;
            }
            if (i >= lines.length) break;

            // The cue may have an optional identifier line before the timestamp.
            // We detect the timestamp line by the "-->" arrow.
            String maybeId = lines[i];
            String tsLine;
            if (maybeId.contains("-->")) {
                tsLine = maybeId;
                i++;
            } else {
                // identifier line, skip it
                i++;
                if (i >= lines.length) break;
                tsLine = lines[i];
                i++;
            }

            Matcher m = TIMESTAMP_LINE.matcher(tsLine);
            if (!m.matches()) {
                // Not a recognisable cue header — skip until next blank line.
                while (i < lines.length && !lines[i].trim().isEmpty()) i++;
                continue;
            }

            long startMs = parseMs(m.group(1), m.group(2), m.group(3), m.group(4));
            long endMs = parseMs(m.group(5), m.group(6), m.group(7), m.group(8));

            // Collect cue body lines until next blank line.
            StringBuilder body = new StringBuilder();
            while (i < lines.length && !lines[i].trim().isEmpty()) {
                if (body.length() > 0) body.append(' ');
                body.append(lines[i]);
                i++;
            }

            String text = clean(body.toString());
            if (!text.isEmpty() && endMs > startMs) {
                result.add(new SubtitleLine(startMs, endMs, text));
            }
        }
        return result;
    }

    private static long parseMs(String hh, String mm, String ss, String ms) {
        long h = Long.parseLong(hh);
        long m = Long.parseLong(mm);
        long s = Long.parseLong(ss);
        long milli = Long.parseLong(ms);
        return ((h * 60 + m) * 60 + s) * 1000 + milli;
    }

    private static String clean(String raw) {
        String stripped = INLINE_TAG.matcher(raw).replaceAll("");
        // HTML entities — only the small set YouTube actually emits.
        stripped = stripped
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
        return stripped.trim();
    }
}
