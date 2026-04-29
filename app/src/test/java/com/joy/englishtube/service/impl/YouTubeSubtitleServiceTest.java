package com.joy.englishtube.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Light-touch coverage for the URL helpers that fence around the network
 * layer. The actual tier orchestration is exercised by manual smoke testing
 * against live YouTube — there's no value in mocking OkHttp for it.
 */
public class YouTubeSubtitleServiceTest {

    @Test
    public void absolutize_addsHttpsForHostRelative() {
        assertEquals(
                "https://www.youtube.com/api/timedtext?v=ID",
                YouTubeSubtitleService.absolutize("/api/timedtext?v=ID"));
    }

    @Test
    public void absolutize_addsSchemeForProtocolRelative() {
        assertEquals(
                "https://example.com/foo",
                YouTubeSubtitleService.absolutize("//example.com/foo"));
    }

    @Test
    public void absolutize_keepsAbsoluteUrlUnchanged() {
        String absolute = "https://www.youtube.com/api/timedtext?v=ID";
        assertEquals(absolute, YouTubeSubtitleService.absolutize(absolute));
    }

    @Test
    public void absolutize_passesThroughEmptyAndNull() {
        assertNull(YouTubeSubtitleService.absolutize(null));
        assertEquals("", YouTubeSubtitleService.absolutize(""));
    }
}
