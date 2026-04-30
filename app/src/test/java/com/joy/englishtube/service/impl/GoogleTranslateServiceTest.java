package com.joy.englishtube.service.impl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.joy.englishtube.service.TranslationService;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GoogleTranslateServiceTest {

    @Test
    public void chunk_packsLinesUntilLimit() {
        List<String> sources = Arrays.asList(
                repeat('a', 1000),
                repeat('b', 1000),
                repeat('c', 1000),
                repeat('d', 3000)
        );
        List<List<String>> chunks = GoogleTranslateService.chunk(sources, 4500);
        assertEquals(2, chunks.size());
        assertEquals(3, chunks.get(0).size());
        assertEquals(1, chunks.get(1).size());
    }

    @Test
    public void chunk_emptyInput() {
        List<List<String>> chunks = GoogleTranslateService.chunk(Collections.emptyList(), 100);
        assertTrue(chunks.isEmpty());
    }

    @Test
    public void chunk_singleOversizedLineGetsItsOwnChunk() {
        List<String> sources = Arrays.asList(repeat('x', 6000));
        List<List<String>> chunks = GoogleTranslateService.chunk(sources, 4500);
        assertEquals(1, chunks.size());
        assertEquals(1, chunks.get(0).size());
    }

    @Test
    public void joinWithSentinel_preservesOrder() {
        List<String> sources = Arrays.asList("hello", "world");
        String joined = GoogleTranslateService.joinWithSentinel(sources);
        assertEquals("hello" + GoogleTranslateService.SENTINEL + "world", joined);
    }

    @Test
    public void splitOnSentinel_exactSentinelMatch() {
        String translated = "xin chào" + GoogleTranslateService.SENTINEL + "thế giới";
        String[] parts = GoogleTranslateService.splitOnSentinel(translated, 2);
        assertArrayEquals(new String[]{"xin chào", "thế giới"}, parts);
    }

    @Test
    public void splitOnSentinel_fallbackToCoreWhenNewlinesDropped() {
        // Google sometimes rewrites the surrounding \n, but keeps @@@.
        String translated = "xin chào @@@ thế giới";
        String[] parts = GoogleTranslateService.splitOnSentinel(translated, 2);
        assertEquals(2, parts.length);
        assertEquals("xin chào", parts[0]);
        assertEquals("thế giới", parts[1]);
    }

    @Test
    public void splitOnSentinel_paddedWhenModelDroppedSentinel() {
        // No sentinel at all — pad with empties up to expected.
        String translated = "everything joined";
        String[] parts = GoogleTranslateService.splitOnSentinel(translated, 3);
        assertEquals(3, parts.length);
        assertEquals("everything joined", parts[0]);
        assertEquals("", parts[1]);
        assertEquals("", parts[2]);
    }

    @Test
    public void parseTranslated_concatenatesAllSegments() throws Exception {
        // Mimic Google's nested array shape: root[0] = list of 2 segments.
        String body = "[[[\"xin chào \",\"hello \",null,null,1],"
                + "[\"thế giới\",\"world\",null,null,1]],null,\"en\"]";
        String out = GoogleTranslateService.parseTranslated(body);
        assertEquals("xin chào thế giới", out);
    }

    @Test
    public void parseTranslated_throwsOnMalformedRoot() {
        assertThrows(TranslationService.TranslationException.class,
                () -> GoogleTranslateService.parseTranslated("\"not an array\""));
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }
}
