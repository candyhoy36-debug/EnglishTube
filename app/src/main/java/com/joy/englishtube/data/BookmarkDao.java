package com.joy.englishtube.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface BookmarkDao {

    @Insert
    long insert(BookmarkEntity entity);

    @Update
    void update(BookmarkEntity entity);

    @Delete
    void delete(BookmarkEntity entity);

    @Query("SELECT * FROM bookmark ORDER BY videoId, startMs ASC")
    List<BookmarkEntity> getAll();

    @Query("SELECT * FROM bookmark WHERE videoId = :videoId ORDER BY startMs ASC")
    List<BookmarkEntity> getByVideo(String videoId);

    @Query("DELETE FROM bookmark")
    void clear();
}
