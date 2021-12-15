package com.fox2code.mmm.settings;

import android.os.Bundle;
import android.text.method.Touch;
import android.widget.Toast;

import androidx.annotation.StyleRes;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import com.fox2code.mmm.AppUpdateManager;
import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.Constants;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.compat.CompatActivity;
import com.fox2code.mmm.compat.CompatThemeWrapper;
import com.fox2code.mmm.installer.InstallerInitializer;
import com.fox2code.mmm.repo.RepoData;
import com.fox2code.mmm.repo.RepoManager;
import com.fox2code.mmm.utils.IntentHelper;
import com.mikepenz.aboutlibraries.LibsBuilder;
import com.topjohnwu.superuser.internal.UiThreadHandler;

public class SettingsActivity extends CompatActivity {
    private static int devModeStep = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        devModeStep = 0;
        super.onCreate(savedInstanceState);
        this.setDisplayHomeAsUpEnabled(true);
        setContentView(R.layout.settings_activity);
        setTitle(R.string.app_name);
        if (savedInstanceState == null) {
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
            ListPreference themePreference = findPreference("pref_theme");
            themePreference.setSummaryProvider(p -> themePreference.getEntry());
            themePreference.setOnPreferenceClickListener(p -> {
                // You need to reboot your device at least once to be able to access dev-mode
                if (!MainApplication.isFirstBoot()) devModeStep = 1;
                return false;
            });
            themePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                devModeStep = 0;
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
            Preference forceEnglish = findPreference("pref_force_english");
            forceEnglish.setOnPreferenceChangeListener((preference, newValue) -> {
                CompatThemeWrapper compatThemeWrapper =
                        MainApplication.getINSTANCE().getMarkwonThemeContext();
                if (compatThemeWrapper != null) {
                    compatThemeWrapper.setForceEnglish(
                            Boolean.parseBoolean(String.valueOf(newValue)));
                }
                return true;
            });
            if ("dark".equals(themePreference.getValue())) {
                findPreference("pref_force_dark_terminal").setEnabled(false);
            }
            if (!MainApplication.isDeveloper()) {
                findPreference("pref_disable_low_quality_module_filter").setVisible(false);
            }
            if (InstallerInitializer.peekMagiskVersion() < Constants.MAGISK_VER_CODE_INSTALL_COMMAND
                    || !MainApplication.isDeveloper()) {
                findPreference("pref_use_magisk_install_command").setVisible(false);
            }

            setRepoNameResolution("pref_repo_main", RepoManager.MAGISK_REPO,
                    "Magisk Modules Repo (Official)", RepoManager.MAGISK_REPO_HOMEPAGE);
            setRepoNameResolution("pref_repo_alt", RepoManager.MAGISK_ALT_REPO,
                    "Magisk Modules Alt Repo", RepoManager.MAGISK_ALT_REPO_HOMEPAGE);
            final LibsBuilder libsBuilder = new LibsBuilder()
                    .withFields(R.string.class.getFields()).withShowLoadingProgress(false)
                    .withLicenseShown(true).withAboutMinimalDesign(false);
            Preference update = findPreference("pref_update");
            update.setVisible(AppUpdateManager.getAppUpdateManager().peekHasUpdate());
            update.setOnPreferenceClickListener(p -> {
                devModeStep = 0;
                IntentHelper.openUrl(p.getContext(),
                        "https://github.com/Fox2Code/FoxMagiskModuleManager/releases");
                return true;
            });
            findPreference("pref_source_code").setOnPreferenceClickListener(p -> {
                if (devModeStep == 2 && (BuildConfig.DEBUG || !MainApplication.isDeveloper())) {
                    devModeStep = 0;
                    MainApplication.getSharedPreferences().edit()
                            .putBoolean("developer", true).apply();
                    Toast.makeText(getContext(), // Tell the user something changed
                            R.string.dev_mode_enabled, Toast.LENGTH_SHORT).show();
                    return true;
                }
                IntentHelper.openUrl(p.getContext(),
                        "https://github.com/Fox2Code/FoxMagiskModuleManager");
                return true;
            });
            findPreference("pref_show_licenses").setOnPreferenceClickListener(p -> {
                devModeStep = devModeStep == 1 ? 2 : 0;
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