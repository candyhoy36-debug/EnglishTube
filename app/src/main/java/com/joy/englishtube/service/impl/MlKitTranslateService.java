package com.joy.englishtube.service.impl;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.joy.englishtube.service.TranslationService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Translate via Google ML Kit on-device, EN → VI. The model (≈30MB) is
 * downloaded the first time {@link #translateEnToVi} runs; subsequent
 * runs work fully offline.
 *
 * Concurrency: ML Kit's {@link Translator} returns Tasks; we await them
 * synchronously here so the service exposes a blocking API consistent
 * with {@link GoogleTranslateService}. Callers invoke this from a worker
 * thread (PlayerActivity's IO executor), never the main thread.
 */
public class MlKitTranslateService implements TranslationService {

    /** Hard timeout for the model download — first run on a slow network. */
    private static final long DOWNLOAD_TIMEOUT_SEC = 60L;
    /** Per-line translation timeout; ML Kit is fast on-device. */
    private static final long TRANSLATE_TIMEOUT_SEC = 10L;

    private final TranslatorOptions opts = new TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.VIETNAMESE)
            .build();

    /**
     * Wi-Fi only by default — the user is on mobile data while watching a
     * YouTube video and we don't want to consume their plan downloading a
     * 30MB model. The user can override this via Settings (Sprint 6).
     */
    private final DownloadConditions downloadConditions =
            new DownloadConditions.Builder().requireWifi().build();

    private volatile boolean modelReady = false;

    @Override
    public List<String> translateEnToVi(List<String> sources) throws TranslationException {
        if (sources == null || sources.isEmpty()) return Collections.emptyList();

        Translator translator = Translation.getClient(opts);
        try {
            ensureModelDownloaded(translator);
            List<String> out = new ArrayList<>(sources.size());
            for (String src : sources) {
                if (src == null || src.isEmpty()) {
                    out.add("");
                    continue;
                }
                out.add(translateOne(translator, src));
            }
            return out;
        } finally {
            // ML Kit translators hold native resources; close once we're
            // done with this batch so we don't leak across video changes.
            translator.close();
        }
    }

    private void ensureModelDownloaded(@NonNull Translator translator) throws TranslationException {
        if (modelReady) return;
        try {
            Tasks.await(translator.downloadModelIfNeeded(downloadConditions),
                    DOWNLOAD_TIMEOUT_SEC, TimeUnit.SECONDS);
            modelReady = true;
        } catch (ExecutionException | InterruptedException e) {
            throw new TranslationException("ml kit model download failed", e);
        } catch (TimeoutException e) {
            throw new TranslationException(
                    "ml kit model download timed out after "
                            + DOWNLOAD_TIMEOUT_SEC + "s", e);
        }
    }

    private static String translateOne(@NonNull Translator translator, @NonNull String src)
            throws TranslationException {
        try {
            return Tasks.await(translator.translate(src),
                    TRANSLATE_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (ExecutionException | InterruptedException e) {
            throw new TranslationException("ml kit translate failed", e);
        } catch (TimeoutException e) {
            throw new TranslationException(
                    "ml kit translate timed out after "
                            + TRANSLATE_TIMEOUT_SEC + "s", e);
        }
    }
}
