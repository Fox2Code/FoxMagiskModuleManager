package com.fox2code.mmm.settings;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.TwoStatePreference;

import com.fox2code.foxcompat.FoxActivity;
import com.fox2code.foxcompat.FoxDisplay;
import com.fox2code.foxcompat.FoxViewCompat;
import com.fox2code.mmm.AppUpdateManager;
import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.Constants;
import com.fox2code.mmm.MainActivity;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.installer.InstallerInitializer;
import com.fox2code.mmm.module.ActionButtonType;
import com.fox2code.mmm.repo.CustomRepoData;
import com.fox2code.mmm.repo.CustomRepoManager;
import com.fox2code.mmm.repo.RepoData;
import com.fox2code.mmm.repo.RepoManager;
import com.fox2code.mmm.utils.Http;
import com.fox2code.mmm.utils.IntentHelper;
import com.fox2code.rosettax.LanguageActivity;
import com.fox2code.rosettax.LanguageSwitcher;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.internal.TextWatcherAdapter;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.mikepenz.aboutlibraries.LibsBuilder;
import com.topjohnwu.superuser.internal.UiThreadHandler;

import org.json.JSONException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;

public class SettingsActivity extends FoxActivity implements LanguageActivity {
    private static final String TAG = "SettingsActivity";
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

    @Override
    @SuppressLint("InlinedApi")
    public void refreshRosettaX() {
        Intent mStartActivity = new Intent(this, MainActivity.class);
        mStartActivity.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        int mPendingIntentId = 123456;
        PendingIntent mPendingIntent = PendingIntent.getActivity(this, mPendingIntentId,
                mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager mgr = (AlarmManager)this.getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
        System.exit(0); // Exit app process
    }

    public static class SettingsFragment extends PreferenceFragmentCompat
            implements FoxActivity.OnBackPressedCallback {
        @Override
        @SuppressWarnings("ConstantConditions")
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            getPreferenceManager().setSharedPreferencesName("mmm");
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
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
                    FoxActivity.getFoxActivity(this).setThemeRecreate(
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
            disableMonet.setOnPreferenceClickListener(preference -> {
                UiThreadHandler.handler.postDelayed(() -> {
                    MainApplication.getINSTANCE().updateTheme();
                    ((FoxActivity) this.requireActivity()).setThemeRecreate(
                            MainApplication.getINSTANCE().getManagerThemeResId());
                }, 1);
                return true;
            });

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
                    .withLicenseShown(true).withAboutMinimalDesign(false);
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
            FoxActivity compatActivity = getFoxActivity(this);
            compatActivity.setOnBackPressedCallback(this);
            compatActivity.setTitle(title);
            compatActivity.getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, fragment)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .commit();
        }

        @Override
        public boolean onBackPressed(FoxActivity compatActivity) {
            compatActivity.setTitle(R.string.app_name);
            compatActivity.getSupportFragmentManager()
                    .beginTransaction().replace(R.id.settings, this)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .commit();
            return true;
        }
    }

    public static class RepoFragment extends PreferenceFragmentCompat {
        private static final int CUSTOM_REPO_ENTRIES = 5;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            getPreferenceManager().setSharedPreferencesName("mmm");
            setPreferencesFromResource(R.xml.repo_preferences, rootKey);
            setRepoData(RepoManager.MAGISK_ALT_REPO);
            setRepoData(RepoManager.ANDROIDACY_MAGISK_REPO_ENDPOINT);
            setRepoData(RepoManager.DG_MAGISK_REPO_GITHUB);
            updateCustomRepoList(true);
        }

        @SuppressLint("RestrictedApi")
        public void updateCustomRepoList(boolean initial) {
            final SharedPreferences sharedPreferences = Objects.requireNonNull(
                    this.getPreferenceManager().getSharedPreferences());
            final CustomRepoManager customRepoManager =
                    RepoManager.getINSTANCE().getCustomRepoManager();
            for (int i = 0; i < CUSTOM_REPO_ENTRIES; i++) {
                CustomRepoData repoData = customRepoManager.getRepo(i);
                setRepoData(repoData, "pref_custom_repo_" + i);
                if (initial) {
                    Preference preference =
                        findPreference("pref_custom_repo_" + i + "_delete");
                    if (preference == null) continue;
                    final int index = i;
                    preference.setOnPreferenceClickListener(preference1 -> {
                        sharedPreferences.edit().putBoolean(
                                "pref_custom_repo_" + index + "_enabled", false).apply();
                        customRepoManager.removeRepo(index);
                        updateCustomRepoList(false);
                        return true;
                    });
                }
            }
            Preference preference = findPreference("pref_custom_add_repo");
            if (preference == null) return;
            preference.setVisible(customRepoManager.canAddRepo() &&
                    customRepoManager.getRepoCount() < CUSTOM_REPO_ENTRIES);
            if (initial) { // Custom repo add button part.
                preference = findPreference("pref_custom_add_repo_button");
                if (preference == null) return;
                preference.setOnPreferenceClickListener(preference1 -> {
                    final Context context = this.requireContext();
                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
                    final MaterialAutoCompleteTextView input =
                            new MaterialAutoCompleteTextView(context);
                    input.setHint(R.string.custom_url);
                    builder.setIcon(R.drawable.ic_baseline_add_box_24);
                    builder.setTitle(R.string.add_repo);
                    builder.setView(input);
                    builder.setPositiveButton("OK", (dialog, which) -> {
                        String text = String.valueOf(input.getText());
                        if (customRepoManager.canAddRepo(text)) {
                            final CustomRepoData customRepoData =
                                    customRepoManager.addRepo(text);
                            customRepoData.setEnabled(true);
                            new Thread("Add Custom Repo Thread") {
                                @Override
                                public void run() {
                                    try {
                                        customRepoData.quickPrePopulate();
                                    } catch (IOException|JSONException e) {
                                        Log.e(TAG, "Failed to preload repo values", e);
                                    }
                                    UiThreadHandler.handler.post(() -> {
                                        updateCustomRepoList(false);
                                    });
                                }
                            }.start();
                        }
                    });
                    builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
                    AlertDialog alertDialog = builder.show();
                    final Button positiveButton =
                            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    input.setValidator(new AutoCompleteTextView.Validator() {
                        @Override
                        public boolean isValid(CharSequence charSequence) {
                            return customRepoManager.canAddRepo(charSequence.toString());
                        }

                        @Override
                        public CharSequence fixText(CharSequence charSequence) {
                            return charSequence;
                        }
                    });
                    input.addTextChangedListener(new TextWatcherAdapter() {
                        @Override
                        public void onTextChanged(
                                @NonNull CharSequence charSequence, int i, int i1, int i2) {
                            positiveButton.setEnabled(customRepoManager
                                    .canAddRepo(charSequence.toString()) &&
                                    customRepoManager.getRepoCount() < CUSTOM_REPO_ENTRIES);
                        }
                    });
                    positiveButton.setEnabled(false);
                    int dp10 = FoxDisplay.dpToPixel(10),
                            dp20 = FoxDisplay.dpToPixel(20);
                    FoxViewCompat.setMargin(input, dp20, dp10, dp20, dp10);
                    return true;
                });
            }
        }

        private void setRepoData(String url) {
            final RepoData repoData = RepoManager.getINSTANCE().get(url);
            setRepoData(repoData, "pref_" + (repoData == null ?
                    RepoManager.internalIdOfUrl(url) : repoData.getPreferenceId()));
        }

        private void setRepoData(final RepoData repoData, String preferenceName) {
            if (repoData == null) {
                hideRepoData(preferenceName);
                return;
            }
            Preference preference = findPreference(preferenceName);
            if (preference == null) return;
            preference.setVisible(true);
            preference.setTitle(repoData.getName());
            preference = findPreference(preferenceName + "_enabled");
            if (preference != null) {
                ((TwoStatePreference) preference).setChecked(repoData.isEnabled());
                preference.setTitle(repoData.isEnabled() ?
                        R.string.repo_enabled : R.string.repo_disabled);
                preference.setOnPreferenceChangeListener((p, newValue) -> {
                    p.setTitle(((Boolean) newValue) ?
                            R.string.repo_enabled : R.string.repo_disabled);
                    return true;
                });
            }
            preference = findPreference(preferenceName + "_website");
            String homepage = repoData.getWebsite();
            if (preference != null) {
                if (!homepage.isEmpty()) {
                    preference.setVisible(true);
                    preference.setOnPreferenceClickListener(p -> {
                        if (homepage.startsWith("https://www.androidacy.com/")) {
                            IntentHelper.openUrlAndroidacy(
                                    getFoxActivity(this), homepage, true);
                        } else {
                            IntentHelper.openUrl(getFoxActivity(this), homepage);
                        }
                        return true;
                    });
                } else {
                    preference.setVisible(false);
                }
            }
            preference = findPreference(preferenceName + "_support");
            String supportUrl = repoData.getSupport();
            if (preference != null) {
                if (supportUrl != null && !supportUrl.isEmpty()) {
                    preference.setVisible(true);
                    preference.setIcon(ActionButtonType.supportIconForUrl(supportUrl));
                    preference.setOnPreferenceClickListener(p -> {
                        IntentHelper.openUrl(getFoxActivity(this), supportUrl);
                        return true;
                    });
                } else {
                    preference.setVisible(false);
                }
            }
            preference = findPreference(preferenceName + "_donate");
            String donateUrl = repoData.getDonate();
            if (preference != null) {
                if (donateUrl != null) {
                    preference.setVisible(true);
                    preference.setIcon(ActionButtonType.donateIconForUrl(donateUrl));
                    preference.setOnPreferenceClickListener(p -> {
                        IntentHelper.openUrl(getFoxActivity(this), donateUrl);
                        return true;
                    });
                } else {
                    preference.setVisible(false);
                }
            }
            preference = findPreference(preferenceName + "_submit");
            String submissionUrl = repoData.getSubmitModule();
            if (preference != null) {
                if (submissionUrl != null && !submissionUrl.isEmpty()) {
                    preference.setVisible(true);
                    preference.setOnPreferenceClickListener(p -> {
                        if (submissionUrl.startsWith("https://www.androidacy.com/")) {
                            IntentHelper.openUrlAndroidacy(
                                    getFoxActivity(this), submissionUrl, true);
                        } else {
                            IntentHelper.openUrl(getFoxActivity(this), submissionUrl);
                        }
                        return true;
                    });
                } else {
                    preference.setVisible(false);
                }
            }
        }

        private void hideRepoData(String preferenceName) {
            Preference preference = findPreference(preferenceName);
            if (preference == null) return;
            preference.setVisible(false);
        }
    }
}
