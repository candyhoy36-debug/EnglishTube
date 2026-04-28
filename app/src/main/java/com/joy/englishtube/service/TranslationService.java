package com.joy.englishtube.service;

import java.util.List;

/**
 * Sprint 3 will provide two implementations:
 *  - {@code GoogleTranslateOnlineEngine} (web endpoint, default)
 *  - {@code MlKitOfflineEngine}          (on-device, fallback / user choice)
 * The engine is chosen via SettingsRepository.
 */
public interface TranslationService {

    /** Translate a batch of English strings to Vietnamese, preserving order. */
    List<String> translateEnToVi(List<String> sources) throws TranslationException;

    /** Translate a single string. Convenience wrapper. */
    default String translateEnToVi(String source) throws TranslationException {
        return translateEnToVi(java.util.Collections.singletonList(source)).get(0);
    }

    class TranslationException extends Exception {
        public TranslationException(String msg, Throwable cause) { super(msg, cause); }
    }
}
