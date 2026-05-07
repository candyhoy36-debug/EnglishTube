package com.joy.englishtube.util;

import com.joy.englishtube.model.SubtitleLine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses SubRip (.srt) text into a list of {@link SubtitleLine}s.
 *
 * <p>The format is loosely:
 * <pre>
 * 1
 * 00:00:01,234 --&gt; 00:00:03,456
 * Hello world
 * second line of the same cue
 *
 * 2
 * 00:00:03,500 --&gt; 00:00:05,100
 * Next cue
 * </pre>
 *
 * <p>Real-world files break the spec in many small ways (BOM at the
 * start, mixed CRLF/LF line endings, missing index numbers, blank
 * lines inside a cue, decimal separator using "." instead of ","), so
 * the parser is intentionally lenient: malformed cues are skipped
 * rather than throwing, and the index line is optional.
 */
public final class SrtParser {

    /** {@code HH:MM:SS,mmm --> HH:MM:SS,mmm} (also tolerates ".mmm"). */
    private static final Pattern TIMING = Pattern.compile(
            "(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})"
                    + "\\s*-->\\s*"
                    + "(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})");

    private SrtParser() {}

    /**
     * Parses the given SRT text. Never throws — malformed cues are
     * silently skipped so a single bad block doesn't lose the whole
     * file. Returns an empty list if no valid cues are found.
     */
    public static List<SubtitleLine> parse(String text) {
        List<SubtitleLine> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;

        // Strip UTF-8 BOM if present (file pickers often hand us one).
        if (text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        // Normalise newlines so we can split on a blank line.
        String normalised = text.replace("\r\n", "\n").replace('\r', '\n');

        // Cues are separated by one or more blank lines.
        String[] blocks = normalised.split("\n\\s*\n");
        for (String block : blocks) {
            SubtitleLine line = parseBlock(block);
            if (line != null) out.add(line);
        }
        return out;
    }

    private static SubtitleLine parseBlock(String block) {
        String[] lines = block.split("\n");
        // Find the timing line — it might be lines[0] (no index)
        // or lines[1] (standard "1\n00:00..." block). Scan to be safe.
        int timingIdx = -1;
        Matcher m = null;
        for (int i = 0; i < lines.length; i++) {
            Matcher candidate = TIMING.matcher(lines[i]);
            if (candidate.find()) {
                m = candidate;
                timingIdx = i;
                break;
            }
        }
        if (m == null) return null;

        long startMs = toMs(m.group(1), m.group(2), m.group(3), m.group(4));
        long endMs = toMs(m.group(5), m.group(6), m.group(7), m.group(8));
        if (endMs <= startMs) return null;

        StringBuilder text = new StringBuilder();
        for (int i = timingIdx + 1; i < lines.length; i++) {
            String t = lines[i].trim();
            if (t.isEmpty()) continue;
            if (text.length() > 0) text.append(' ');
            text.append(t);
        }
        String textEn = text.toString().trim();
        if (textEn.isEmpty()) return null;
        return new SubtitleLine(startMs, endMs, textEn);
    }

    private static long toMs(String hh, String mm, String ss, String mmm) {
        long h = Long.parseLong(hh);
        long m = Long.parseLong(mm);
        long s = Long.parseLong(ss);
        // Pad / truncate fractional seconds to milliseconds.
        String fracPadded = (mmm + "000").substring(0, 3);
        long ms = Long.parseLong(fracPadded);
        return ((h * 60 + m) * 60 + s) * 1000 + ms;
    }
}
