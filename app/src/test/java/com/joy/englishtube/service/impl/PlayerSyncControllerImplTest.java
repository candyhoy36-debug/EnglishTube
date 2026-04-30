package com.joy.englishtube.service.impl;

import static org.junit.Assert.assertEquals;

import com.joy.englishtube.model.SubtitleLine;
import com.joy.englishtube.service.PlayerSyncController;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PlayerSyncControllerImplTest {

    @Test
    public void binarySearchFindsActiveLine() {
        PlayerSyncControllerImpl c = new PlayerSyncControllerImpl();
        c.attach(Arrays.asList(
                new SubtitleLine(0, 1000, "a"),
                new SubtitleLine(1000, 2500, "b"),
                new SubtitleLine(2500, 4000, "c")
        ));
        assertEquals(0, c.binarySearch(500));
        assertEquals(1, c.binarySearch(1000));
        assertEquals(1, c.binarySearch(2499));
        assertEquals(2, c.binarySearch(2500));
        assertEquals(2, c.binarySearch(3999));
    }

    @Test
    public void binarySearchReturnsMinusOneForGapsAndOutOfRange() {
        PlayerSyncControllerImpl c = new PlayerSyncControllerImpl();
        c.attach(Arrays.asList(
                new SubtitleLine(1000, 2000, "a"),
                new SubtitleLine(3000, 4000, "b")
        ));
        assertEquals(-1, c.binarySearch(0));      // before first
        assertEquals(-1, c.binarySearch(2500));   // gap
        assertEquals(-1, c.binarySearch(5000));   // after last
        assertEquals(-1, c.binarySearch(2000));   // exactly endMs of first (exclusive)
    }

    @Test
    public void binarySearchEmptyLines() {
        PlayerSyncControllerImpl c = new PlayerSyncControllerImpl();
        c.attach(java.util.Collections.emptyList());
        assertEquals(-1, c.binarySearch(0));
        assertEquals(-1, c.binarySearch(1000));
    }

    @Test
    public void onTickEmitsOnlyOnIndexChange() {
        PlayerSyncControllerImpl c = new PlayerSyncControllerImpl();
        c.attach(Arrays.asList(
                new SubtitleLine(0, 1000, "a"),
                new SubtitleLine(1000, 2000, "b")
        ));
        List<Integer> events = new ArrayList<>();
        c.setListener(events::add);

        c.onTick(0);     // active=0  → emit 0
        c.onTick(100);   // active=0  → no emit
        c.onTick(900);   // active=0  → no emit
        c.onTick(1000);  // active=1  → emit 1
        c.onTick(1500);  // active=1  → no emit
        c.onTick(2000);  // active=-1 → emit -1
        c.onTick(2100);  // active=-1 → no emit

        assertEquals(Arrays.asList(0, 1, -1), events);
    }

    @Test
    public void detachClearsState() {
        PlayerSyncControllerImpl c = new PlayerSyncControllerImpl();
        c.attach(Arrays.asList(new SubtitleLine(0, 1000, "a")));
        c.detach();
        assertEquals(-1, c.binarySearch(500));
    }
}
