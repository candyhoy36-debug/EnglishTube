package com.joy.englishtube.ui.player;

import android.content.Context;
import android.util.DisplayMetrics;

import androidx.recyclerview.widget.LinearSmoothScroller;

/**
 * Smoothly scrolls so the target item ends up at the very top of the
 * RecyclerView. Joy wanted the currently-playing subtitle line pinned
 * just below the video so the rest of the panel acts as a preview
 * window for the upcoming cues.
 */
public class TopSmoothScroller extends LinearSmoothScroller {

    /** Slightly slower than the default to make the auto-scroll feel calm. */
    private static final float MILLISECONDS_PER_INCH = 100f;

    public TopSmoothScroller(Context context) {
        super(context);
    }

    @Override
    protected int getVerticalSnapPreference() {
        return SNAP_TO_START;
    }

    @Override
    public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int snapPreference) {
        // Always pin the leading edge of the target view to the top of the
        // visible box, regardless of what snapPreference RecyclerView passes.
        return boxStart - viewStart;
    }

    @Override
    protected float calculateSpeedPerPixel(DisplayMetrics dm) {
        return MILLISECONDS_PER_INCH / dm.densityDpi;
    }
}
