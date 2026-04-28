package com.joy.englishtube.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "history")
public class HistoryEntity {

    @PrimaryKey
    @NonNull
    public String videoId = "";

    @Nullable
    public String title;

    @Nullable
    public String thumbnailUrl;

    public long lastPositionMs;

    public long durationMs;

    public long lastWatchedAt;
}
