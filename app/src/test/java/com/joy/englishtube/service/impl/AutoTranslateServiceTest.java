package com.joy.englishtube.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.joy.englishtube.service.TranslationService;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class AutoTranslateServiceTest {

    @Test
    public void usesPrimaryWhenItSucceeds() throws Exception {
        TranslationService primary = sources -> Arrays.asList("vi-1", "vi-2");
        TranslationService fallback = sources -> {
            throw new AssertionError("fallback should not be called");
        };
        AutoTranslateService auto = new AutoTranslateService(primary, fallback);

        List<String> out = auto.translateEnToVi(Arrays.asList("en-1", "en-2"));
        assertEquals(Arrays.asList("vi-1", "vi-2"), out);
    }

    @Test
    public void fallsBackWhenPrimaryThrows() throws Exception {
        TranslationService primary = sources -> {
            throw new TranslationService.TranslationException("offline", null);
        };
        TranslationService fallback = sources -> Arrays.asList("vi-1");
        AutoTranslateService auto = new AutoTranslateService(primary, fallback);

        List<String> out = auto.translateEnToVi(Arrays.asList("en-1"));
        assertEquals(Arrays.asList("vi-1"), out);
    }

    @Test
    public void chainsBothFailures() {
        TranslationService.TranslationException primaryFail =
                new TranslationService.TranslationException("primary boom", null);
        TranslationService primary = sources -> { throw primaryFail; };
        TranslationService fallback = sources -> {
            throw new TranslationService.TranslationException("fallback boom", null);
        };
        AutoTranslateService auto = new AutoTranslateService(primary, fallback);

        TranslationService.TranslationException ex = assertThrows(
                TranslationService.TranslationException.class,
                () -> auto.translateEnToVi(Arrays.asList("hi")));
        assertSame(primaryFail, ex.getCause());
        assertTrue(ex.getMessage().contains("fallback boom"));
        assertEquals(1, ex.getSuppressed().length);
    }
}
