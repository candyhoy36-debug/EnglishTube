package com.joy.englishtube.util;

/**
 * Centralised SharedPreferences keys + canonical default values
 * shared by SettingsFragment and PlayerActivity.
 *
 * <p>Keep keys in sync with {@code res/xml/preferences.xml}.
 */
public final class PrefsKeys {
    private PrefsKeys() {}

    public static final String LANG_MODE = "pref_default_lang_mode";
    public static final String COMBINE_MODE = "pref_default_combine_mode";
    public static final String PLAYBACK_SPEED = "pref_default_playback_speed";
    public static final String LOOP_VIDEO = "pref_default_loop_video";
    public static final String TRANSLATION_SERVICE = "pref_translation_service";
    public static final String OVERLAY_FONT_SIZE = "pref_overlay_font_size";
    public static final String OVERLAY_OPACITY = "pref_overlay_opacity";

    public static final String DEFAULT_LANG_MODE = "EN";
    public static final String DEFAULT_COMBINE_MODE = "NONE";
    public static final String DEFAULT_PLAYBACK_SPEED = "1.0";
    public static final boolean DEFAULT_LOOP_VIDEO = false;
    public static final String DEFAULT_TRANSLATION_SERVICE = "GOOGLE";
    public static final int DEFAULT_OVERLAY_FONT_SIZE_SP = 20;
    public static final int DEFAULT_OVERLAY_OPACITY_PERCENT = 60;
}
