package com.joy.englishtube.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                HistoryEntity.class,
                BookmarkEntity.class,
                SubtitleCacheEntity.class,
                SettingEntity.class
        },
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract HistoryDao historyDao();

    public abstract BookmarkDao bookmarkDao();

    public abstract SubtitleCacheDao subtitleCacheDao();

    public abstract SettingDao settingDao();

    public static AppDatabase create(Context context) {
        return Room.databaseBuilder(
                context.getApplicationContext(),
                AppDatabase.class,
                "englishtube.db"
        ).build();
    }
}
