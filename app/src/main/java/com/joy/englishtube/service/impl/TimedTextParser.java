package com.joy.englishtube.service.impl;

import com.joy.englishtube.model.SubtitleLine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses YouTube's timedtext XML format into {@link SubtitleLine}s.
 *
 * Sample input:
 * <pre>
 * &lt;?xml version="1.0" encoding="utf-8" ?&gt;
 * &lt;transcript&gt;
 *   &lt;text start="0" dur="3.5"&gt;Hello world&lt;/text&gt;
 *   &lt;text start="3.5" dur="2.0"&gt;&amp;quot;Sure!&amp;quot;&lt;/text&gt;
 * &lt;/transcript&gt;
 * </pre>
 *
 * Returns an empty list if the response is empty or malformed (caller decides
 * whether that means "fetch failed" or "no track").
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

    public static List<SubtitleLine> parse(String xml) {
        List<SubtitleLine> result = new ArrayList<>();
        if (xml == null || xml.isEmpty()) return result;

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
