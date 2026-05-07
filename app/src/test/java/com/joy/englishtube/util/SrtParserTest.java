package com.joy.englishtube.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.joy.englishtube.model.SubtitleLine;

import org.junit.Test;

import java.util.List;

public class SrtParserTest {

    @Test
    public void parses_standardSrt() {
        String srt = "1\n"
                + "00:00:01,234 --> 00:00:03,456\n"
                + "Hello world\n"
                + "\n"
                + "2\n"
                + "00:00:03,500 --> 00:00:05,100\n"
                + "Next cue\n";
        List<SubtitleLine> out = SrtParser.parse(srt);

        assertEquals(2, out.size());
        assertEquals(1234L, out.get(0).startMs);
        assertEquals(3456L, out.get(0).endMs);
        assertEquals("Hello world", out.get(0).textEn);
        assertEquals(3500L, out.get(1).startMs);
        assertEquals("Next cue", out.get(1).textEn);
    }

    @Test
    public void stripsUtf8Bom() {
        String srt = "\uFEFF1\n"
                + "00:00:00,500 --> 00:00:01,000\n"
                + "Hi\n";
        List<SubtitleLine> out = SrtParser.parse(srt);
        assertEquals(1, out.size());
        assertEquals(500L, out.get(0).startMs);
        assertEquals("Hi", out.get(0).textEn);
    }

    @Test
    public void handlesCrLfLineEndings() {
        String srt = "1\r\n"
                + "00:00:00,000 --> 00:00:02,000\r\n"
                + "line one\r\n"
                + "line two\r\n";
        List<SubtitleLine> out = SrtParser.parse(srt);
        assertEquals(1, out.size());
        // Multiple text lines collapse to a single space-joined string.
        assertEquals("line one line two", out.get(0).textEn);
    }

    @Test
    public void joinsMultiLineCueText() {
        String srt = "1\n"
                + "00:00:00,000 --> 00:00:02,000\n"
                + "first\n"
                + "second\n"
                + "third\n";
        List<SubtitleLine> out = SrtParser.parse(srt);
        assertEquals(1, out.size());
        assertEquals("first second third", out.get(0).textEn);
    }

    @Test
    public void acceptsDotAsDecimalSeparator() {
        // Some tools write 00:00:01.234 instead of 00:00:01,234.
        String srt = "1\n"
                + "00:00:01.234 --> 00:00:02.500\n"
                + "Dot decimal\n";
        List<SubtitleLine> out = SrtParser.parse(srt);
        assertEquals(1, out.size());
        assertEquals(1234L, out.get(0).startMs);
        assertEquals(2500L, out.get(0).endMs);
    }

    @Test
    public void skipsCueWithoutTimingLine() {
        String srt = "1\n"
                + "Just some text without a time\n"
                + "\n"
                + "2\n"
                + "00:00:01,000 --> 00:00:02,000\n"
                + "Second cue\n";
        List<SubtitleLine> out = SrtParser.parse(srt);
        // First block has no timing → skipped. Second block parses fine.
        assertEquals(1, out.size());
        assertEquals("Second cue", out.get(0).textEn);
    }

    @Test
    public void skipsCueWithEndBeforeStart() {
        // Bad ordering — silently dropped.
        String srt = "1\n"
                + "00:00:05,000 --> 00:00:02,000\n"
                + "Inverted\n";
        List<SubtitleLine> out = SrtParser.parse(srt);
        assertEquals(0, out.size());
    }

    @Test
    public void skipsCueWithEmptyText() {
        String srt = "1\n"
                + "00:00:01,000 --> 00:00:02,000\n"
                + "\n"
                + "2\n"
                + "00:00:03,000 --> 00:00:04,000\n"
                + "Has text\n";
        List<SubtitleLine> out = SrtParser.parse(srt);
        // First cue has no body → dropped (we don't surface empty cues).
        assertEquals(1, out.size());
        assertEquals("Has text", out.get(0).textEn);
    }

    @Test
    public void emptyStringReturnsEmpty() {
        assertTrue(SrtParser.parse("").isEmpty());
        assertTrue(SrtParser.parse(null).isEmpty());
    }

    @Test
    public void parsesUnicodeText() {
        String srt = "1\n"
                + "00:00:00,000 --> 00:00:01,000\n"
                + "Xin chào, thế giới! 你好\n";
        List<SubtitleLine> out = SrtParser.parse(srt);
        assertEquals(1, out.size());
        assertEquals("Xin chào, thế giới! 你好", out.get(0).textEn);
    }

    @Test
    public void toleratesMissingIndexNumber() {
        // No "1\n" line at the top; just timing.
        String srt = "00:00:01,000 --> 00:00:02,000\n"
                + "No index\n";
        List<SubtitleLine> out = SrtParser.parse(srt);
        assertEquals(1, out.size());
        assertEquals("No index", out.get(0).textEn);
    }
}
