package com.joy.englishtube.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface SettingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void put(SettingEntity entity);

    @Query("SELECT value FROM settings WHERE `key` = :key LIMIT 1")
    String getValue(String key);

    @Query("DELETE FROM settings WHERE `key` = :key")
    void remove(String key);
}
