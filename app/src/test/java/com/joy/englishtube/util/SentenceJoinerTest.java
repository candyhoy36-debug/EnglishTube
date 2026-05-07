package com.joy.englishtube.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.joy.englishtube.model.SubtitleLine;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SentenceJoinerTest {

    @Test
    public void emptyAndNullInputProduceEmptyList() {
        assertTrue(SentenceJoiner.join(null).isEmpty());
        assertTrue(SentenceJoiner.join(Collections.emptyList()).isEmpty());
    }

    @Test
    public void joinsCuesIntoSingleSentence() {
        List<SubtitleLine> cues = Arrays.asList(
                new SubtitleLine(0, 1000, "Hello there"),
                new SubtitleLine(1000, 2000, "my friend"),
                new SubtitleLine(2000, 3000, "how are you?")
        );
        List<SubtitleLine> sentences = SentenceJoiner.join(cues);
        assertEquals(1, sentences.size());
        assertEquals(0L, sentences.get(0).startMs);
        assertEquals(3000L, sentences.get(0).endMs);
        assertEquals("Hello there my friend how are you?",
                sentences.get(0).textEn);
    }

    @Test
    public void splitsAtSentenceTerminators() {
        List<SubtitleLine> cues = Arrays.asList(
                new SubtitleLine(0, 1000, "I love this."),
                new SubtitleLine(1000, 2000, "Do you?"),
                new SubtitleLine(2000, 3000, "Wow!")
        );
        List<SubtitleLine> sentences = SentenceJoiner.join(cues);
        assertEquals(3, sentences.size());
        assertEquals("I love this.", sentences.get(0).textEn);
        assertEquals("Do you?", sentences.get(1).textEn);
        assertEquals("Wow!", sentences.get(2).textEn);
        assertEquals(0L, sentences.get(0).startMs);
        assertEquals(1000L, sentences.get(0).endMs);
        assertEquals(1000L, sentences.get(1).startMs);
        assertEquals(2000L, sentences.get(1).endMs);
    }

    @Test
    public void flushesTrailingCuesWithoutTerminator() {
        List<SubtitleLine> cues = Arrays.asList(
                new SubtitleLine(0, 1000, "First sentence."),
                new SubtitleLine(1000, 2000, "Tail cue without"),
                new SubtitleLine(2000, 3000, "punctuation")
        );
        List<SubtitleLine> sentences = SentenceJoiner.join(cues);
        assertEquals(2, sentences.size());
        assertEquals("First sentence.", sentences.get(0).textEn);
        assertEquals("Tail cue without punctuation",
                sentences.get(1).textEn);
        assertEquals(1000L, sentences.get(1).startMs);
        assertEquals(3000L, sentences.get(1).endMs);
    }

    @Test
    public void handlesEllipsisTerminator() {
        List<SubtitleLine> cues = Arrays.asList(
                new SubtitleLine(0, 1000, "Wait\u2026"),
                new SubtitleLine(1000, 2000, "what?")
        );
        List<SubtitleLine> sentences = SentenceJoiner.join(cues);
        assertEquals(2, sentences.size());
        assertEquals("Wait\u2026", sentences.get(0).textEn);
        assertEquals("what?", sentences.get(1).textEn);
    }

    @Test
    public void treatsClosingQuotesAsPartOfSentenceEnd() {
        List<SubtitleLine> cues = Arrays.asList(
                new SubtitleLine(0, 1000, "He said \"hello.\""),
                new SubtitleLine(1000, 2000, "Then left.")
        );
        List<SubtitleLine> sentences = SentenceJoiner.join(cues);
        assertEquals(2, sentences.size());
        assertEquals("He said \"hello.\"", sentences.get(0).textEn);
        assertEquals("Then left.", sentences.get(1).textEn);
    }

    @Test
    public void joinsVietnameseWhenEveryCueIsTranslated() {
        SubtitleLine a = new SubtitleLine(0, 1000, "I am");
        a.textVi = "Tôi";
        SubtitleLine b = new SubtitleLine(1000, 2000, "fine.");
        b.textVi = "khoẻ.";
        List<SubtitleLine> sentences = SentenceJoiner.join(Arrays.asList(a, b));
        assertEquals(1, sentences.size());
        assertEquals("I am fine.", sentences.get(0).textEn);
        assertEquals("Tôi khoẻ.", sentences.get(0).textVi);
    }

    @Test
    public void leavesVietnameseNullWhenAnyCueIsMissingTranslation() {
        SubtitleLine a = new SubtitleLine(0, 1000, "I am");
        a.textVi = "Tôi";
        SubtitleLine b = new SubtitleLine(1000, 2000, "fine.");
        // b has no textVi
        List<SubtitleLine> sentences = SentenceJoiner.join(Arrays.asList(a, b));
        assertEquals(1, sentences.size());
        assertNull(sentences.get(0).textVi);
    }

    @Test
    public void skipsEmptyCuesGracefullyButPreservesSpan() {
        List<SubtitleLine> cues = Arrays.asList(
                new SubtitleLine(0, 500, ""),
                new SubtitleLine(500, 1500, "Hello world."),
                new SubtitleLine(1500, 2000, "")
        );
        List<SubtitleLine> sentences = SentenceJoiner.join(cues);
        assertEquals(1, sentences.size());
        assertEquals("Hello world.", sentences.get(0).textEn);
        assertEquals(0L, sentences.get(0).startMs);
        assertEquals(1500L, sentences.get(0).endMs);
    }

    @Test
    public void sentenceIndexForCueMapsForward() {
        List<SubtitleLine> cues = Arrays.asList(
                new SubtitleLine(0, 1000, "First."),
                new SubtitleLine(1000, 2000, "Second part"),
                new SubtitleLine(2000, 3000, "of two."),
                new SubtitleLine(3000, 4000, "Third!")
        );
        assertEquals(0, SentenceJoiner.sentenceIndexForCue(cues, 0));
        assertEquals(1, SentenceJoiner.sentenceIndexForCue(cues, 1));
        assertEquals(1, SentenceJoiner.sentenceIndexForCue(cues, 2));
        assertEquals(2, SentenceJoiner.sentenceIndexForCue(cues, 3));
        assertEquals(-1, SentenceJoiner.sentenceIndexForCue(cues, 99));
        assertEquals(-1, SentenceJoiner.sentenceIndexForCue(cues, -1));
    }

    // --- Strict mode (Ghép câu 2) ------------------------------------------

    @Test
    public void joinStrictSplitsAtMidCueTerminator() {
        // BBC pattern: a sentence ends inside a cue and the next sentence
        // begins in the same cue ("...sweets, right? And the whole...").
        List<SubtitleLine> cues = Arrays.asList(
                new SubtitleLine(0, 1000, "you could charge anything you liked for"),
                new SubtitleLine(1000, 2000, "your sweets, right? And the whole purpose"),
                new SubtitleLine(2000, 3000, "of capitalism and competition.")
        );
        List<SubtitleLine> sentences = SentenceJoiner.joinStrict(cues);
        assertEquals(2, sentences.size());
        assertEquals("you could charge anything you liked for your sweets, right?",
                sentences.get(0).textEn);
        assertEquals("And the whole purpose of capitalism and competition.",
                sentences.get(1).textEn);
    }

    @Test
    public void joinStrictSplitsSelfContainedCueIntoMultiple() {
        // Same input as mergesSentencesWithinSelfContainedCue, but strict
        // mode SPLITS at every internal terminator instead of merging.
        SubtitleLine a = new SubtitleLine(69_000L, 125_000L,
                "Michelle Fleury. Hello, Michelle. Hello from Washington, DC.");
        a.textVi = "Michelle Fleury. Xin chào, Michelle. Chào từ Washington, DC.";
        List<SubtitleLine> sentences = SentenceJoiner.joinStrict(Arrays.asList(a));
        assertEquals(3, sentences.size());
        assertEquals("Michelle Fleury.", sentences.get(0).textEn);
        assertEquals("Hello, Michelle.", sentences.get(1).textEn);
        assertEquals("Hello from Washington, DC.", sentences.get(2).textEn);
        // Mid-cue split → VI dropped on every resulting sentence.
        assertNull(sentences.get(0).textVi);
        assertNull(sentences.get(1).textVi);
        assertNull(sentences.get(2).textVi);
    }

    @Test
    public void joinStrictDoesNotSplitOnDecimal() {
        List<SubtitleLine> cues = Arrays.asList(
                new SubtitleLine(0, 1000, "Pi is roughly 3.14 you know."),
                new SubtitleLine(1000, 2000, "Got it?")
        );
        List<SubtitleLine> sentences = SentenceJoiner.joinStrict(cues);
        assertEquals(2, sentences.size());
        assertEquals("Pi is roughly 3.14 you know.", sentences.get(0).textEn);
    }

    @Test
    public void joinStrictKeepsEmptyInputEmpty() {
        assertTrue(SentenceJoiner.joinStrict(null).isEmpty());
        assertTrue(SentenceJoiner.joinStrict(Collections.emptyList()).isEmpty());
    }

    @Test
    public void firstCueIndexForSentenceMapsBackward() {
        List<SubtitleLine> cues = Arrays.asList(
                new SubtitleLine(0, 1000, "First."),
                new SubtitleLine(1000, 2000, "Second part"),
                new SubtitleLine(2000, 3000, "of two."),
                new SubtitleLine(3000, 4000, "Third!")
        );
        assertEquals(0, SentenceJoiner.firstCueIndexForSentence(cues, 0));
        assertEquals(1, SentenceJoiner.firstCueIndexForSentence(cues, 1));
        assertEquals(3, SentenceJoiner.firstCueIndexForSentence(cues, 2));
        assertEquals(-1, SentenceJoiner.firstCueIndexForSentence(cues, 3));
    }
}
