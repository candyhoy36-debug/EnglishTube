package com.joy.englishtube.service.impl;

import android.util.Log;

import androidx.annotation.NonNull;

import com.joy.englishtube.service.TranslationService;

import java.util.List;

/**
 * Gateway service: try the online Google endpoint first because it is
 * by far the fastest path (no model download, batched per-request). When
 * Google fails — typically because the device is offline, the endpoint
 * rate-limits, or the JSON shape is malformed — fall back to the on-device
 * ML Kit translator so the user still gets Vietnamese subtitles.
 *
 * Both delegates are constructor-injected so tests can swap them out.
 */
public class AutoTranslateService implements TranslationService {

    private static final String TAG = "AutoTranslate";

    private final TranslationService primary;
    private final TranslationService fallback;

    public AutoTranslateService(@NonNull TranslationService primary,
                                @NonNull TranslationService fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public List<String> translateEnToVi(List<String> sources) throws TranslationException {
        try {
            return primary.translateEnToVi(sources);
        } catch (TranslationException primaryFail) {
            Log.w(TAG, "primary engine failed, falling back: " + primaryFail.getMessage());
            try {
                return fallback.translateEnToVi(sources);
            } catch (TranslationException fallbackFail) {
                // Surface the fallback's failure but chain the primary's
                // cause so logs show why we even tried the fallback.
                TranslationException chained = new TranslationException(
                        "both engines failed; fallback=" + fallbackFail.getMessage(),
                        primaryFail);
                chained.addSuppressed(fallbackFail);
                throw chained;
            }
        }
    }
}
