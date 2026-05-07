package com.joy.englishtube.util;

import android.content.Context;

import com.joy.englishtube.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Tiny "ago" formatter for History / Bookmark rows. Bands chosen
 * so the resolution matches what the user actually cares about —
 * "Vừa xem" for the last minute, then minutes / hours / days.
 * Falls back to a calendar date once we're past a week.
 */
public final class RelativeTime {

    private static final long MIN_MS = 60_000L;
    private static final long HOUR_MS = 60 * MIN_MS;
    private static final long DAY_MS = 24 * HOUR_MS;
    private static final long WEEK_MS = 7 * DAY_MS;

    private RelativeTime() {}

    public static String format(Context ctx, long whenMs) {
        long delta = System.currentTimeMillis() - whenMs;
        if (delta < MIN_MS) return ctx.getString(R.string.history_just_watched);
        if (delta < HOUR_MS) {
            int mins = (int) (delta / MIN_MS);
            return ctx.getString(R.string.history_minutes_ago, mins);
        }
        if (delta < DAY_MS) {
            int hours = (int) (delta / HOUR_MS);
            return ctx.getString(R.string.history_hours_ago, hours);
        }
        if (delta < WEEK_MS) {
            int days = (int) (delta / DAY_MS);
            return ctx.getString(R.string.history_days_ago, days);
        }
        // > 1 week: just show the calendar date.
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                .format(new Date(whenMs));
    }
}
