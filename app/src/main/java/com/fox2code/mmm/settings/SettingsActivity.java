package com.fox2code.mmm.settings;

import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.TwoStatePreference;

import com.fox2code.mmm.AppUpdateManager;
import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.Constants;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.OverScrollManager;
import com.fox2code.mmm.R;
import com.fox2code.mmm.compat.CompatActivity;
import com.fox2code.mmm.installer.InstallerInitializer;
import com.fox2code.mmm.repo.RepoData;
import com.fox2code.mmm.repo.RepoManager;
import com.fox2code.mmm.utils.Http;
import com.fox2code.mmm.utils.IntentHelper;

import com.fox2code.rosettax.LanguageSwitcher;
import com.mikepenz.aboutlibraries.LibsBuilder;
import com.topjohnwu.superuser.internal.UiThreadHandler;

import java.util.HashSet;
import java.util.Locale;

public class SettingsActivity extends CompatActivity {
    private static int devModeStep = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        devModeStep = 0;
        super.onCreate(savedInstanceState);
        this.setDisplayHomeAsUpEnabled(true);
        setContentView(R.layout.settings_activity);
        setTitle(R.string.app_name);
        setActionBarBackground(null);
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
            OverScrollManager.install(getListView());
            findPreference("pref_manage_repos").setOnPreferenceClickListener(p -> {
                devModeStep = 0;
                openFragment(new RepoFragment(), R.string.manage_repos_pref);
                return true;
            });
            ListPreference themePreference = findPreference("pref_theme");
            themePreference.setSummaryProvider(p -> themePreference.getEntry());
            themePreference.setOnPreferenceClickListener(p -> {
                // You need to reboot your device at least once to be able to access dev-mode
                if (!MainApplication.isFirstBoot()) devModeStep = 1;
                return false;
            });
            themePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                devModeStep = 0;
                UiThreadHandler.handler.postDelayed(() -> {
                    MainApplication.getINSTANCE().updateTheme();
                    CompatActivity.getCompatActivity(this).setThemeRecreate(
                            MainApplication.getINSTANCE().getManagerThemeResId());
                }, 1);
                return true;
            });
            Preference enableBlur = findPreference("pref_enable_blur");
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                enableBlur.setSummary(R.string.require_android_6);
                enableBlur.setEnabled(false);
            }

            Preference disableMonet = findPreference("pref_enable_monet");
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                disableMonet.setSummary(R.string.require_android_12);
                disableMonet.setEnabled(false);
            }

            findPreference("pref_dns_over_https").setOnPreferenceChangeListener((p, v) -> {
                Http.setDoh((Boolean) v);
                return true;
            });

            // Warning! Locales that are't exist will crash the app
            HashSet<String> supportedLocales = new HashSet<>();
            supportedLocales.add("cs");
            supportedLocales.add("de");
            supportedLocales.add("es-rMX");
            supportedLocales.add("et");
            supportedLocales.add("fr");
            supportedLocales.add("id");
            supportedLocales.add("ja");
            supportedLocales.add("nb-rNO");
            supportedLocales.add("pl");
            supportedLocales.add("pt-rBR");
            supportedLocales.add("ro");
            supportedLocales.add("ru");
            supportedLocales.add("sk");
            supportedLocales.add("tr");
            supportedLocales.add("vi");
            supportedLocales.add("zh-rCH");
            supportedLocales.add("zh-rTW");
            supportedLocales.add("en");

            Preference languageSelector = findPreference("pref_language_selector");
            languageSelector.setOnPreferenceClickListener(preference -> {
                LanguageSwitcher ls = new LanguageSwitcher(getActivity());
                ls.setSupportedStringLocales(supportedLocales);
                ls.showChangeLanguageDialog(getActivity());
                return true;
            });

            int nightModeFlags =
                    getContext().getResources().getConfiguration().uiMode &
                            Configuration.UI_MODE_NIGHT_MASK;
            switch (nightModeFlags) {
                case Configuration.UI_MODE_NIGHT_YES:
                    findPreference("pref_force_dark_terminal").setEnabled(false);
                    break;
                case Configuration.UI_MODE_NIGHT_NO:
                case Configuration.UI_MODE_NIGHT_UNDEFINED:
                    findPreference("pref_force_dark_terminal").setEnabled(true);
                    break;
            }
            if (!MainApplication.isDeveloper()) {
                findPreference("pref_disable_low_quality_module_filter").setVisible(false);
            }
            if (InstallerInitializer.peekMagiskVersion() < Constants.MAGISK_VER_CODE_INSTALL_COMMAND
                    || !MainApplication.isDeveloper()) {
                findPreference("pref_use_magisk_install_command").setVisible(false);
            }

            final LibsBuilder libsBuilder = new LibsBuilder().withShowLoadingProgress(false)
                    .withLicenseShown(true).withAboutMinimalDesign(false)
                    .withUiListener(new OverScrollManager.LibsOverScroll());
            Preference update = findPreference("pref_update");
            update.setVisible(BuildConfig.ENABLE_AUTO_UPDATER && (BuildConfig.DEBUG ||
                    AppUpdateManager.getAppUpdateManager().peekHasUpdate()));
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
            findPreference("pref_support").setOnPreferenceClickListener(p -> {
                devModeStep = 0;
                IntentHelper.openUrl(p.getContext(), "https://t.me/Fox2Code_Chat");
                return true;
            });
            findPreference("pref_show_licenses").setOnPreferenceClickListener(p -> {
                devModeStep = devModeStep == 1 ? 2 : 0;
                openFragment(libsBuilder.supportFragment(), R.string.licenses);
                return true;
            });
            findPreference("pref_pkg_info").setSummary(
                    BuildConfig.APPLICATION_ID + " v" +
                            BuildConfig.VERSION_NAME + " (" +
                            BuildConfig.VERSION_CODE + ")");
        }

        private void openFragment(Fragment fragment, @StringRes int title) {
            CompatActivity compatActivity = getCompatActivity(this);
            compatActivity.setOnBackPressedCallback(this);
            compatActivity.setTitle(title);
            compatActivity.getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, fragment)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .commit();
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

    public static class RepoFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            getPreferenceManager().setSharedPreferencesName("mmm");
            setPreferencesFromResource(R.xml.repo_preferences, rootKey);
            OverScrollManager.install(getListView());
            setRepoData(RepoManager.MAGISK_ALT_REPO,
                    "Magisk Modules Alt Repo", RepoManager.MAGISK_ALT_REPO_HOMEPAGE,
                    null, null,
                    "https://github.com/Magisk-Modules-Alt-Repo/submission/issues");
            // Androidacy backend not yet implemented!
            setRepoData(RepoManager.ANDROIDACY_MAGISK_REPO_ENDPOINT,
                    "Androidacy Modules Repo",
                    RepoManager.ANDROIDACY_MAGISK_REPO_HOMEPAGE,
                    "https://t.me/androidacy_discussions",
                    "https://patreon.com/androidacy",
                    "https://www.androidacy.com/module-repository-applications/");
        }

        private void setRepoData(String url,
                                 String fallbackTitle, String homepage,
                                 String supportUrl, String donateUrl,
                                 String submissionUrl) {
            String preferenceName = "pref_" + RepoManager.internalIdOfUrl(url);
            Preference preference = findPreference(preferenceName);
            if (preference == null) return;
            final RepoData repoData = RepoManager.getINSTANCE().get(url);
            preference.setTitle(repoData == null ? fallbackTitle :
                    repoData.getNameOrFallback(fallbackTitle));
            preference = findPreference(preferenceName + "_enabled");
            if (preference != null) {
                if (repoData == null) {
                    preference.setTitle(R.string.repo_disabled);
                    preference.setEnabled(false);
                } else {
                    ((TwoStatePreference) preference).setChecked(repoData.isEnabled());
                    preference.setTitle(repoData.isEnabled() ?
                            R.string.repo_enabled : R.string.repo_disabled);
                    preference.setOnPreferenceChangeListener((p, newValue) -> {
                        p.setTitle(((Boolean) newValue) ?
                                R.string.repo_enabled : R.string.repo_disabled);
                        return true;
                    });
                }
            }
            preference = findPreference(preferenceName + "_website");
            if (preference != null && homepage != null) {
                preference.setOnPreferenceClickListener(p -> {
                    if (homepage.startsWith("https://www.androidacy.com/")) {
                        IntentHelper.openUrlAndroidacy(
                                getCompatActivity(this), homepage, true);
                    } else {
                        IntentHelper.openUrl(getCompatActivity(this), homepage);
                    }
                    return true;
                });
            }
            preference = findPreference(preferenceName + "_support");
            if (preference != null && supportUrl != null) {
                preference.setOnPreferenceClickListener(p -> {
                    IntentHelper.openUrl(getCompatActivity(this), supportUrl);
                    return true;
                });
            }
            preference = findPreference(preferenceName + "_donate");
            if (preference != null && donateUrl != null) {
                preference.setOnPreferenceClickListener(p -> {
                    IntentHelper.openUrl(getCompatActivity(this), donateUrl);
                    return true;
                });
            }
            preference = findPreference(preferenceName + "_submit");
            if (preference != null && submissionUrl != null) {
                preference.setOnPreferenceClickListener(p -> {
                    if (submissionUrl.startsWith("https://www.androidacy.com/")) {
                        IntentHelper.openUrlAndroidacy(
                                getCompatActivity(this), submissionUrl, true);
                    } else {
                        IntentHelper.openUrl(getCompatActivity(this), submissionUrl);
                    }
                    return true;
                });
            }
        }
    }
}
