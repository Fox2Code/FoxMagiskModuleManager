package com.fox2code.mmm.settings;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.TwoStatePreference;

import com.fox2code.foxcompat.FoxActivity;
import com.fox2code.foxcompat.FoxDisplay;
import com.fox2code.foxcompat.FoxViewCompat;
import com.fox2code.foxcompat.internal.FoxProcessExt;
import com.fox2code.mmm.AppUpdateManager;
import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.Constants;
import com.fox2code.mmm.MainActivity;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.androidacy.AndroidacyRepoData;
import com.fox2code.mmm.background.BackgroundUpdateChecker;
import com.fox2code.mmm.installer.InstallerInitializer;
import com.fox2code.mmm.module.ActionButtonType;
import com.fox2code.mmm.repo.CustomRepoData;
import com.fox2code.mmm.repo.CustomRepoManager;
import com.fox2code.mmm.repo.RepoData;
import com.fox2code.mmm.repo.RepoManager;
import com.fox2code.mmm.sentry.SentryMain;
import com.fox2code.mmm.utils.ExternalHelper;
import com.fox2code.mmm.utils.Http;
import com.fox2code.mmm.utils.IntentHelper;
import com.fox2code.rosettax.LanguageActivity;
import com.fox2code.rosettax.LanguageSwitcher;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.internal.TextWatcherAdapter;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.mikepenz.aboutlibraries.LibsBuilder;
import com.topjohnwu.superuser.internal.UiThreadHandler;

import org.json.JSONException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Random;

public class SettingsActivity extends FoxActivity implements LanguageActivity {
    private static final int LANGUAGE_SUPPORT_LEVEL = 1;
    private static final String TAG = "SettingsActivity";
    private static boolean devModeStepFirstBootIgnore = MainApplication.isDeveloper();
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
            getSupportFragmentManager().beginTransaction().replace(R.id.settings, new SettingsFragment()).commit();
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
        AlarmManager mgr = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
        System.exit(0); // Exit app process
    }

    @Override
    protected void onPause() {
        BackgroundUpdateChecker.onMainActivityResume(this);
        super.onPause();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements FoxActivity.OnBackPressedCallback {
        @SuppressLint("UnspecifiedImmutableFlag")
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
                if (devModeStepFirstBootIgnore || !MainApplication.isFirstBoot()) devModeStep = 1;
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
            // Crash reporting
            TwoStatePreference crashReportingPreference = findPreference("pref_crash_reporting");
            if (!SentryMain.IS_SENTRY_INSTALLED) crashReportingPreference.setVisible(false);
            crashReportingPreference.setChecked(MainApplication.isCrashReportingEnabled());
            final Object initialValue = MainApplication.isCrashReportingEnabled();
            crashReportingPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                devModeStepFirstBootIgnore = true;
                devModeStep = 0;
                if (initialValue == newValue) return true;
                // Show a dialog to restart the app
                MaterialAlertDialogBuilder materialAlertDialogBuilder = new MaterialAlertDialogBuilder(requireContext());
                materialAlertDialogBuilder.setTitle(R.string.crash_reporting_restart_title);
                materialAlertDialogBuilder.setMessage(R.string.crash_reporting_restart_message);
                materialAlertDialogBuilder.setPositiveButton(R.string.restart, (dialog, which) -> {
                    Intent mStartActivity = new Intent(requireContext(), MainActivity.class);
                    mStartActivity.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    int mPendingIntentId = 123456;
                    // If < 23, FLAG_IMMUTABLE is not available
                    PendingIntent mPendingIntent;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        mPendingIntent = PendingIntent.getActivity(requireContext(), mPendingIntentId,
                                mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    } else {
                        mPendingIntent = PendingIntent.getActivity(requireContext(), mPendingIntentId,
                                mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
                    }
                    AlarmManager mgr = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
                    mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Restarting app to save crash reporting preference: " + newValue);
                    }
                    System.exit(0); // Exit app process
                });
                // Do not reverse the change if the user cancels the dialog
                materialAlertDialogBuilder.setNegativeButton(R.string.no, (dialog, which) -> {});
                materialAlertDialogBuilder.show();
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
                    ((FoxActivity) this.requireActivity()).setThemeRecreate(MainApplication.getINSTANCE().getManagerThemeResId());
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
            supportedLocales.add("el");
            supportedLocales.add("es-rMX");
            supportedLocales.add("et");
            supportedLocales.add("fr");
            supportedLocales.add("id");
            supportedLocales.add("it");
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

            int level = this.currentLanguageLevel();
            if (level != LANGUAGE_SUPPORT_LEVEL) {
                Log.e(TAG, "Detected language level " + level + ", latest is " + LANGUAGE_SUPPORT_LEVEL);
                languageSelector.setSummary(R.string.language_support_outdated);
            } else {
                String translatedBy = this.getString(R.string.language_translated_by);
                // I don't "translate" english
                if (!("Translated by Fox2Code (Put your name here)".equals(translatedBy) ||
                        "Translated by Fox2Code".equals(translatedBy))) {
                    languageSelector.setSummary(R.string.language_translated_by);
                } else {
                    languageSelector.setSummary(null);
                }
            }

            if (!MainApplication.isDeveloper()) {
                findPreference("pref_disable_low_quality_module_filter").setVisible(false);
            }
            if (!SentryMain.IS_SENTRY_INSTALLED || !BuildConfig.DEBUG ||
                    InstallerInitializer.peekMagiskPath() == null) {
                // Hide the pref_crash option if not in debug mode - stop users from purposely crashing the app
                Objects.requireNonNull((Preference) findPreference("pref_crash")).setVisible(false);
            } else {
                findPreference("pref_crash").setOnPreferenceClickListener(preference -> {
                    // Hard crash the app
                    throw new Error("This is a test crash");
                });
            }
            if (InstallerInitializer.peekMagiskVersion() < Constants.MAGISK_VER_CODE_INSTALL_COMMAND || !MainApplication.isDeveloper()) {
                findPreference("pref_use_magisk_install_command").setVisible(false);
            }
            Preference debugNotification = findPreference("pref_background_update_check_debug");
            debugNotification.setEnabled(MainApplication.isBackgroundUpdateCheckEnabled());
            debugNotification.setVisible(MainApplication.isDeveloper() && !MainApplication.isWrapped());
            debugNotification.setOnPreferenceClickListener(preference -> {
                BackgroundUpdateChecker.postNotification(this.requireContext(), new Random().nextInt(4) + 2);
                return true;
            });
            Preference backgroundUpdateCheck = findPreference("pref_background_update_check");
            backgroundUpdateCheck.setVisible(!MainApplication.isWrapped());
            backgroundUpdateCheck.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = Boolean.parseBoolean(String.valueOf(newValue));
                debugNotification.setEnabled(enabled);
                if (!enabled) {
                    BackgroundUpdateChecker.onMainActivityResume(this.requireContext());
                }
                return true;
            });

            final LibsBuilder libsBuilder = new LibsBuilder().withShowLoadingProgress(false).withLicenseShown(true).withAboutMinimalDesign(false);
            Preference update = findPreference("pref_update");
            update.setVisible(BuildConfig.ENABLE_AUTO_UPDATER && (BuildConfig.DEBUG || AppUpdateManager.getAppUpdateManager().peekHasUpdate()));
            update.setOnPreferenceClickListener(p -> {
                devModeStep = 0;
                IntentHelper.openUrl(p.getContext(), "https://github.com/Fox2Code/FoxMagiskModuleManager/releases");
                return true;
            });
            if (BuildConfig.DEBUG || BuildConfig.ENABLE_AUTO_UPDATER) {
                findPreference("pref_report_bug").setOnPreferenceClickListener(p -> {
                    devModeStep = 0;
                    devModeStepFirstBootIgnore = true;
                    IntentHelper.openUrl(p.getContext(), "https://github.com/Fox2Code/FoxMagiskModuleManager/issues");
                    return true;
                });
            } else {
                findPreference("pref_report_bug").setVisible(false);
            }
            findPreference("pref_source_code").setOnPreferenceClickListener(p -> {
                if (devModeStep == 2) {
                    devModeStep = 0;
                    if (MainApplication.isDeveloper() && !BuildConfig.DEBUG) {
                        MainApplication.getSharedPreferences().edit().putBoolean("developer", false).apply();
                        Toast.makeText(getContext(), // Tell the user something changed
                                R.string.dev_mode_disabled, Toast.LENGTH_SHORT).show();
                    } else {
                        MainApplication.getSharedPreferences().edit().putBoolean("developer", true).apply();
                        Toast.makeText(getContext(), // Tell the user something changed
                                R.string.dev_mode_enabled, Toast.LENGTH_SHORT).show();
                    }
                    ExternalHelper.INSTANCE.refreshHelper(getContext());
                    return true;
                }
                IntentHelper.openUrl(p.getContext(), "https://github.com/Fox2Code/FoxMagiskModuleManager");
                return true;
            });
            findPreference("pref_support").setOnPreferenceClickListener(p -> {
                devModeStep = 0;
                IntentHelper.openUrl(p.getContext(), "https://t.me/Fox2Code_Chat");
                return true;
            });
            findPreference("pref_show_licenses").setOnPreferenceClickListener(p -> {
                devModeStep = devModeStep == 1 ? 2 : 0;
                BackgroundUpdateChecker.onMainActivityResume(this.requireContext());
                openFragment(libsBuilder.supportFragment(), R.string.licenses);
                return true;
            });
            findPreference("pref_pkg_info").setSummary(BuildConfig.APPLICATION_ID +
                    " v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")" +
                    getRepackageState()); // State may not be "I am just running from myself as myself"
        }

        @SuppressLint("RestrictedApi")
        private String getRepackageState() {
            Application initialApplication = null;
            try {
                initialApplication = FoxProcessExt.getInitialApplication();
            } catch (Throwable ignored) {
            }
            String realPackageName;
            if (initialApplication != null) {
                realPackageName = initialApplication.getPackageName();
            } else {
                realPackageName = this.requireContext().getPackageName();
            }
            if (BuildConfig.APPLICATION_ID.equals(realPackageName)) return "";
            return "\n" + this.getString(FoxProcessExt.isRootLoader() ?
                    R.string.repackaged_as : R.string.wrapped_from) + realPackageName;
        }

        private void openFragment(Fragment fragment, @StringRes int title) {
            FoxActivity compatActivity = getFoxActivity(this);
            compatActivity.setOnBackPressedCallback(this);
            compatActivity.setTitle(title);
            compatActivity.getSupportFragmentManager().beginTransaction().replace(R.id.settings, fragment).setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE).commit();
        }

        @Override
        public boolean onBackPressed(FoxActivity compatActivity) {
            compatActivity.setTitle(R.string.app_name);
            compatActivity.getSupportFragmentManager().beginTransaction().replace(R.id.settings, this).setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE).commit();
            return true;
        }

        private int currentLanguageLevel() {
            int declaredLanguageLevel = this.getResources().getInteger(R.integer.language_support_level);
            if (declaredLanguageLevel != LANGUAGE_SUPPORT_LEVEL) return declaredLanguageLevel;
            if (!this.getResources().getConfiguration().locale.getLanguage().equals("en") &&
                    this.getResources().getString(R.string.notification_update_pref).equals("Background modules update check") &&
                    this.getResources().getString(R.string.notification_update_desc).equals("May increase battery usage")) {
                return 0;
            }
            return LANGUAGE_SUPPORT_LEVEL;
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
            onCreatePreferencesAndroidacy();
        }

        @SuppressLint({"RestrictedApi", "UnspecifiedImmutableFlag"})
        public void onCreatePreferencesAndroidacy() {
            // Bind the pref_show_captcha_webview to captchaWebview('https://production-api.androidacy.com/')
            // Also require dev modeowCaptchaWebview.setVisible(false);
            Preference androidacyTestMode = Objects.requireNonNull(findPreference("pref_androidacy_test_mode"));
            if (!MainApplication.isDeveloper()) {
                androidacyTestMode.setVisible(false);
            } else {
                // Show a warning if user tries to enable test mode
                androidacyTestMode.setOnPreferenceChangeListener((preference, newValue) -> {
                    if (Boolean.parseBoolean(String.valueOf(newValue))) {
                        // Use MaterialAlertDialogBuilder
                        new MaterialAlertDialogBuilder(this.requireContext())
                                .setTitle(R.string.warning)
                                .setMessage(R.string.androidacy_test_mode_warning)
                                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                                    // User clicked OK button
                                    MainApplication.getSharedPreferences().edit().putBoolean("androidacy_test_mode", true).apply();
                                    // Check the switch
                                    Intent mStartActivity = new Intent(requireContext(), MainActivity.class);
                                    mStartActivity.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                    int mPendingIntentId = 123456;
                                    // If < 23, FLAG_IMMUTABLE is not available
                                    PendingIntent mPendingIntent;
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        mPendingIntent = PendingIntent.getActivity(requireContext(), mPendingIntentId,
                                                mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                                    } else {
                                        mPendingIntent = PendingIntent.getActivity(requireContext(), mPendingIntentId,
                                                mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
                                    }
                                    AlarmManager mgr = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
                                    mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
                                    if (BuildConfig.DEBUG) {
                                        Log.d(TAG, "Restarting app to save staging endpoint preference: " + newValue);
                                    }
                                    System.exit(0); // Exit app process
                                })
                                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                                    // User cancelled the dialog
                                    // Uncheck the switch
                                    SwitchPreferenceCompat switchPreferenceCompat = (SwitchPreferenceCompat) androidacyTestMode;
                                    switchPreferenceCompat.setChecked(false);
                                    // There's probably a better way to do this than duplicate code but I'm too lazy to figure it out
                                    MainApplication.getSharedPreferences().edit().putBoolean("androidacy_test_mode", false).apply();
                                })
                                .show();
                    } else {
                        MainApplication.getSharedPreferences().edit().putBoolean("androidacy_test_mode", false).apply();
                        // Show dialog to restart app with ok button
                        new MaterialAlertDialogBuilder(this.requireContext())
                                .setTitle(R.string.warning)
                                .setMessage(R.string.androidacy_test_mode_disable_warning)
                                .setNeutralButton(android.R.string.ok, (dialog, which) -> {
                                    // User clicked OK button
                                    Intent mStartActivity = new Intent(requireContext(), MainActivity.class);
                                    mStartActivity.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                    int mPendingIntentId = 123456;
                                    // If < 23, FLAG_IMMUTABLE is not available
                                    PendingIntent mPendingIntent;
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        mPendingIntent = PendingIntent.getActivity(requireContext(), mPendingIntentId,
                                                mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                                    } else {
                                        mPendingIntent = PendingIntent.getActivity(requireContext(), mPendingIntentId,
                                                mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
                                    }
                                    AlarmManager mgr = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
                                    mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
                                    if (BuildConfig.DEBUG) {
                                        Log.d(TAG, "Restarting app to save staging endpoint preference: " + newValue);
                                    }
                                    System.exit(0); // Exit app process
                                })
                                .show();
                    }
                    return true;
                });
            }
            String[] originalApiKeyRef = new String[]{
                    MainApplication.getSharedPreferences().getString("pref_androidacy_api_token", "")};
            // Create the pref_androidacy_repo_api_key text input with validation
            EditTextPreference prefAndroidacyRepoApiKey = findPreference("pref_androidacy_repo_api_key");
            assert prefAndroidacyRepoApiKey != null;
            prefAndroidacyRepoApiKey.setOnBindEditTextListener(editText -> {
                editText.setSingleLine();
                // Make the single line wrap
                editText.setHorizontallyScrolling(false);
                // Set the height to the height of 2 lines
                editText.setHeight(editText.getLineHeight() * 3);
                // Make ok button say "Save"
                editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
            });
            prefAndroidacyRepoApiKey.setPositiveButtonText(R.string.save_api_key);
            prefAndroidacyRepoApiKey.setOnPreferenceChangeListener((preference, newValue) -> {
                if (originalApiKeyRef[0].equals(newValue)) return true; // Skip if nothing changed.
                // Curious if this actually works - so crash the app on purpose
                // throw new RuntimeException("This is a test crash");
                // get original api key
                String apiKey = String.valueOf(newValue);
                // Show snack bar with indeterminate progress
                Snackbar.make(requireView(), R.string.checking_api_key, Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.cancel, v -> {
                    // Restore the original api key
                    prefAndroidacyRepoApiKey.setText(originalApiKeyRef[0]);
                }).show();
                // Check the API key on a background thread
                new Thread(() -> {
                    // If key is empty, just remove it and change the text of the snack bar
                    if (apiKey.isEmpty()) {
                        MainApplication.getSharedPreferences().edit().remove(
                                "pref_androidacy_repo_api_key").apply();
                        new Handler(Looper.getMainLooper()).post(() -> Snackbar.make(requireView(),
                                R.string.api_key_removed, Snackbar.LENGTH_SHORT).show());
                    } else {
                        // If key < 64 chars, it's not valid
                        if (apiKey.length() < 64) {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                Snackbar.make(requireView(), R.string.api_key_invalid, Snackbar.LENGTH_SHORT).show();
                                // Save the original key
                                MainApplication.getSharedPreferences().edit().putString(
                                        "pref_androidacy_api_token", originalApiKeyRef[0]).apply();
                                // Re-show the dialog with an error
                                prefAndroidacyRepoApiKey.performClick();
                                // Show error
                                prefAndroidacyRepoApiKey.setDialogMessage(getString(R.string.api_key_invalid));
                            });
                        } else {
                            boolean valid = false;
                            try {
                                valid = AndroidacyRepoData.getInstance().isValidToken(apiKey);
                            } catch (IOException ignored) {}
                            // If the key is valid, save it
                            if (valid) {
                                originalApiKeyRef[0] = apiKey;
                                RepoManager.getINSTANCE().getAndroidacyRepoData().setToken(apiKey);
                                MainApplication.getSharedPreferences().edit().putString(
                                        "pref_androidacy_repo_api_key", apiKey).apply();
                                new Handler(Looper.getMainLooper()).post(() -> Snackbar.make(requireView(),
                                        R.string.api_key_valid, Snackbar.LENGTH_SHORT).show());
                            } else {
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    Snackbar.make(requireView(), R.string.api_key_invalid, Snackbar.LENGTH_SHORT).show();
                                    // Save the original key
                                    MainApplication.getSharedPreferences().edit().putString(
                                            "pref_androidacy_api_token", originalApiKeyRef[0]).apply();
                                    // Re-show the dialog with an error
                                    prefAndroidacyRepoApiKey.performClick();
                                    // Show error
                                    prefAndroidacyRepoApiKey.setDialogMessage(getString(R.string.api_key_invalid));
                                });
                            }
                        }
                    }
                }).start();
                return true;
            });
        }

        @SuppressLint("RestrictedApi")
        public void updateCustomRepoList(boolean initial) {
            final SharedPreferences sharedPreferences = Objects.requireNonNull(this.getPreferenceManager().getSharedPreferences());
            final CustomRepoManager customRepoManager = RepoManager.getINSTANCE().getCustomRepoManager();
            for (int i = 0; i < CUSTOM_REPO_ENTRIES; i++) {
                CustomRepoData repoData = customRepoManager.getRepo(i);
                setRepoData(repoData, "pref_custom_repo_" + i);
                if (initial) {
                    Preference preference = findPreference("pref_custom_repo_" + i + "_delete");
                    if (preference == null) continue;
                    final int index = i;
                    preference.setOnPreferenceClickListener(preference1 -> {
                        sharedPreferences.edit().putBoolean("pref_custom_repo_" + index + "_enabled", false).apply();
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
                    final MaterialAutoCompleteTextView input = new MaterialAutoCompleteTextView(context);
                    input.setHint(R.string.custom_url);
                    builder.setIcon(R.drawable.ic_baseline_add_box_24);
                    builder.setTitle(R.string.add_repo);
                    builder.setView(input);
                    builder.setPositiveButton("OK", (dialog, which) -> {
                        String text = String.valueOf(input.getText());
                        if (customRepoManager.canAddRepo(text)) {
                            final CustomRepoData customRepoData = customRepoManager.addRepo(text);
                            customRepoData.setEnabled(true);
                            new Thread("Add Custom Repo Thread") {
                                @Override
                                public void run() {
                                    try {
                                        customRepoData.quickPrePopulate();
                                    } catch (IOException | JSONException e) {
                                        Log.e(TAG, "Failed to preload repo values", e);
                                    }
                                    UiThreadHandler.handler.post(() -> updateCustomRepoList(false));
                                }
                            }.start();
                        }
                    });
                    builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
                    AlertDialog alertDialog = builder.show();
                    final Button positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
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
                        public void onTextChanged(@NonNull CharSequence charSequence, int i, int i1, int i2) {
                            positiveButton.setEnabled(customRepoManager.canAddRepo(charSequence.toString()) &&
                                    customRepoManager.getRepoCount() < CUSTOM_REPO_ENTRIES);
                        }
                    });
                    positiveButton.setEnabled(false);
                    int dp10 = FoxDisplay.dpToPixel(10), dp20 = FoxDisplay.dpToPixel(20);
                    FoxViewCompat.setMargin(input, dp20, dp10, dp20, dp10);
                    return true;
                });
            }
        }

        private void setRepoData(String url) {
            final RepoData repoData = RepoManager.getINSTANCE().get(url);
            setRepoData(repoData, "pref_" + (repoData == null ? RepoManager.internalIdOfUrl(url) : repoData.getPreferenceId()));
        }

        private void setRepoData(final RepoData repoData, String preferenceName) {
            if (repoData == null || repoData.isForceHide()) {
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
                preference.setTitle(repoData.isEnabled() ? R.string.repo_enabled : R.string.repo_disabled);
                preference.setOnPreferenceChangeListener((p, newValue) -> {
                    p.setTitle(((Boolean) newValue) ? R.string.repo_enabled : R.string.repo_disabled);
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
                            IntentHelper.openUrlAndroidacy(getFoxActivity(this), homepage, true);
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
                            IntentHelper.openUrlAndroidacy(getFoxActivity(this), submissionUrl, true);
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
