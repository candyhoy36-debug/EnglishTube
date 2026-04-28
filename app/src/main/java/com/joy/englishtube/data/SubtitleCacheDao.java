package com.joy.englishtube.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface SubtitleCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SubtitleCacheEntity entity);

    @Query("SELECT * FROM subtitle_cache WHERE videoId = :videoId AND lang = :lang LIMIT 1")
    SubtitleCacheEntity find(String videoId, String lang);

    @Query("DELETE FROM subtitle_cache")
    void clear();
}
