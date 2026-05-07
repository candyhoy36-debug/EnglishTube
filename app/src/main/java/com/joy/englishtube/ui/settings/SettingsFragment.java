package com.joy.englishtube.ui.settings;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.joy.englishtube.EnglishTubeApp;
import com.joy.englishtube.R;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

/**
 * Hosts every user-tunable preference. SharedPreferences is the
 * source of truth for defaults read by PlayerActivity on launch
 * (see {@link com.joy.englishtube.util.PrefsKeys}).
 *
 * <p>The "Storage" preferences trigger one-shot DB clears and
 * confirm via Toast on completion. We intentionally don't put
 * these inside the prefs persistence layer — they are commands,
 * not values, so {@link Preference#setOnPreferenceClickListener}
 * is the cleanest hook.
 */
public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState,
                                    @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        bindClick("pref_clear_history", () -> confirmClear(
                R.string.history_clear_confirm,
                () -> EnglishTubeApp.get().getDatabase().historyDao().clear()));

        bindClick("pref_clear_bookmarks", () -> confirmClear(
                R.string.bookmark_clear_confirm,
                () -> EnglishTubeApp.get().getDatabase().bookmarkDao().clear()));

        bindClick("pref_clear_subtitle_cache", () -> confirmClear(
                R.string.pref_clear_subtitle_cache_confirm,
                () -> EnglishTubeApp.get().getDatabase().subtitleCacheDao().clear()));

        Preference about = findPreference("pref_about_version");
        if (about != null) {
            about.setSummary(versionLabel());
        }
    }

    private void bindClick(@NonNull String key, @NonNull Runnable action) {
        Preference p = findPreference(key);
        if (p == null) return;
        p.setOnPreferenceClickListener(pref -> {
            action.run();
            return true;
        });
    }

    @NonNull
    private String versionLabel() {
        try {
            PackageInfo pi = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0);
            return pi.versionName + " (" + pi.versionCode + ")";
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private void confirmClear(int messageRes, @NonNull Runnable doClear) {
        new AlertDialog.Builder(requireContext())
                .setMessage(messageRes)
                .setPositiveButton(R.string.action_delete, (d, w) ->
                        EnglishTubeApp.get().getDbExecutor().execute(() -> {
                            doClear.run();
                            requireActivity().runOnUiThread(() ->
                                    Toast.makeText(requireContext(),
                                            R.string.pref_cleared,
                                            Toast.LENGTH_SHORT).show());
                        }))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
