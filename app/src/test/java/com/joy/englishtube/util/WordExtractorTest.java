package com.joy.englishtube.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class WordExtractorTest {

    @Test
    public void returnsWordContainingOffset() {
        String s = "The quick brown fox";
        assertEquals("The", WordExtractor.wordAt(s, 0));
        assertEquals("The", WordExtractor.wordAt(s, 2));
        assertEquals("quick", WordExtractor.wordAt(s, 4));
        assertEquals("quick", WordExtractor.wordAt(s, 8));
        assertEquals("brown", WordExtractor.wordAt(s, 10));
        assertEquals("fox", WordExtractor.wordAt(s, 18));
    }

    @Test
    public void preservesContractions() {
        assertEquals("don't", WordExtractor.wordAt("I don't know.", 4));
        assertEquals("it's", WordExtractor.wordAt("it's fine", 0));
        // Curly apostrophe (Word/YouTube auto-replace) — also accepted.
        assertEquals("don\u2019t", WordExtractor.wordAt("I don\u2019t know", 4));
    }

    @Test
    public void preservesHyphenatedWords() {
        assertEquals("well-known", WordExtractor.wordAt("a well-known fact", 5));
        assertEquals("well-known", WordExtractor.wordAt("a well-known fact", 11));
    }

    @Test
    public void resolvesWhenTapLandsOnPunctuation() {
        // Tap on the period right after "hello".
        assertEquals("hello", WordExtractor.wordAt("Say hello.", 9));
        // Tap on the comma between two words — falls back to previous word.
        assertEquals("Hello", WordExtractor.wordAt("Hello, world!", 5));
    }

    @Test
    public void trimsTrailingPunctuationCharacters() {
        // Apostrophe at the end of a possessive fragment "boys'" should not
        // appear in the output.
        assertEquals("boys", WordExtractor.wordAt("boys' day", 1));
    }

    @Test
    public void returnsNullForOutOfRangeOrPunctuationOnly() {
        assertNull(WordExtractor.wordAt(null, 0));
        assertNull(WordExtractor.wordAt("", 0));
        assertNull(WordExtractor.wordAt("hi", -1));
        assertNull(WordExtractor.wordAt("...", 1));
        assertNull(WordExtractor.wordAt("   ", 1));
    }

    @Test
    public void resolvesAtEndOfText() {
        // TextView.getOffsetForPosition can return an offset == length
        // when the user taps past the last character.
        assertEquals("end", WordExtractor.wordAt("the end", 7));
    }

    @Test
    public void handlesNumbersAsWordChars() {
        assertEquals("3D", WordExtractor.wordAt("a 3D model", 2));
        assertEquals("2024", WordExtractor.wordAt("year 2024 was", 7));
    }
}
