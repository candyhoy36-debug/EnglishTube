package com.joy.englishtube.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Generic key/value store. PreferenceFragmentCompat is preferred for UI-bound settings. */
@Entity(tableName = "settings")
public class SettingEntity {

    @PrimaryKey
    @NonNull
    public String key = "";

    @Nullable
    public String value;
}
