package com.joy.englishtube.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.List;

public class CaptionsTrackResolverTest {

    @Test
    public void prefersManualEnglishOverAuto() {
        String html = wrap(
                track("https://manual.example/url", "en", null) + ","
                        + track("https://asr.example/url", "en", "asr"));
        CaptionsTrackResolver.Track t = CaptionsTrackResolver.findEnglishTrack(html);
        assertNotNull(t);
        assertEquals("https://manual.example/url", t.baseUrl);
        assertEquals(CaptionsTrackResolver.TrackKind.MANUAL, t.kind);
    }

    @Test
    public void fallsBackToAsrWhenNoManualEnglish() {
        String html = wrap(
                track("https://manual.fr.example/url", "fr", null) + ","
                        + track("https://asr.example/url", "en", "asr"));
        CaptionsTrackResolver.Track t = CaptionsTrackResolver.findEnglishTrack(html);
        assertNotNull(t);
        assertEquals("https://asr.example/url", t.baseUrl);
        assertEquals(CaptionsTrackResolver.TrackKind.ASR, t.kind);
    }

    @Test
    public void returnsNullWhenNoEnglishTrack() {
        String html = wrap(
                track("https://manual.fr.example/url", "fr", null) + ","
                        + track("https://manual.es.example/url", "es", null));
        assertNull(CaptionsTrackResolver.findEnglishTrack(html));
    }

    @Test
    public void returnsNullWhenNoCaptionsBlock() {
        String html = "<html><body>nothing here</body></html>";
        assertNull(CaptionsTrackResolver.findEnglishTrack(html));
    }

    @Test
    public void handlesNestedNameObject() {
        // Real YouTube payloads include nested {"simpleText":"…"} for `name`
        String trackWithName = "{"
                + "\"baseUrl\":\"https://manual.example/url\","
                + "\"name\":{\"simpleText\":\"English\"},"
                + "\"vssId\":\".en\","
                + "\"languageCode\":\"en\""
                + "}";
        String html = wrap(trackWithName);
        CaptionsTrackResolver.Track t = CaptionsTrackResolver.findEnglishTrack(html);
        assertNotNull(t);
        assertEquals("https://manual.example/url", t.baseUrl);
    }

    @Test
    public void unescapesUnicodeAndSlashes() {
        String track = "{"
                + "\"baseUrl\":\"https:\\/\\/youtube.com\\/api\\/timedtext?v=abc&\\u0026hl=en\","
                + "\"languageCode\":\"en\""
                + "}";
        String html = wrap(track);
        CaptionsTrackResolver.Track t = CaptionsTrackResolver.findEnglishTrack(html);
        assertNotNull(t);
        assertEquals("https://youtube.com/api/timedtext?v=abc&&hl=en", t.baseUrl);
    }

    @Test
    public void splitTopLevelObjects_handlesEmptyArray() {
        List<String> objs = CaptionsTrackResolver.splitTopLevelObjects("");
        assertEquals(0, objs.size());
    }

    @Test
    public void splitTopLevelObjects_skipsBracesInsideStrings() {
        // The string contains unescaped '{' and '}' which must NOT be counted.
        String body = "{\"name\":\"weird {value} here\",\"languageCode\":\"en\"}";
        List<String> objs = CaptionsTrackResolver.splitTopLevelObjects(body);
        assertEquals(1, objs.size());
        assertEquals(body, objs.get(0));
    }

    private static String track(String url, String lang, String kind) {
        StringBuilder sb = new StringBuilder("{\"baseUrl\":\"").append(url).append("\"");
        sb.append(",\"languageCode\":\"").append(lang).append("\"");
        if (kind != null) sb.append(",\"kind\":\"").append(kind).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private static String wrap(String tracksJson) {
        return "var ytInitialPlayerResponse = {\"captions\":{\"playerCaptionsTracklistRenderer\":{"
                + "\"captionTracks\":[" + tracksJson + "],"
                + "\"audioTracks\":[]"
                + "}}};";
    }
}
