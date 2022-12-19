package com.fox2code.mmm.settings;

import static com.fox2code.mmm.settings.SettingsActivity.RepoFragment.applyMaterial3;
import static java.lang.Integer.parseInt;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
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
import com.fox2code.mmm.utils.ExternalHelper;
import com.fox2code.mmm.utils.Http;
import com.fox2code.mmm.utils.IntentHelper;
import com.fox2code.mmm.utils.ProcessHelper;
import com.fox2code.mmm.utils.SentryMain;
import com.fox2code.rosettax.LanguageActivity;
import com.fox2code.rosettax.LanguageSwitcher;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.internal.TextWatcherAdapter;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.common.hash.Hashing;
import com.mikepenz.aboutlibraries.LibsBuilder;
import com.topjohnwu.superuser.internal.UiThreadHandler;

import org.json.JSONException;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

public class SettingsActivity extends FoxActivity implements LanguageActivity {
    private static final int LANGUAGE_SUPPORT_LEVEL = 1;
    private static final String TAG = "SettingsActivity";
    private static boolean devModeStepFirstBootIgnore = MainApplication.isDeveloper();
    private static int devModeStep = 0;

    // Shamelessly adapted from https://github.com/DrKLO/Telegram/blob/2c71f6c92b45386f0c2b25f1442596462404bb39/TMessagesProj/src/main/java/org/telegram/messenger/SharedConfig.java#L1254
    public final static int PERFORMANCE_CLASS_LOW = 0;
    public final static int PERFORMANCE_CLASS_AVERAGE = 1;
    public final static int PERFORMANCE_CLASS_HIGH = 2;

    @PerformanceClass
    public static int getDevicePerformanceClass() {
        int devicePerformanceClass;
        int androidVersion = Build.VERSION.SDK_INT;
        int cpuCount = Runtime.getRuntime().availableProcessors();
        int memoryClass =
                ((ActivityManager) MainApplication.getINSTANCE().getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
        int totalCpuFreq = 0;
        int freqResolved = 0;
        for (int i = 0; i < cpuCount; i++) {
            try {
                RandomAccessFile reader = new RandomAccessFile(String.format(Locale.ENGLISH, "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", i), "r");
                String line = reader.readLine();
                if (line != null) {
                    totalCpuFreq += parseInt(line) / 1000;
                    freqResolved++;
                }
                reader.close();
            } catch (Throwable ignore) {}
        }
        int maxCpuFreq = freqResolved == 0 ? -1 : (int) Math.ceil(totalCpuFreq / (float) freqResolved);

        if (androidVersion < 21 || cpuCount <= 2 || memoryClass <= 100 || cpuCount <= 4 && maxCpuFreq != -1 && maxCpuFreq <= 1250 || cpuCount <= 4 && maxCpuFreq <= 1600 && memoryClass <= 128 && androidVersion <= 21 || cpuCount <= 4 && maxCpuFreq <= 1300 && memoryClass <= 128 && androidVersion <= 24) {
            devicePerformanceClass = PERFORMANCE_CLASS_LOW;
        } else if (cpuCount < 8 || memoryClass <= 160 || maxCpuFreq != -1 && maxCpuFreq <= 2050 || maxCpuFreq == -1 && cpuCount == 8 && androidVersion <= 23) {
            devicePerformanceClass = PERFORMANCE_CLASS_AVERAGE;
        } else {
            devicePerformanceClass = PERFORMANCE_CLASS_HIGH;
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "getDevicePerformanceClass: androidVersion=" + androidVersion + " cpuCount=" + cpuCount + " memoryClass=" + memoryClass + " maxCpuFreq=" + maxCpuFreq + " devicePerformanceClass=" + devicePerformanceClass);
        }

        return devicePerformanceClass;
    }

    public @interface PerformanceClass {}

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
        ProcessHelper.restartApplicationProcess(this);
    }

    @Override
    protected void onPause() {
        BackgroundUpdateChecker.onMainActivityResume(this);
        super.onPause();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements FoxActivity.OnBackPressedCallback {
        @RequiresApi(api = Build.VERSION_CODES.N)
        @SuppressLint("UnspecifiedImmutableFlag")
        @Override
        @SuppressWarnings("ConstantConditions")
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            getPreferenceManager().setSharedPreferencesName("mmm");
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            applyMaterial3(getPreferenceScreen());
            findPreference("pref_manage_repos").setOnPreferenceClickListener(p -> {
                devModeStep = 0;
                openFragment(new RepoFragment(), R.string.manage_repos_pref);
                return true;
            });
            ListPreference themePreference = findPreference("pref_theme");
            // If transparent theme(s) are set, disable monet
            if (themePreference.getValue().equals("transparent_light")) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Transparent theme is set, disabling monet");
                }
                findPreference("pref_enable_monet").setEnabled(false);
                // Toggle monet off
                ((TwoStatePreference) findPreference("pref_enable_monet")).setChecked(false);
                SharedPreferences.Editor editor =
                        getPreferenceManager().getSharedPreferences().edit();
                editor.putBoolean("pref_enable_monet", false).apply();
                // Set summary
                findPreference("pref_enable_monet").setSummary(R.string.monet_disabled_summary);
                // Same for blur
                findPreference("pref_enable_blur").setEnabled(false);
                ((TwoStatePreference) findPreference("pref_enable_blur")).setChecked(false);
                editor.putBoolean("pref_enable_blur", false).apply();
                findPreference("pref_enable_blur").setSummary(R.string.blur_disabled_summary);
            }
            themePreference.setSummaryProvider(p -> themePreference.getEntry());
            themePreference.setOnPreferenceClickListener(p -> {
                // You need to reboot your device at least once to be able to access dev-mode
                if (devModeStepFirstBootIgnore || !MainApplication.isFirstBoot()) devModeStep = 1;
                return false;
            });
            themePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Theme changed, refreshing activity. New value: " + newValue);
                }
                // Immediately save
                SharedPreferences.Editor editor =
                        getPreferenceManager().getSharedPreferences().edit();
                editor.putString("pref_theme", (String) newValue).apply();
                // If theme contains "transparent" then disable monet
                if (newValue.toString().contains("transparent")) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Transparent theme is being set, disabling monet");
                    }
                    // Show a dialogue warning the user about issues with transparent themes and
                    // that blur/monet will be disabled
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.transparent_theme_dialogue_title)
                            .setMessage(R.string.transparent_theme_dialogue_message)
                            .setPositiveButton(R.string.ok, (dialog, which) -> {
                                // Toggle monet off
                                ((TwoStatePreference) findPreference("pref_enable_monet")).setChecked(false);
                                editor.putBoolean("pref_enable_monet", false).apply();
                                // Set summary
                                findPreference("pref_enable_monet").setSummary(R.string.monet_disabled_summary);
                                // Same for blur
                                ((TwoStatePreference) findPreference("pref_enable_blur")).setChecked(false);
                                editor.putBoolean("pref_enable_blur", false).apply();
                                findPreference("pref_enable_blur").setSummary(R.string.blur_disabled_summary);
                                // Refresh activity
                                devModeStep = 0;
                                UiThreadHandler.handler.postDelayed(() -> {
                                    MainApplication.getINSTANCE().updateTheme();
                                    FoxActivity.getFoxActivity(this).setThemeRecreate(
                                            MainApplication.getINSTANCE().getManagerThemeResId());
                                }, 1);
                            })
                            .setNegativeButton(R.string.cancel, (dialog, which) -> {
                                // Revert to system theme
                                ((ListPreference) findPreference("pref_theme")).setValue("system");
                                // Refresh activity
                                devModeStep = 0;
                                UiThreadHandler.handler.postDelayed(() -> {
                                    MainApplication.getINSTANCE().updateTheme();
                                    FoxActivity.getFoxActivity(this).setThemeRecreate(
                                            MainApplication.getINSTANCE().getManagerThemeResId());
                                }, 1);
                            })
                            .show();
                } else {
                    findPreference("pref_enable_monet").setEnabled(true);
                    findPreference("pref_enable_monet").setSummary(null);
                    findPreference("pref_enable_blur").setEnabled(true);
                    findPreference("pref_enable_blur").setSummary(null);
                    devModeStep = 0;
                    UiThreadHandler.handler.postDelayed(() -> {
                        MainApplication.getINSTANCE().updateTheme();
                        FoxActivity.getFoxActivity(this).setThemeRecreate(
                                MainApplication.getINSTANCE().getManagerThemeResId());
                    }, 1);
                }
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
                    mPendingIntent = PendingIntent.getActivity(requireContext(), mPendingIntentId,
                            mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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
            // Disable blur on low performance devices
            if (getDevicePerformanceClass() < PERFORMANCE_CLASS_AVERAGE) {
                // Show a warning
                enableBlur.setOnPreferenceChangeListener((preference, newValue) -> {
                    if (newValue.equals(true)) {
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.low_performance_device_dialogue_title)
                                .setMessage(R.string.low_performance_device_dialogue_message)
                                .setPositiveButton(R.string.ok, (dialog, which) -> {
                                    // Toggle blur on
                                    ((TwoStatePreference) findPreference("pref_enable_blur")).setChecked(true);
                                    SharedPreferences.Editor editor =
                                            getPreferenceManager().getSharedPreferences().edit();
                                    editor.putBoolean("pref_enable_blur", true).apply();
                                    // Set summary
                                    findPreference("pref_enable_blur").setSummary(R.string.blur_disabled_summary);
                                })
                                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                                    // Revert to blur on
                                    ((TwoStatePreference) findPreference("pref_enable_blur")).setChecked(false);
                                    SharedPreferences.Editor editor =
                                            getPreferenceManager().getSharedPreferences().edit();
                                    editor.putBoolean("pref_enable_blur", false).apply();
                                    // Set summary
                                    findPreference("pref_enable_blur").setSummary(null);
                                })
                                .show();
                    }
                    return true;
                });
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
                Log.d(TAG, String.format("Sentry installed: %s, debug: %s, magisk path: %s",
                        SentryMain.IS_SENTRY_INSTALLED, BuildConfig.DEBUG, InstallerInitializer.peekMagiskPath()));
                Objects.requireNonNull((Preference) findPreference("pref_test_crash")).setVisible(false);
                // Find pref_clear_data and set it invisible
                Objects.requireNonNull((Preference) findPreference("pref_clear_data")).setVisible(false);
            } else {
                if (findPreference("pref_test_crash") != null && findPreference("pref_clear_data") != null) {
                    findPreference("pref_test_crash").setOnPreferenceClickListener(preference -> {
                        // Hard crash the app
                        throw new Error("This is a test crash");
                    });
                    findPreference("pref_clear_data").setOnPreferenceClickListener(preference -> {
                        // Clear app data
                        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.clear_data_dialogue_title).setMessage(R.string.clear_data_dialogue_message).setPositiveButton(R.string.yes, (dialog, which) -> {
                            // Clear app data
                            MainApplication.getINSTANCE().clearAppData();
                            // Restart app
                            ProcessHelper.restartApplicationProcess(requireContext());
                        }).setNegativeButton(R.string.no, (dialog, which) -> {
                        }).show();
                        return true;
                    });
                } else {
                    Log.e(TAG, String.format("Something is null: %s, %s",
                            findPreference("pref_test_crash"), findPreference("pref_clear_data")));
                }
            }
            if (InstallerInitializer.peekMagiskVersion() < Constants.MAGISK_VER_CODE_INSTALL_COMMAND || !MainApplication.isDeveloper()) {
                findPreference("pref_use_magisk_install_command").setVisible(false);
            }
            Preference debugNotification = findPreference("pref_background_update_check_debug");
            debugNotification.setEnabled(MainApplication.isBackgroundUpdateCheckEnabled());
            debugNotification.setVisible(MainApplication.isDeveloper() && !MainApplication.isWrapped());
            debugNotification.setVisible(MainApplication.isDeveloper() && !MainApplication.isWrapped());
            debugNotification.setOnPreferenceClickListener(preference -> {
                BackgroundUpdateChecker.postNotification(this.requireContext(), new Random().nextInt(4) + 2);
                return true;
            });
            Preference backgroundUpdateCheck = findPreference("pref_background_update_check");
            backgroundUpdateCheck.setVisible(!MainApplication.isWrapped());
            // Make uncheckable if POST_NOTIFICATIONS permission is not granted
            if (!MainApplication.isNotificationPermissionGranted()) {
                // Instead of disabling the preference, we make it uncheckable and when the user
                // clicks on it, we show a dialog explaining why the permission is needed
                backgroundUpdateCheck.setOnPreferenceClickListener(preference -> {
                    // set the box to unchecked
                    ((SwitchPreferenceCompat) backgroundUpdateCheck).setChecked(false);
                    // ensure that the preference is false
                    MainApplication.getSharedPreferences().edit().putBoolean("pref_background_update_check", false).apply();
                    new MaterialAlertDialogBuilder(this.requireContext())
                            .setTitle(R.string.permission_notification_title)
                            .setMessage(R.string.permission_notification_message)
                            .setPositiveButton(R.string.ok, (dialog, which) -> {
                                // Open the app settings
                                Intent intent = new Intent();
                                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package", this.requireContext().getPackageName(), null);
                                intent.setData(uri);
                                this.startActivity(intent);
                            })
                            .setNegativeButton(R.string.cancel, (dialog, which) -> {})
                            .show();
                    return true;
                });
                backgroundUpdateCheck.setSummary(R.string.background_update_check_permission_required);
            }
            backgroundUpdateCheck.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = Boolean.parseBoolean(String.valueOf(newValue));
                debugNotification.setEnabled(enabled);
                if (!enabled) {
                    BackgroundUpdateChecker.onMainActivityResume(this.requireContext());
                }
                return true;
            });

            final LibsBuilder libsBuilder = new LibsBuilder().withShowLoadingProgress(false).withLicenseShown(true).withAboutMinimalDesign(false);
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            LongClickablePreference linkClickable = findPreference("pref_update");
            linkClickable.setVisible(BuildConfig.ENABLE_AUTO_UPDATER &&
                    (BuildConfig.DEBUG || AppUpdateManager.getAppUpdateManager().peekHasUpdate()));
            linkClickable.setOnPreferenceClickListener(p -> {
                devModeStep = 0;
                IntentHelper.openUrl(p.getContext(), "https://github.com/Fox2Code/FoxMagiskModuleManager/releases");
                return true;
            });
            linkClickable.setOnPreferenceLongClickListener(p -> {
                String toastText = requireContext().getString(R.string.link_copied);
                clipboard.setPrimaryClip(ClipData.newPlainText(toastText,
                        "https://github.com/Fox2Code/FoxMagiskModuleManager/releases"));
                Toast.makeText(requireContext(), toastText, Toast.LENGTH_SHORT).show();
                return true;
            });
            if (BuildConfig.DEBUG || BuildConfig.ENABLE_AUTO_UPDATER) {
                linkClickable = findPreference("pref_report_bug");
                linkClickable.setOnPreferenceClickListener(p -> {
                    devModeStep = 0;
                    devModeStepFirstBootIgnore = true;
                    IntentHelper.openUrl(p.getContext(), "https://github.com/Fox2Code/FoxMagiskModuleManager/issues");
                    return true;
                });
                linkClickable.setOnPreferenceLongClickListener(p -> {
                    String toastText = requireContext().getString(R.string.link_copied);
                    clipboard.setPrimaryClip(ClipData.newPlainText(toastText,
                            "https://github.com/Fox2Code/FoxMagiskModuleManager/issues"));
                    Toast.makeText(requireContext(), toastText, Toast.LENGTH_SHORT).show();
                    return true;
                });
            } else {
                findPreference("pref_report_bug").setVisible(false);
            }
            linkClickable = findPreference("pref_source_code");
            linkClickable.setOnPreferenceClickListener(p -> {
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
            linkClickable.setOnPreferenceLongClickListener(p -> {
                String toastText = requireContext().getString(R.string.link_copied);
                clipboard.setPrimaryClip(ClipData.newPlainText(toastText,
                        "https://github.com/Fox2Code/FoxMagiskModuleManager"));
                Toast.makeText(requireContext(), toastText, Toast.LENGTH_SHORT).show();
                return true;
            });
            linkClickable = findPreference("pref_support");
            linkClickable.setOnPreferenceClickListener(p -> {
                devModeStep = 0;
                IntentHelper.openUrl(p.getContext(), "https://t.me/Fox2Code_Chat");
                return true;
            });
            linkClickable.setOnPreferenceLongClickListener(p -> {
                String toastText = requireContext().getString(R.string.link_copied);
                clipboard.setPrimaryClip(ClipData.newPlainText(toastText,
                        "https://t.me/Fox2Code_Chat"));
                Toast.makeText(requireContext(), toastText, Toast.LENGTH_SHORT).show();
                return true;
            });
            findPreference("pref_show_licenses").setOnPreferenceClickListener(p -> {
                devModeStep = devModeStep == 1 ? 2 : 0;
                BackgroundUpdateChecker.onMainActivityResume(this.requireContext());
                openFragment(libsBuilder.supportFragment(), R.string.licenses);
                return true;
            });
            // Determine if this is an official build based on the signature
            boolean isOfficial = false;
            try {
                // Get the signature of the key used to sign the app
                @SuppressLint("PackageManagerGetSignatures") Signature[] signatures = requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), PackageManager.GET_SIGNATURES).signatures;
                String officialSignatureHash =
                "7bec7c4462f4aac616612d9f56a023ee3046e83afa956463b5fab547fd0a0be6";
                String ourSignatureHash = Hashing.sha256().hashBytes(signatures[0].toByteArray()).toString();
                isOfficial = ourSignatureHash.equals(officialSignatureHash);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            String flavor = BuildConfig.FLAVOR;
            String type = BuildConfig.BUILD_TYPE;
            // Set the summary of pref_pkg_info to something like Github-debug v1.0 (123) (Official)
            String pkgInfo = getString(R.string.pref_pkg_info_summary, flavor + "-" + type,
                    BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, isOfficial ?
                            getString(R.string.official) : getString(R.string.unofficial));
            findPreference("pref_pkg_info").setSummary(pkgInfo);
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

        // *says proudly* I stole it
        // namely, from https://github.com/NeoApplications/Neo-Wellbeing/blob/9fca4136263780c022f9ec6433c0b43d159166db/app/src/main/java/org/eu/droid_ng/wellbeing/prefs/SettingsActivity.java#L101
        public static void applyMaterial3(Preference p) {
            if (p instanceof PreferenceGroup) {
                PreferenceGroup pg = (PreferenceGroup) p;
                for (int i = 0; i < pg.getPreferenceCount(); i++) {
                    applyMaterial3(pg.getPreference(i));
                }
            }
            if (p instanceof SwitchPreferenceCompat) {
                p.setWidgetLayoutResource(R.layout.preference_material_switch);
            }
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
                                .setCancelable(false)
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
                                    mPendingIntent = PendingIntent.getActivity(requireContext(), mPendingIntentId,
                                            mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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
                                .setCancelable(false)
                                .setMessage(R.string.androidacy_test_mode_disable_warning)
                                .setNeutralButton(android.R.string.ok, (dialog, which) -> {
                                    // User clicked OK button
                                    Intent mStartActivity = new Intent(requireContext(), MainActivity.class);
                                    mStartActivity.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                    int mPendingIntentId = 123456;
                                    // If < 23, FLAG_IMMUTABLE is not available
                                    PendingIntent mPendingIntent;
                                    mPendingIntent = PendingIntent.getActivity(requireContext(), mPendingIntentId,
                                            mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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
            // Disable toggling the pref_androidacy_repo_enabled on builds without an
            // ANDROIDACY_CLIENT_ID or where the ANDROIDACY_CLIENT_ID is empty
            Preference androidacyRepoEnabled = Objects.requireNonNull(findPreference("pref_androidacy_repo_enabled"));
            if (Objects.equals(BuildConfig.ANDROIDACY_CLIENT_ID, "")) {
                androidacyRepoEnabled.setOnPreferenceClickListener(preference -> {
                    new MaterialAlertDialogBuilder(this.requireContext())
                            .setTitle(R.string.androidacy_repo_disabled)
                            .setCancelable(false)
                            .setMessage(R.string.androidacy_repo_disabled_message)
                            .setPositiveButton(R.string.download_full_app, (dialog, which) -> {
                                // User clicked OK button. Open GitHub releases page
                                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                                        "https://github.com/Fox2Code/FoxMagiskModuleManager/releases"));
                                startActivity(browserIntent);
                            })
                            .show();
                    // Revert the switch to off
                    SwitchPreferenceCompat switchPreferenceCompat = (SwitchPreferenceCompat) androidacyRepoEnabled;
                    switchPreferenceCompat.setChecked(false);
                    // Save the preference
                    MainApplication.getSharedPreferences().edit().putBoolean("pref_androidacy_repo_enabled", false).apply();
                    return false;
                });
            }
            String[] originalApiKeyRef = new String[]{MainApplication.getINSTANCE().getSharedPreferences("androidacy", 0).getString("pref_androidacy_api_token", null)};
            // Get the dummy pref_androidacy_repo_api_token EditTextPreference
            EditTextPreference prefAndroidacyRepoApiKey = Objects.requireNonNull(findPreference("pref_androidacy_api_token"));
            prefAndroidacyRepoApiKey.setTitle(R.string.api_key);
            prefAndroidacyRepoApiKey.setSummary(R.string.api_key_summary);
            prefAndroidacyRepoApiKey.setDialogTitle(R.string.api_key);
            prefAndroidacyRepoApiKey.setDefaultValue(originalApiKeyRef[0]);
            // Set the value to the current value
            prefAndroidacyRepoApiKey.setText(originalApiKeyRef[0]);
            prefAndroidacyRepoApiKey.setOnBindEditTextListener(editText -> {
                editText.setSingleLine();
                // Make the single line wrap
                editText.setHorizontallyScrolling(false);
                // Set the height to the maximum required to fit the text
                editText.setMaxLines(Integer.MAX_VALUE);
                // Make ok button say "Save"
                editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
            });
            prefAndroidacyRepoApiKey.setPositiveButtonText(R.string.save_api_key);
            prefAndroidacyRepoApiKey.setOnPreferenceChangeListener((preference, newValue) -> {
                if (originalApiKeyRef[0].equals(newValue)) return true;
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
                        MainApplication.getINSTANCE().getSharedPreferences("androidacy", 0).edit().remove("pref_androidacy_api_token").apply();
                        new Handler(Looper.getMainLooper()).post(() -> {
                            Snackbar.make(requireView(), R.string.api_key_removed, Snackbar.LENGTH_SHORT).show();
                            // Show dialog to restart app with ok button
                            new MaterialAlertDialogBuilder(this.requireContext())
                                    .setTitle(R.string.restart)
                                    .setCancelable(false)
                                    .setMessage(R.string.api_key_restart)
                                    .setNeutralButton(android.R.string.ok, (dialog, which) -> {
                                        // User clicked OK button
                                        Intent mStartActivity = new Intent(requireContext(), MainActivity.class);
                                        mStartActivity.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                        int mPendingIntentId = 123456;
                                        // If < 23, FLAG_IMMUTABLE is not available
                                        PendingIntent mPendingIntent;
                                        mPendingIntent = PendingIntent.getActivity(requireContext(), mPendingIntentId,
                                                mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                                        AlarmManager mgr = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
                                        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
                                        if (BuildConfig.DEBUG) {
                                            Log.d(TAG, "Restarting app to save token preference: " + newValue);
                                        }
                                        System.exit(0); // Exit app process
                                    })
                                    .show();
                        });
                    } else {
                        // If key < 64 chars, it's not valid
                        if (apiKey.length() < 64) {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                Snackbar.make(requireView(), R.string.api_key_invalid, Snackbar.LENGTH_SHORT).show();
                                // Save the original key
                                MainApplication.getINSTANCE().getSharedPreferences("androidacy", 0).edit().putString("pref_androidacy_api_token", originalApiKeyRef[0]).apply();
                                // Re-show the dialog with an error
                                prefAndroidacyRepoApiKey.performClick();
                                // Show error
                                prefAndroidacyRepoApiKey.setDialogMessage(getString(R.string.api_key_invalid));
                            });
                        } else {
                            // If the key is the same as the original, just show a snack bar
                            if (apiKey.equals(originalApiKeyRef[0])) {
                                new Handler(Looper.getMainLooper()).post(() -> Snackbar.make(requireView(), R.string.api_key_unchanged, Snackbar.LENGTH_SHORT).show());
                                return;
                            }
                            boolean valid = false;
                            try {
                                valid = AndroidacyRepoData.getInstance().isValidToken(apiKey);
                            } catch (IOException | NoSuchAlgorithmException ignored) {}
                            // If the key is valid, save it
                            if (valid) {
                                originalApiKeyRef[0] = apiKey;
                                RepoManager.getINSTANCE().getAndroidacyRepoData().setToken(apiKey);
                                MainApplication.getINSTANCE().getSharedPreferences("androidacy", 0).edit().putString("pref_androidacy_api_token", apiKey).apply();
                                // Snackbar with success and restart button
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    Snackbar.make(requireView(), R.string.api_key_valid, Snackbar.LENGTH_SHORT).show();
                                    // Show dialog to restart app with ok button
                                    new MaterialAlertDialogBuilder(this.requireContext())
                                            .setTitle(R.string.restart)
                                            .setCancelable(false)
                                            .setMessage(R.string.api_key_restart)
                                            .setNeutralButton(android.R.string.ok, (dialog, which) -> {
                                                // User clicked OK button
                                                Intent mStartActivity = new Intent(requireContext(), MainActivity.class);
                                                mStartActivity.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                                int mPendingIntentId = 123456;
                                                // If < 23, FLAG_IMMUTABLE is not available
                                                PendingIntent mPendingIntent;
                                                mPendingIntent = PendingIntent.getActivity(requireContext(), mPendingIntentId,
                                                        mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                                                AlarmManager mgr = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
                                                mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
                                                if (BuildConfig.DEBUG) {
                                                    Log.d(TAG, "Restarting app to save token preference: " + newValue);
                                                }
                                                System.exit(0); // Exit app process
                                            })
                                            .show();
                                });
                            } else {
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    Snackbar.make(requireView(), R.string.api_key_invalid, Snackbar.LENGTH_SHORT).show();
                                    // Save the original key
                                    MainApplication.getINSTANCE().getSharedPreferences("androidacy", 0).edit().putString(
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
            // make sure the preference is visible if repo is enabled
            prefAndroidacyRepoApiKey.setVisible(RepoManager.getINSTANCE().getAndroidacyRepoData().isEnabled());
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
                        sharedPreferences.edit().remove("pref_custom_repo_" + index + "_enabled").apply();
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
                            new Thread("Add Custom Repo Thread") {
                                @Override
                                public void run() {
                                    try {
                                        customRepoData.quickPrePopulate();
                                    } catch (IOException | JSONException | NoSuchAlgorithmException e) {
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
            ClipboardManager clipboard = (ClipboardManager)
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
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
                // Handle custom repo separately
                if (repoData instanceof CustomRepoData) {
                    preference.setTitle(R.string.custom_repo_always_on);
                    // Disable the preference
                    preference.setEnabled(false);
                    return;
                } else {
                    ((TwoStatePreference) preference).setChecked(repoData.isEnabled());
                    preference.setTitle(repoData.isEnabled() ? R.string.repo_enabled : R.string.repo_disabled);
                    preference.setOnPreferenceChangeListener((p, newValue) -> {
                        p.setTitle(((Boolean) newValue) ? R.string.repo_enabled : R.string.repo_disabled);
                        return true;
                    });
                }
            }
            preference = findPreference(preferenceName + "_website");
            String homepage = repoData.getWebsite();
            if (preference != null) {
                if (!homepage.isEmpty()) {
                    preference.setVisible(true);
                    preference.setOnPreferenceClickListener(p -> {
                        IntentHelper.openUrl(getFoxActivity(this), homepage);
                        return true;
                    });
                    ((LongClickablePreference) preference).setOnPreferenceLongClickListener(p -> {
                        String toastText = requireContext().getString(R.string.link_copied);
                        clipboard.setPrimaryClip(ClipData.newPlainText(toastText, homepage));
                        Toast.makeText(requireContext(), toastText, Toast.LENGTH_SHORT).show();
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
                    ((LongClickablePreference) preference).setOnPreferenceLongClickListener(p -> {
                        String toastText = requireContext().getString(R.string.link_copied);
                        clipboard.setPrimaryClip(ClipData.newPlainText(toastText, supportUrl));
                        Toast.makeText(requireContext(), toastText, Toast.LENGTH_SHORT).show();
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
                    ((LongClickablePreference) preference).setOnPreferenceLongClickListener(p -> {
                        String toastText = requireContext().getString(R.string.link_copied);
                        clipboard.setPrimaryClip(ClipData.newPlainText(toastText, donateUrl));
                        Toast.makeText(requireContext(), toastText, Toast.LENGTH_SHORT).show();
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
                        IntentHelper.openUrl(getFoxActivity(this), submissionUrl);
                        return true;
                    });
                    ((LongClickablePreference) preference).setOnPreferenceLongClickListener(p -> {
                        String toastText = requireContext().getString(R.string.link_copied);
                        clipboard.setPrimaryClip(ClipData.newPlainText(toastText, submissionUrl));
                        Toast.makeText(requireContext(), toastText, Toast.LENGTH_SHORT).show();
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

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            getPreferenceManager().setSharedPreferencesName("mmm");
            setPreferencesFromResource(R.xml.repo_preferences, rootKey);
            applyMaterial3(getPreferenceScreen());
            setRepoData(RepoManager.MAGISK_ALT_REPO);
            setRepoData(RepoManager.ANDROIDACY_MAGISK_REPO_ENDPOINT);
            updateCustomRepoList(true);
            onCreatePreferencesAndroidacy();
        }
    }
}
