package com.joy.englishtube.service.impl;

import com.joy.englishtube.model.SubtitleLine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses YouTube's timedtext payloads into {@link SubtitleLine}s.
 *
 * <p>Two on-the-wire formats are accepted:</p>
 *
 * <ul>
 *   <li><b>srv1 (legacy)</b> — {@code <text start="S" dur="D">body</text>} where
 *       S/D are seconds. Returned by old endpoints / when the server honours
 *       {@code fmt=srv1}.</li>
 *   <li><b>srv3 (current default)</b> — {@code <p t="MS" d="MS">body</p>}
 *       where t/d are milliseconds. Returned when the server ignores our
 *       fmt hint, which it does for many newly-signed baseUrls.</li>
 * </ul>
 *
 * Returns an empty list if the response is empty or in an unknown format
 * (caller decides whether that means "fetch failed" or "no track").
 */
public final class TimedTextParser {

    private TimedTextParser() {}

    private static final Pattern TEXT_TAG = Pattern.compile(
            "<text\\b([^>]*)>(.*?)</text>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern ATTR_START = Pattern.compile(
            "\\bstart=\"([0-9]+(?:\\.[0-9]+)?)\"", Pattern.CASE_INSENSITIVE);

    private static final Pattern ATTR_DUR = Pattern.compile(
            "\\bdur=\"([0-9]+(?:\\.[0-9]+)?)\"", Pattern.CASE_INSENSITIVE);

    /** srv3: {@code <p t="ms" d="ms">body</p>}. */
    private static final Pattern P_TAG = Pattern.compile(
            "<p\\b([^>]*)>(.*?)</p>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern ATTR_T_MS = Pattern.compile(
            "\\bt=\"([0-9]+)\"", Pattern.CASE_INSENSITIVE);

    private static final Pattern ATTR_D_MS = Pattern.compile(
            "\\bd=\"([0-9]+)\"", Pattern.CASE_INSENSITIVE);

    /** srv3 inline word-level segment. We strip these to a flat text string. */
    private static final Pattern S_TAG = Pattern.compile(
            "<s\\b[^>]*>(.*?)</s>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static List<SubtitleLine> parse(String xml) {
        if (xml == null || xml.isEmpty()) return new ArrayList<>();

        // Try srv1 first — it's what `fmt=srv1` is supposed to give us.
        List<SubtitleLine> srv1 = parseSrv1(xml);
        if (!srv1.isEmpty()) return srv1;

        // Fall back to srv3 (current YouTube default).
        return parseSrv3(xml);
    }

    private static List<SubtitleLine> parseSrv1(String xml) {
        List<SubtitleLine> result = new ArrayList<>();
        Matcher tag = TEXT_TAG.matcher(xml);
        while (tag.find()) {
            String attrs = tag.group(1);
            String body = tag.group(2);

            float startSec = parseFloat(ATTR_START, attrs);
            float durSec = parseFloat(ATTR_DUR, attrs);
            if (startSec < 0 || durSec <= 0) continue;

            String text = decode(body);
            if (text.isEmpty()) continue;

            long startMs = (long) (startSec * 1000f);
            long endMs = startMs + (long) (durSec * 1000f);
            result.add(new SubtitleLine(startMs, endMs, text));
        }
        return result;
    }

    private static List<SubtitleLine> parseSrv3(String xml) {
        List<SubtitleLine> result = new ArrayList<>();
        Matcher tag = P_TAG.matcher(xml);
        while (tag.find()) {
            String attrs = tag.group(1);
            String body = tag.group(2);

            long startMs = parseLong(ATTR_T_MS, attrs);
            long durMs = parseLong(ATTR_D_MS, attrs);
            if (startMs < 0 || durMs <= 0) continue;

            // srv3 wraps segments in <s>; pull their inner text out before decoding.
            String flattened = S_TAG.matcher(body).replaceAll("$1");
            String text = decode(flattened);
            if (text.isEmpty()) continue;

            result.add(new SubtitleLine(startMs, startMs + durMs, text));
        }
        return result;
    }

    private static long parseLong(Pattern p, String attrs) {
        if (attrs == null) return -1L;
        Matcher m = p.matcher(attrs);
        if (!m.find()) return -1L;
        try {
            return Long.parseLong(m.group(1));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static float parseFloat(Pattern p, String attrs) {
        if (attrs == null) return -1f;
        Matcher m = p.matcher(attrs);
        if (!m.find()) return -1f;
        try {
            return Float.parseFloat(m.group(1));
        } catch (NumberFormatException e) {
            return -1f;
        }
    }

    /**
     * Decodes HTML entities and strips inline tags (eg. {@code <br/>}, font tags
     * sometimes inserted for stylized auto-captions). Whitespace is collapsed.
     */
    static String decode(String raw) {
        if (raw == null) return "";
        // Strip CDATA wrappers if present.
        String s = raw.replaceAll("<!\\[CDATA\\[(.*?)\\]\\]>", "$1");
        // Strip any nested HTML/XML tags.
        s = s.replaceAll("<[^>]+>", " ");
        // Decode common entities (numeric + named).
        s = s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
        // Numeric entities &#NN; / &#xHH;
        s = decodeNumeric(s);
        // Collapse whitespace.
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    private static final Pattern NUM_ENTITY = Pattern.compile("&#(x?)([0-9a-fA-F]+);");

    private static String decodeNumeric(String s) {
        Matcher m = NUM_ENTITY.matcher(s);
        if (!m.find()) return s;
        StringBuffer out = new StringBuffer();
        m.reset();
        while (m.find()) {
            int radix = m.group(1).isEmpty() ? 10 : 16;
            try {
                int cp = Integer.parseInt(m.group(2), radix);
                m.appendReplacement(out, Matcher.quoteReplacement(new String(Character.toChars(cp))));
            } catch (NumberFormatException e) {
                m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
            }
        }
        m.appendTail(out);
        return out.toString();
    }
}
