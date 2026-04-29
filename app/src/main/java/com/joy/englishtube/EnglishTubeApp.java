package com.joy.englishtube;

import android.app.Application;

import com.joy.englishtube.data.AppDatabase;
import com.joy.englishtube.service.impl.NewPipeDownloader;

import org.schabi.newpipe.extractor.NewPipe;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * Application entry-point. Initializes singletons (Room DB, OkHttp client,
 * NewPipeExtractor) lazily where possible, eagerly where required.
 */
public class EnglishTubeApp extends Application {

    private static EnglishTubeApp instance;
    private AppDatabase database;
    private OkHttpClient httpClient;

    public static EnglishTubeApp get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // NewPipeExtractor must be initialised before any extractor call is
        // made, so we do it eagerly. The downloader wraps our shared OkHttp
        // client so we keep a single connection pool across the app.
        NewPipe.init(new NewPipeDownloader(getHttpClient()));
    }

    public synchronized AppDatabase getDatabase() {
        if (database == null) {
            database = AppDatabase.create(this);
        }
        return database;
    }

    public synchronized OkHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();
        }
        return httpClient;
    }
}
