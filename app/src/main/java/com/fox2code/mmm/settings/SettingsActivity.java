package com.fox2code.mmm.settings;

import android.os.Bundle;

import androidx.annotation.StyleRes;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.compat.CompatActivity;
import com.fox2code.mmm.repo.RepoData;
import com.fox2code.mmm.repo.RepoManager;
import com.fox2code.mmm.utils.IntentHelper;
import com.mikepenz.aboutlibraries.LibsBuilder;

public class SettingsActivity extends CompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setDisplayHomeAsUpEnabled(true);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            setTitle(R.string.app_name);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat
            implements CompatActivity.OnBackPressedCallback {
        @Override
        @SuppressWarnings("ConstantConditions")
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            getPreferenceManager().setSharedPreferencesName("mmm");
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            findPreference("pref_theme").setOnPreferenceChangeListener((preference, newValue) -> {
                @StyleRes int themeResId;
                switch (String.valueOf(newValue)) {
                    default:
                    case "system":
                        themeResId = R.style.Theme_MagiskModuleManager;
                        break;
                    case "dark":
                        themeResId = R.style.Theme_MagiskModuleManager_Dark;
                        break;
                    case "light":
                        themeResId = R.style.Theme_MagiskModuleManager_Light;
                        break;
                }
                MainApplication.getINSTANCE().setManagerThemeResId(themeResId);
                CompatActivity.getCompatActivity(this).setThemeRecreate(themeResId);
                return true;
            });

            setRepoNameResolution("pref_repo_main", RepoManager.MAGISK_REPO,
                    "Magisk Modules Repo (Official)", RepoManager.MAGISK_REPO_HOMEPAGE);
            setRepoNameResolution("pref_repo_alt", RepoManager.MAGISK_ALT_REPO,
                    "Magisk Modules Alt Repo", RepoManager.MAGISK_ALT_REPO_HOMEPAGE);
            final LibsBuilder libsBuilder = new LibsBuilder()
                    .withFields(R.string.class.getFields()).withShowLoadingProgress(false)
                    .withLicenseShown(true).withAboutMinimalDesign(false);
            findPreference("pref_source_code").setOnPreferenceClickListener(p -> {
                IntentHelper.openUrl(p.getContext(), "https://github.com/Fox2Code/FoxMagiskModuleManager");
                return true;
            });
            findPreference("pref_show_licenses").setOnPreferenceClickListener(p -> {
                CompatActivity compatActivity = getCompatActivity(this);
                compatActivity.setOnBackPressedCallback(this);
                compatActivity.setTitle(R.string.licenses);
                compatActivity.getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.settings, libsBuilder.supportFragment())
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                        .commit();
                return true;
            });
        }

        private void setRepoNameResolution(String preferenceName,String url,
                                           String fallbackTitle,String homepage) {
            Preference preference = findPreference(preferenceName);
            if (preference == null) return;
            RepoData repoData = RepoManager.getINSTANCE().get(url);
            preference.setTitle(repoData == null ? fallbackTitle :
                    repoData.getNameOrFallback(fallbackTitle));
            preference.setOnPreferenceClickListener(p -> {
                IntentHelper.openUrl(getCompatActivity(this), homepage);
                return true;
            });
        }

        @Override
        public boolean onBackPressed(CompatActivity compatActivity) {
            compatActivity.setTitle(R.string.app_name);
            compatActivity.getSupportFragmentManager()
                    .beginTransaction().replace(R.id.settings, this)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .commit();
            return true;
        }
    }
}