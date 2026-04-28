package com.joy.englishtube.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface HistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(HistoryEntity entity);

    @Query("SELECT * FROM history ORDER BY lastWatchedAt DESC")
    List<HistoryEntity> getAll();

    @Query("SELECT * FROM history WHERE videoId = :videoId LIMIT 1")
    HistoryEntity findById(String videoId);

    @Query("DELETE FROM history WHERE videoId = :videoId")
    void deleteById(String videoId);

    @Query("DELETE FROM history")
    void clear();
}
