package com.joy.englishtube;

import android.app.Application;

import com.joy.englishtube.data.AppDatabase;

/**
 * Application entry-point. Initializes singletons (Room DB) lazily.
 * Sprint 0 skeleton — wires up the DI-free service locator pattern used by repositories.
 */
public class EnglishTubeApp extends Application {

    private static EnglishTubeApp instance;
    private AppDatabase database;

    public static EnglishTubeApp get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public synchronized AppDatabase getDatabase() {
        if (database == null) {
            database = AppDatabase.create(this);
        }
        return database;
    }
}
