package com.joy.englishtube.ui.player;

import android.content.Context;
import android.util.DisplayMetrics;

import androidx.recyclerview.widget.LinearSmoothScroller;

/**
 * Smoothly scrolls so the target item ends up roughly in the vertical center
 * of the RecyclerView. Used by PlayerActivity to keep the active subtitle line
 * visible while a video is playing.
 */
public class CenterSmoothScroller extends LinearSmoothScroller {

    /** Slightly slower than the default to make the auto-scroll feel calm. */
    private static final float MILLISECONDS_PER_INCH = 100f;

    public CenterSmoothScroller(Context context) {
        super(context);
    }

    @Override
    public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int snapPreference) {
        return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2);
    }

    @Override
    protected float calculateSpeedPerPixel(DisplayMetrics dm) {
        return MILLISECONDS_PER_INCH / dm.densityDpi;
    }
}
