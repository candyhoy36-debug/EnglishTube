package com.joy.englishtube.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.joy.englishtube.model.SubtitleLine;

import org.junit.Test;

import java.util.List;

public class TimedTextParserTest {

    @Test
    public void parsesBasicTranscript() {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n"
                + "<transcript>"
                + "<text start=\"0\" dur=\"3.5\">Hello world</text>"
                + "<text start=\"3.5\" dur=\"2\">how are you</text>"
                + "</transcript>";
        List<SubtitleLine> lines = TimedTextParser.parse(xml);
        assertEquals(2, lines.size());
        assertEquals(0L, lines.get(0).startMs);
        assertEquals(3500L, lines.get(0).endMs);
        assertEquals("Hello world", lines.get(0).textEn);
        assertEquals(3500L, lines.get(1).startMs);
        assertEquals(5500L, lines.get(1).endMs);
        assertEquals("how are you", lines.get(1).textEn);
    }

    @Test
    public void decodesHtmlEntities() {
        String xml = "<transcript><text start=\"1\" dur=\"2\">"
                + "&quot;Hello,&quot; she said &amp; left &#39;quickly&#39;"
                + "</text></transcript>";
        List<SubtitleLine> lines = TimedTextParser.parse(xml);
        assertEquals(1, lines.size());
        assertEquals("\"Hello,\" she said & left 'quickly'", lines.get(0).textEn);
    }

    @Test
    public void stripsInlineTagsAndCollapsesWhitespace() {
        String xml = "<transcript><text start=\"0\" dur=\"3\">"
                + "Line one<br/>line two   with    spaces"
                + "</text></transcript>";
        List<SubtitleLine> lines = TimedTextParser.parse(xml);
        assertEquals("Line one line two with spaces", lines.get(0).textEn);
    }

    @Test
    public void skipsEmptyAndMalformedEntries() {
        String xml = "<transcript>"
                + "<text start=\"0\" dur=\"1\">  </text>"            // empty after trim
                + "<text dur=\"2\">no start attr</text>"               // missing start
                + "<text start=\"5\">no dur attr</text>"               // missing dur
                + "<text start=\"6\" dur=\"-1\">negative dur</text>"  // negative dur
                + "<text start=\"7\" dur=\"1\">good</text>"
                + "</transcript>";
        List<SubtitleLine> lines = TimedTextParser.parse(xml);
        assertEquals(1, lines.size());
        assertEquals("good", lines.get(0).textEn);
    }

    @Test
    public void emptyAndNullInputs() {
        assertTrue(TimedTextParser.parse("").isEmpty());
        assertTrue(TimedTextParser.parse(null).isEmpty());
        assertTrue(TimedTextParser.parse("<transcript></transcript>").isEmpty());
    }

    @Test
    public void numericEntities() {
        String xml = "<transcript><text start=\"0\" dur=\"1\">"
                + "Caf&#233; &#x2014; &#xe0; bient&#244;t"
                + "</text></transcript>";
        List<SubtitleLine> lines = TimedTextParser.parse(xml);
        assertEquals("Café — à bientôt", lines.get(0).textEn);
    }
}
