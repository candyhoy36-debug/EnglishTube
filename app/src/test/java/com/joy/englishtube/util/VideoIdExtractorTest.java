package com.joy.englishtube.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VideoIdExtractorTest {

    private static final String ID = "dQw4w9WgXcQ";

    @Test
    public void mobileWatch() {
        assertEquals(ID, VideoIdExtractor.extract("https://m.youtube.com/watch?v=" + ID));
    }

    @Test
    public void desktopWatchWithExtraParams() {
        assertEquals(ID, VideoIdExtractor.extract(
                "https://www.youtube.com/watch?v=" + ID + "&t=42s&list=ABC"));
    }

    @Test
    public void watchWithLeadingParam() {
        assertEquals(ID, VideoIdExtractor.extract(
                "https://www.youtube.com/watch?feature=share&v=" + ID));
    }

    @Test
    public void shortLink() {
        assertEquals(ID, VideoIdExtractor.extract("https://youtu.be/" + ID + "?t=10"));
    }

    @Test
    public void shortsLink() {
        assertEquals(ID, VideoIdExtractor.extract("https://www.youtube.com/shorts/" + ID));
    }

    @Test
    public void embedLink() {
        assertEquals(ID, VideoIdExtractor.extract("https://www.youtube.com/embed/" + ID));
    }

    @Test
    public void noScheme() {
        assertEquals(ID, VideoIdExtractor.extract("m.youtube.com/watch?v=" + ID));
    }

    @Test
    public void notAVideoUrl_returnsNull() {
        assertNull(VideoIdExtractor.extract("https://www.youtube.com/feed/subscriptions"));
        assertNull(VideoIdExtractor.extract("https://www.youtube.com/results?search_query=test"));
        assertNull(VideoIdExtractor.extract("https://www.google.com"));
        assertNull(VideoIdExtractor.extract(""));
        assertNull(VideoIdExtractor.extract(null));
    }

    @Test
    public void wrongIdLength_returnsNull() {
        assertNull(VideoIdExtractor.extract("https://www.youtube.com/watch?v=tooShort"));
        assertNull(VideoIdExtractor.extract("https://www.youtube.com/watch?v=" + ID + "X"));
    }

    @Test
    public void extractStandardWatch_acceptsStandardOnly() {
        assertEquals(ID, VideoIdExtractor.extractStandardWatch(
                "https://m.youtube.com/watch?v=" + ID));
        assertEquals(ID, VideoIdExtractor.extractStandardWatch(
                "https://www.youtube.com/watch?feature=share&v=" + ID + "&t=10"));
    }

    @Test
    public void extractStandardWatch_rejectsShortsAndYoutuBe() {
        assertNull(VideoIdExtractor.extractStandardWatch(
                "https://www.youtube.com/shorts/" + ID));
        assertNull(VideoIdExtractor.extractStandardWatch("https://youtu.be/" + ID));
        assertNull(VideoIdExtractor.extractStandardWatch(
                "https://www.youtube.com/embed/" + ID));
    }

    @Test
    public void isWatchUrl_helper() {
        assertTrue(VideoIdExtractor.isWatchUrl("https://m.youtube.com/watch?v=" + ID));
        assertFalse(VideoIdExtractor.isWatchUrl("https://m.youtube.com/feed/trending"));
    }
}
