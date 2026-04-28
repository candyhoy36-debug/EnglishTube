package com.joy.englishtube.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;

/**
 * Cached subtitle (or translated) payload as JSON, keyed by (videoId, lang).
 * lang = "en" stores raw lines, lang = "vi" stores translated lines.
 */
@Entity(tableName = "subtitle_cache", primaryKeys = {"videoId", "lang"})
public class SubtitleCacheEntity {

    @NonNull
    public String videoId = "";

    @NonNull
    public String lang = "";

    @NonNull
    public String payloadJson = "";

    public long fetchedAt;
}
