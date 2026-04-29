package com.joy.englishtube.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.joy.englishtube.model.SubtitleLine;

import org.junit.Test;

import java.util.List;

public class VttParserTest {

    @Test
    public void parsesBasicCues() {
        String vtt = "WEBVTT\n"
                + "\n"
                + "00:00:00.000 --> 00:00:03.500\n"
                + "Hello world\n"
                + "\n"
                + "00:00:03.500 --> 00:00:05.000\n"
                + "How are you\n";
        List<SubtitleLine> lines = VttParser.parse(vtt);
        assertEquals(2, lines.size());
        assertEquals(0L, lines.get(0).startMs);
        assertEquals(3500L, lines.get(0).endMs);
        assertEquals("Hello world", lines.get(0).textEn);
        assertEquals(3500L, lines.get(1).startMs);
        assertEquals(5000L, lines.get(1).endMs);
        assertEquals("How are you", lines.get(1).textEn);
    }

    @Test
    public void joinsMultilineCue() {
        String vtt = "WEBVTT\n"
                + "\n"
                + "00:00:01.000 --> 00:00:04.000\n"
                + "Line one\n"
                + "Line two\n";
        List<SubtitleLine> lines = VttParser.parse(vtt);
        assertEquals(1, lines.size());
        assertEquals("Line one Line two", lines.get(0).textEn);
    }

    @Test
    public void stripsInlineCueTags() {
        // YouTube auto-captions wrap timing inside cues.
        String vtt = "WEBVTT\n"
                + "\n"
                + "00:00:00.500 --> 00:00:02.500\n"
                + "<c.colorE5E5E5>Hello</c><00:00:01.500><c> world</c>\n";
        List<SubtitleLine> lines = VttParser.parse(vtt);
        assertEquals(1, lines.size());
        assertEquals("Hello world", lines.get(0).textEn);
    }

    @Test
    public void decodesHtmlEntities() {
        String vtt = "WEBVTT\n"
                + "\n"
                + "00:00:00.000 --> 00:00:01.000\n"
                + "Tom &amp; Jerry &lt;3\n";
        List<SubtitleLine> lines = VttParser.parse(vtt);
        assertEquals(1, lines.size());
        assertEquals("Tom & Jerry <3", lines.get(0).textEn);
    }

    @Test
    public void ignoresOptionalCueIdentifier() {
        String vtt = "WEBVTT\n"
                + "\n"
                + "cue-1\n"
                + "00:00:00.000 --> 00:00:01.000\n"
                + "First\n"
                + "\n"
                + "cue-2\n"
                + "00:00:01.000 --> 00:00:02.000\n"
                + "Second\n";
        List<SubtitleLine> lines = VttParser.parse(vtt);
        assertEquals(2, lines.size());
        assertEquals("First", lines.get(0).textEn);
        assertEquals("Second", lines.get(1).textEn);
    }

    @Test
    public void supportsHourTimestamp() {
        String vtt = "WEBVTT\n"
                + "\n"
                + "01:02:03.456 --> 01:02:05.789\n"
                + "Long video cue\n";
        List<SubtitleLine> lines = VttParser.parse(vtt);
        assertEquals(1, lines.size());
        long expectedStart = ((1L * 60 + 2) * 60 + 3) * 1000 + 456;
        long expectedEnd = ((1L * 60 + 2) * 60 + 5) * 1000 + 789;
        assertEquals(expectedStart, lines.get(0).startMs);
        assertEquals(expectedEnd, lines.get(0).endMs);
    }

    @Test
    public void emptyInputProducesEmptyList() {
        assertTrue(VttParser.parse(null).isEmpty());
        assertTrue(VttParser.parse("").isEmpty());
        assertTrue(VttParser.parse("WEBVTT\n").isEmpty());
    }

    @Test
    public void handlesCrlfLineEndings() {
        String vtt = "WEBVTT\r\n"
                + "\r\n"
                + "00:00:00.000 --> 00:00:01.000\r\n"
                + "Windows line endings\r\n";
        List<SubtitleLine> lines = VttParser.parse(vtt);
        assertEquals(1, lines.size());
        assertEquals("Windows line endings", lines.get(0).textEn);
    }
}
