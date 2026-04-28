package com.joy.englishtube.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "bookmark",
        indices = {@Index("videoId")}
)
public class BookmarkEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String videoId = "";

    @Nullable
    public String videoTitle;

    public long startMs;

    public long endMs;

    @NonNull
    public String textEn = "";

    @Nullable
    public String textVi;

    @Nullable
    public String note;

    public long createdAt;
}
