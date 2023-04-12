package com.fox2code.mmm.settings;

import static com.fox2code.mmm.settings.SettingsActivity.RepoFragment.applyMaterial3;
import static java.lang.Integer.parseInt;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Html;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.TwoStatePreference;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.fox2code.foxcompat.app.FoxActivity;
import com.fox2code.foxcompat.view.FoxDisplay;
import com.fox2code.foxcompat.view.FoxViewCompat;
import com.fox2code.mmm.AppUpdateManager;
import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.Constants;
import com.fox2code.mmm.MainActivity;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.UpdateActivity;
import com.fox2code.mmm.androidacy.AndroidacyRepoData;
import com.fox2code.mmm.background.BackgroundUpdateChecker;
import com.fox2code.mmm.installer.InstallerInitializer;
import com.fox2code.mmm.manager.LocalModuleInfo;
import com.fox2code.mmm.manager.ModuleManager;
import com.fox2code.mmm.module.ActionButtonType;
import com.fox2code.mmm.repo.CustomRepoData;
import com.fox2code.mmm.repo.CustomRepoManager;
import com.fox2code.mmm.repo.RepoData;
import com.fox2code.mmm.repo.RepoManager;
import com.fox2code.mmm.utils.ExternalHelper;
import com.fox2code.mmm.utils.IntentHelper;
import com.fox2code.mmm.utils.ProcessHelper;
import com.fox2code.mmm.utils.io.net.Http;
import com.fox2code.mmm.utils.realm.ReposList;
import com.fox2code.mmm.utils.sentry.SentryMain;
import com.fox2code.rosettax.LanguageActivity;
import com.fox2code.rosettax.LanguageSwitcher;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.mikepenz.aboutlibraries.LibsBuilder;
import com.topjohnwu.superuser.internal.UiThreadHandler;

import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmResults;
import timber.log.Timber;

public class SettingsActivity extends FoxActivity implements LanguageActivity {
    // Shamelessly adapted from https://github.com/DrKLO/Telegram/blob/2c71f6c92b45386f0c2b25f1442596462404bb39/TMessagesProj/src/main/java/org/telegram/messenger/SharedConfig.java#L1254
    public final static int PERFORMANCE_CLASS_LOW = 0;
    public final static int PERFORMANCE_CLASS_AVERAGE = 1;
    public final static int PERFORMANCE_CLASS_HIGH = 2;
    private static final int LANGUAGE_SUPPORT_LEVEL = 1;
    private static boolean devModeStepFirstBootIgnore = MainApplication.isDeveloper();
    private static int devModeStep = 0;
    @SuppressLint("RestrictedApi")
    private final NavigationBarView.OnItemSelectedListener onItemSelectedListener = item -> {
        int itemId = item.getItemId();
        if (itemId == R.id.back) {
            startActivity(new Intent(this, MainActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
            return true;
        } else //noinspection RedundantIfStatement
            if (itemId == R.id.settings_menu_item) {
                return true;
            }
        return false;
    };

    @PerformanceClass
    public static int getDevicePerformanceClass() {
        int devicePerformanceClass;
        int androidVersion = Build.VERSION.SDK_INT;
        int cpuCount = Runtime.getRuntime().availableProcessors();
        int memoryClass = ((ActivityManager) MainApplication.getINSTANCE().getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
        int totalCpuFreq = 0;
        int freqResolved = 0;
        for (int i = 0; i < cpuCount; i++) {
            try (RandomAccessFile reader = new RandomAccessFile(String.format(Locale.ENGLISH, "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", i), "r")) {
                String line = reader.readLine();
                if (line != null) {
                    totalCpuFreq += parseInt(line) / 1000;
                    freqResolved++;
                }
            } catch (Exception ignore) {
            }
        }
        int maxCpuFreq = freqResolved == 0 ? -1 : (int) Math.ceil(totalCpuFreq / (float) freqResolved);

        if (androidVersion < 21 || cpuCount <= 2 || memoryClass <= 100 || cpuCount <= 4 && maxCpuFreq != -1 && maxCpuFreq <= 1250 || cpuCount <= 4 && maxCpuFreq <= 1600 && memoryClass <= 128 && androidVersion == 21 || cpuCount <= 4 && maxCpuFreq <= 1300 && memoryClass <= 128 && androidVersion <= 24) {
            devicePerformanceClass = PERFORMANCE_CLASS_LOW;
        } else if (cpuCount < 8 || memoryClass <= 160 || maxCpuFreq != -1 && maxCpuFreq <= 2050 || maxCpuFreq == -1 && cpuCount == 8 && androidVersion <= 23) {
            devicePerformanceClass = PERFORMANCE_CLASS_AVERAGE;
        } else {
            devicePerformanceClass = PERFORMANCE_CLASS_HIGH;
        }

        Timber.d("getDevicePerformanceClass: androidVersion=" + androidVersion + " cpuCount=" + cpuCount + " memoryClass=" + memoryClass + " maxCpuFreq=" + maxCpuFreq + " devicePerformanceClass=" + devicePerformanceClass);

        return devicePerformanceClass;
    }

    @SuppressLint("RestrictedApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        devModeStep = 0;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        setTitle(R.string.app_name);
        //hideActionBar();
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(onItemSelectedListener);
        if (savedInstanceState == null) {
            SettingsFragment settingsFragment = new SettingsFragment();
            getSupportFragmentManager().beginTransaction().replace(R.id.settings, settingsFragment).commit();
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

    public @interface PerformanceClass {
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements FoxActivity.OnBackPressedCallback {

        @SuppressLint("UnspecifiedImmutableFlag")
        @Override
        @SuppressWarnings("ConstantConditions")
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            String name = "mmm";
            Context context = MainApplication.getINSTANCE();
            MasterKey masterKey;
            PreferenceManager preferenceManager = getPreferenceManager();
            SharedPreferenceDataStore dataStore;
            SharedPreferences.Editor editor;
            try {
                masterKey = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
                dataStore = new SharedPreferenceDataStore(EncryptedSharedPreferences.create(context, name, masterKey, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM));
                preferenceManager.setPreferenceDataStore(dataStore);
                preferenceManager.setSharedPreferencesName("mmm");
                editor = dataStore.getSharedPreferences().edit();
            } catch (Exception e) {
                Timber.e(e, "Failed to create encrypted shared preferences");
                throw new RuntimeException(getString(R.string.error_encrypted_shared_preferences));
            }
            assert preferenceManager != null;
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            applyMaterial3(getPreferenceScreen());
            // add bottom navigation bar to the settings
            BottomNavigationView bottomNavigationView = requireActivity().findViewById(R.id.bottom_navigation);
            if (bottomNavigationView != null) {
                bottomNavigationView.setVisibility(View.VISIBLE);
                bottomNavigationView.getMenu().findItem(R.id.settings_menu_item).setChecked(true);
            }
            findPreference("pref_manage_repos").setOnPreferenceClickListener(p -> {
                devModeStep = 0;
                openFragment(new RepoFragment(), R.string.manage_repos_pref);
                return true;
            });
            ListPreference themePreference = findPreference("pref_theme");
            // If transparent theme(s) are set, disable monet
            if (themePreference.getValue().equals("transparent_light")) {
                Timber.d("disabling monet");
                findPreference("pref_enable_monet").setEnabled(false);
                // Toggle monet off
                ((TwoStatePreference) findPreference("pref_enable_monet")).setChecked(false);
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
            themePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                // You need to reboot your device at least once to be able to access dev-mode
                if (devModeStepFirstBootIgnore || !MainApplication.isFirstBoot()) devModeStep = 1;
                Timber.d("refreshing activity. New value: %s", newValue);
                editor.putString("pref_theme", (String) newValue).apply();
                // If theme contains "transparent" then disable monet
                if (newValue.toString().contains("transparent")) {
                    Timber.d("disabling monet");
                    // Show a dialogue warning the user about issues with transparent themes and
                    // that blur/monet will be disabled
                    new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.transparent_theme_dialogue_title).setMessage(R.string.transparent_theme_dialogue_message).setPositiveButton(R.string.ok, (dialog, which) -> {
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
                            FoxActivity.getFoxActivity(this).setThemeRecreate(MainApplication.getINSTANCE().getManagerThemeResId());
                        }, 1);
                        Intent intent = new Intent(requireContext(), SettingsActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                    }).setNegativeButton(R.string.cancel, (dialog, which) -> {
                        // Revert to system theme
                        ((ListPreference) findPreference("pref_theme")).setValue("system");
                        // Refresh activity
                        devModeStep = 0;
                    }).show();
                } else {
                    findPreference("pref_enable_monet").setEnabled(true);
                    findPreference("pref_enable_monet").setSummary(null);
                    findPreference("pref_enable_blur").setEnabled(true);
                    findPreference("pref_enable_blur").setSummary(null);
                    devModeStep = 0;
                }
                UiThreadHandler.handler.postDelayed(() -> {
                    MainApplication.getINSTANCE().updateTheme();
                    FoxActivity.getFoxActivity(this).setThemeRecreate(MainApplication.getINSTANCE().getManagerThemeResId());
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
                    mPendingIntent = PendingIntent.getActivity(requireContext(), mPendingIntentId, mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    AlarmManager mgr = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
                    mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
                    Timber.d("Restarting app to save crash reporting preference: %s", newValue);
                    System.exit(0); // Exit app process
                });
                // Do not reverse the change if the user cancels the dialog
                materialAlertDialogBuilder.setNegativeButton(R.string.no, (dialog, which) -> {
                });
                materialAlertDialogBuilder.show();
                return true;
            });
            Preference enableBlur = findPreference("pref_enable_blur");
            // Disable blur on low performance devices
            if (getDevicePerformanceClass() < PERFORMANCE_CLASS_AVERAGE) {
                // Show a warning
                enableBlur.setOnPreferenceChangeListener((preference, newValue) -> {
                    if (newValue.equals(true)) {
                        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.low_performance_device_dialogue_title).setMessage(R.string.low_performance_device_dialogue_message).setPositiveButton(R.string.ok, (dialog, which) -> {
                            // Toggle blur on
                            ((TwoStatePreference) findPreference("pref_enable_blur")).setChecked(true);
                            editor.putBoolean("pref_enable_blur", true).apply();
                            // Set summary
                            findPreference("pref_enable_blur").setSummary(R.string.blur_disabled_summary);
                        }).setNegativeButton(R.string.cancel, (dialog, which) -> {
                            // Revert to blur on
                            ((TwoStatePreference) findPreference("pref_enable_blur")).setChecked(false);
                            editor.putBoolean("pref_enable_blur", false).apply();
                            // Set summary
                            findPreference("pref_enable_blur").setSummary(null);
                        }).show();
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

            Preference languageSelector = findPreference("pref_language_selector");
            languageSelector.setOnPreferenceClickListener(preference -> {
                LanguageSwitcher ls = new LanguageSwitcher(getActivity());
                ls.setSupportedStringLocales(MainApplication.supportedLocales);
                ls.showChangeLanguageDialog(getActivity());
                return true;
            });

            // Handle pref_language_selector_cta by taking user to https://translate.nift4.org/engage/foxmmm/
            LongClickablePreference languageSelectorCta = findPreference("pref_language_selector_cta");
            languageSelectorCta.setOnPreferenceClickListener(preference -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://translate.nift4.org/engage/foxmmm/"));
                startActivity(browserIntent);
                return true;
            });

            // Long click to copy url
            languageSelectorCta.setOnPreferenceLongClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("URL", "https://translate.nift4.org/engage/foxmmm/");
                clipboard.setPrimaryClip(clip);
                Toast.makeText(requireContext(), R.string.link_copied, Toast.LENGTH_SHORT).show();
                return true;
            });

            int level = this.currentLanguageLevel();
            if (level != LANGUAGE_SUPPORT_LEVEL) {
                Timber.e("latest is %s", LANGUAGE_SUPPORT_LEVEL);
                languageSelector.setSummary(R.string.language_support_outdated);
            } else {
                String translatedBy = this.getString(R.string.language_translated_by);
                // I don't "translate" english
                if (!("Translated by Fox2Code (Put your name here)".equals(translatedBy) || "Translated by Fox2Code".equals(translatedBy))) {
                    languageSelector.setSummary(R.string.language_translated_by);
                } else {
                    languageSelector.setSummary(null);
                }
            }

            if (!MainApplication.isDeveloper()) {
                findPreference("pref_disable_low_quality_module_filter").setVisible(false);
                // Find pref_clear_data and set it invisible
                Objects.requireNonNull((Preference) findPreference("pref_clear_data")).setVisible(false);
            }
            // hande clear cache
            findPreference("pref_clear_cache").setOnPreferenceClickListener(preference -> {
                // Clear cache
                new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.clear_cache_dialogue_title).setMessage(R.string.clear_cache_dialogue_message).setPositiveButton(R.string.yes, (dialog, which) -> {
                    // Clear app cache
                    try {
                        // use apache commons IO to delete the cache
                        FileUtils.deleteDirectory(requireContext().getCacheDir());
                        // create a new cache dir
                        FileUtils.forceMkdir(requireContext().getCacheDir());
                        // create cache dirs for cronet and webview
                        FileUtils.forceMkdir(new File(requireContext().getCacheDir(), "cronet"));
                        FileUtils.forceMkdir(new File(MainApplication.getINSTANCE().getDataDir() + "/cache/WebView/Default/HTTP Cache/Code Cache/wasm"));
                        FileUtils.forceMkdir(new File(MainApplication.getINSTANCE().getDataDir() + "/cache/WebView/Default/HTTP Cache/Code Cache/js"));
                        Toast.makeText(requireContext(), R.string.cache_cleared, Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Timber.e(e);
                        Toast.makeText(requireContext(), R.string.cache_clear_failed, Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton(R.string.no, (dialog, which) -> {
                    // Do nothing
                }).show();
                return true;
            });
            if (!SentryMain.IS_SENTRY_INSTALLED || !BuildConfig.DEBUG || InstallerInitializer.peekMagiskPath() == null) {
                // Hide the pref_crash option if not in debug mode - stop users from purposely crashing the app
                Timber.i(InstallerInitializer.peekMagiskPath());
                Objects.requireNonNull((Preference) findPreference("pref_test_crash")).setVisible(false);
            } else {
                if (findPreference("pref_test_crash") != null && findPreference("pref_clear_data") != null) {
                    findPreference("pref_test_crash").setOnPreferenceClickListener(preference -> {
                        // Hard crash the app
                        // we need a stacktrace to see if the crash is from the app or from the system
                        throw new RuntimeException("This is a test crash with a stupidly long description to show off the crash handler. Are we having fun yet?");
                    });
                    findPreference("pref_clear_data").setOnPreferenceClickListener(preference -> {
                        // Clear app data
                        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.clear_data_dialogue_title).setMessage(R.string.clear_data_dialogue_message).setPositiveButton(R.string.yes, (dialog, which) -> {
                            // Clear app data
                            MainApplication.getINSTANCE().resetApp();
                        }).setNegativeButton(R.string.no, (dialog, which) -> {
                        }).show();
                        return true;
                    });
                } else {
                    Timber.e("Something is null: %s, %s", findPreference("pref_clear_data"), findPreference("pref_test_crash"));
                }
            }
            if (InstallerInitializer.peekMagiskVersion() < Constants.MAGISK_VER_CODE_INSTALL_COMMAND || !MainApplication.isDeveloper()) {
                findPreference("pref_use_magisk_install_command").setVisible(false);
            }
            Preference debugNotification = findPreference("pref_background_update_check_debug");
            Preference updateCheckExcludes = findPreference("pref_background_update_check_excludes");
            debugNotification.setEnabled(MainApplication.isBackgroundUpdateCheckEnabled());
            debugNotification.setVisible(MainApplication.isDeveloper() && !MainApplication.isWrapped() && MainApplication.isBackgroundUpdateCheckEnabled());
            debugNotification.setOnPreferenceClickListener(preference -> {
                // fake updatable modules hashmap
                HashMap<String, String> updateableModules = new HashMap<>();
                // count of modules to fake must match the count in the random number generator
                Random random = new Random();
                int count;
                do {
                    count = random.nextInt(4) + 2;
                } while (count == 2);
                for (int i = 0; i < count; i++) {
                    int fakeVersion;
                    do {
                        fakeVersion = random.nextInt(10);
                    } while (fakeVersion == 0);
                    Timber.d("Fake version: %s, count: %s", fakeVersion, i);
                    updateableModules.put("FakeModule " + i, "1.0." + fakeVersion);
                }
                BackgroundUpdateChecker.postNotification(this.requireContext(), updateableModules, count, true);
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
                    MainApplication.getPreferences("mmm").edit().putBoolean("pref_background_update_check", false).apply();
                    new MaterialAlertDialogBuilder(this.requireContext()).setTitle(R.string.permission_notification_title).setMessage(R.string.permission_notification_message).setPositiveButton(R.string.ok, (dialog, which) -> {
                        // Open the app settings
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", this.requireContext().getPackageName(), null);
                        intent.setData(uri);
                        this.startActivity(intent);
                    }).setNegativeButton(R.string.cancel, (dialog, which) -> {
                    }).show();
                    return true;
                });
                backgroundUpdateCheck.setSummary(R.string.background_update_check_permission_required);
            }
            updateCheckExcludes.setVisible(MainApplication.isBackgroundUpdateCheckEnabled() && !MainApplication.isWrapped());
            backgroundUpdateCheck.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = Boolean.parseBoolean(String.valueOf(newValue));
                debugNotification.setEnabled(enabled);
                debugNotification.setVisible(MainApplication.isDeveloper() && !MainApplication.isWrapped() && enabled);
                updateCheckExcludes.setEnabled(enabled);
                updateCheckExcludes.setVisible(enabled && !MainApplication.isWrapped());
                if (!enabled) {
                    BackgroundUpdateChecker.onMainActivityResume(this.requireContext());
                }
                return true;
            });
            // updateCheckExcludes saves to pref_background_update_check_excludes as a stringset. On clicking, it should open a dialog with a list of all installed modules
            updateCheckExcludes.setOnPreferenceClickListener(preference -> {
                Collection<LocalModuleInfo> localModuleInfos = ModuleManager.getINSTANCE().getModules().values();
                // make sure we have modules
                boolean[] checkedItems;
                if (!localModuleInfos.isEmpty()) {
                    String[] moduleNames = new String[localModuleInfos.size()];
                    checkedItems = new boolean[localModuleInfos.size()];
                    int i = 0;
                    for (LocalModuleInfo localModuleInfo : localModuleInfos) {
                        moduleNames[i] = localModuleInfo.name;
                        SharedPreferences sharedPreferences = MainApplication.getPreferences("mmm");
                        // get the stringset pref_background_update_check_excludes
                        Set<String> stringSet = sharedPreferences.getStringSet("pref_background_update_check_excludes", new HashSet<>());
                        // Stringset uses id, we show name
                        checkedItems[i] = stringSet.contains(localModuleInfo.id);
                        Timber.d("name: %s, checked: %s", moduleNames[i], checkedItems[i]);
                        i++;
                    }
                    new MaterialAlertDialogBuilder(this.requireContext()).setTitle(R.string.background_update_check_excludes).setMultiChoiceItems(moduleNames, checkedItems, (dialog, which, isChecked) -> {
                        // get the stringset pref_background_update_check_excludes
                        SharedPreferences sharedPreferences = MainApplication.getPreferences("mmm");
                        Set<String> stringSet = new HashSet<>(sharedPreferences.getStringSet("pref_background_update_check_excludes", new HashSet<>()));
                        // get id from name
                        String id;
                        if (localModuleInfos.stream().anyMatch(localModuleInfo -> localModuleInfo.name.equals(moduleNames[which]))) {
                            id = localModuleInfos.stream().filter(localModuleInfo -> localModuleInfo.name.equals(moduleNames[which])).findFirst().orElse(null).id;
                        } else {
                            id = "";
                        }
                        if (!id.isEmpty()) {
                            if (isChecked) {
                                stringSet.add(id);
                            } else {
                                stringSet.remove(id);
                            }
                        }
                        sharedPreferences.edit().putStringSet("pref_background_update_check_excludes", stringSet).apply();
                    }).setPositiveButton(R.string.ok, (dialog, which) -> {
                    }).show();
                } else {
                    new MaterialAlertDialogBuilder(this.requireContext()).setTitle(R.string.background_update_check_excludes).setMessage(R.string.background_update_check_excludes_no_modules).setPositiveButton(R.string.ok, (dialog, which) -> {
                    }).show();
                }
                return true;
            });
            final LibsBuilder libsBuilder = new LibsBuilder().withShowLoadingProgress(false).withLicenseShown(true).withAboutMinimalDesign(false);
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            LongClickablePreference linkClickable = findPreference("pref_update");
            linkClickable.setVisible(BuildConfig.ENABLE_AUTO_UPDATER && (BuildConfig.DEBUG || AppUpdateManager.getAppUpdateManager().peekHasUpdate()));
            linkClickable.setOnPreferenceClickListener(p -> {
                devModeStep = 0;
                // open UpdateActivity with CHECK action
                Intent intent = new Intent(requireContext(), UpdateActivity.class);
                intent.setAction(UpdateActivity.ACTIONS.CHECK.name());
                startActivity(intent);
                return true;
            });
            linkClickable.setOnPreferenceLongClickListener(p -> {
                String toastText = requireContext().getString(R.string.link_copied);
                clipboard.setPrimaryClip(ClipData.newPlainText(toastText, "https://github.com/Fox2Code/FoxMagiskModuleManager/releases/latest"));
                Toast.makeText(requireContext(), toastText, Toast.LENGTH_SHORT).show();
                return true;
            });
            // for pref_background_update_check_debug_download, do the same as pref_update except with DOWNLOAD action
            Preference debugDownload = findPreference("pref_background_update_check_debug_download");
            debugDownload.setVisible(MainApplication.isDeveloper() && MainApplication.isBackgroundUpdateCheckEnabled() && !MainApplication.isWrapped());
            debugDownload.setOnPreferenceClickListener(p -> {
                devModeStep = 0;
                Intent intent = new Intent(requireContext(), UpdateActivity.class);
                intent.setAction(UpdateActivity.ACTIONS.DOWNLOAD.name());
                startActivity(intent);
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
                    clipboard.setPrimaryClip(ClipData.newPlainText(toastText, "https://github.com/Fox2Code/FoxMagiskModuleManager/issues"));
                    Toast.makeText(requireContext(), toastText, Toast.LENGTH_SHORT).show();
                    return true;
                });
            } else {
                findPreference("pref_report_bug").setVisible(false);
            }
            linkClickable = findPreference("pref_source_code");
            // Set summary to the last commit this build was built from @ User/Repo
            // Build userRepo by removing all parts of REMOTE_URL that are not the user/repo
            String userRepo = BuildConfig.REMOTE_URL;
            // remove .git
            userRepo = userRepo.replaceAll("\\.git$", "");
            Timber.d("userRepo: %s", userRepo);

            // finalUserRepo is the user/repo part of REMOTE_URL
            // get everything after .com/ or .org/ or .io/ or .me/ or .net/ or .xyz/ or .tk/ or .co/ minus .git
            String finalUserRepo = userRepo.replaceAll("^(https?://)?(www\\.)?(github\\.com|gitlab\\.com|bitbucket\\.org|git\\.io|git\\.me|git\\.net|git\\.xyz|git\\.tk|git\\.co)/", "");
            linkClickable.setSummary(String.format(getString(R.string.source_code_summary), BuildConfig.COMMIT_HASH, finalUserRepo));
            Timber.d("finalUserRepo: %s", finalUserRepo);
            String finalUserRepo1 = userRepo;
            linkClickable.setOnPreferenceClickListener(p -> {
                if (devModeStep == 2) {
                    devModeStep = 0;
                    if (MainApplication.isDeveloper() && !BuildConfig.DEBUG) {
                        MainApplication.getPreferences("mmm").edit().putBoolean("developer", false).apply();
                        Toast.makeText(getContext(), // Tell the user something changed
                                R.string.dev_mode_disabled, Toast.LENGTH_SHORT).show();
                    } else {
                        MainApplication.getPreferences("mmm").edit().putBoolean("developer", true).apply();
                        Toast.makeText(getContext(), // Tell the user something changed
                                R.string.dev_mode_enabled, Toast.LENGTH_SHORT).show();
                    }
                    ExternalHelper.INSTANCE.refreshHelper(getContext());
                    return true;
                }
                // build url from BuildConfig.REMOTE_URL and BuildConfig.COMMIT_HASH. May have to remove the .git at the end
                IntentHelper.openUrl(p.getContext(), finalUserRepo1.replace(".git", "") + "/tree/" + BuildConfig.COMMIT_HASH);
                return true;
            });
            linkClickable.setOnPreferenceLongClickListener(p -> {
                String toastText = requireContext().getString(R.string.link_copied);
                clipboard.setPrimaryClip(ClipData.newPlainText(toastText, BuildConfig.REMOTE_URL + "/tree/" + BuildConfig.COMMIT_HASH));
                Toast.makeText(requireContext(), toastText, Toast.LENGTH_SHORT).show();
                return true;
            });
            // Next, the pref_androidacy_thanks should lead to the androidacy website
            linkClickable = findPreference("pref_androidacy_thanks");
            linkClickable.setOnPreferenceClickListener(p -> {
                IntentHelper.openUrl(p.getContext(), "https://www.androidacy.com?utm_source=FoxMagiskModuleManager&utm_medium=app&utm_campaign=FoxMagiskModuleManager");
                return true;
            });
            linkClickable.setOnPreferenceLongClickListener(p -> {
                String toastText = requireContext().getString(R.string.link_copied);
                clipboard.setPrimaryClip(ClipData.newPlainText(toastText, "https://www.androidacy.com?utm_source=FoxMagiskModuleManager&utm_medium=app&utm_campaign=FoxMagiskModuleManager"));
                Toast.makeText(requireContext(), toastText, Toast.LENGTH_SHORT).show();
                return true;
            });
            // pref_fox2code_thanks should lead to https://github.com/Fox2Code
            linkClickable = findPreference("pref_fox2code_thanks");
            linkClickable.setOnPreferenceClickListener(p -> {
                IntentHelper.openUrl(p.getContext(), "https://github.com/Fox2Code");
                return true;
            });
            linkClickable.setOnPreferenceLongClickListener(p -> {
                String toastText = requireContext().getString(R.string.link_copied);
                clipboard.setPrimaryClip(ClipData.newPlainText(toastText, "https://github.com/Fox2Code"));
                Toast.makeText(requireContext(), toastText, Toast.LENGTH_SHORT).show();
                return true;
            });
            // handle pref_save_logs which saves logs to our external storage and shares them
            Preference saveLogs = findPreference("pref_save_logs");
            saveLogs.setOnPreferenceClickListener(p -> {
                // Save logs to external storage
                File logsFile = new File(requireContext().getExternalFilesDir(null), "logs.txt");
                FileOutputStream fileOutputStream = null;
                try {
                    //noinspection ResultOfMethodCallIgnored
                    logsFile.createNewFile();
                    fileOutputStream = new FileOutputStream(logsFile);
                    // first, write some info about the device
                    fileOutputStream.write(("FoxMagiskModuleManager version: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n").getBytes());
                    fileOutputStream.write(("Android version: " + Build.VERSION.RELEASE + " (" + Build.VERSION.SDK_INT + ")\n").getBytes());
                    fileOutputStream.write(("Device: " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")\n").getBytes());
                    fileOutputStream.write(("Magisk version: " + InstallerInitializer.peekMagiskVersion() + "\n").getBytes());
                    fileOutputStream.write(("Has internet: " + (RepoManager.getINSTANCE().hasConnectivity() ? "Yes" : "No") + "\n").getBytes());
                    // read our logcat but format the output to be more readable
                    Process process = Runtime.getRuntime().exec("logcat -d -v tag");
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        fileOutputStream.write((line + "\n").getBytes());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(requireContext(), R.string.error_saving_logs, Toast.LENGTH_SHORT).show();
                    return true;
                } finally {
                    if (fileOutputStream != null) {
                        try {
                            fileOutputStream.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
                // Share logs
                Intent shareIntent = new Intent();
                // create a new intent and grantUriPermission to the file provider
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                shareIntent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(requireContext(), BuildConfig.APPLICATION_ID + ".file-provider", logsFile));
                shareIntent.setType("text/plain");
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_logs)));
                return true;
            });
            // pref_contributors should lead to the contributors page
            linkClickable = findPreference("pref_contributors");
            linkClickable.setOnPreferenceClickListener(p -> {
                // Remove the .git if it exists and add /graphs/contributors
                String url = BuildConfig.REMOTE_URL;
                if (url.endsWith(".git")) {
                    url = url.substring(0, url.length() - 4);
                }
                url += "/graphs/contributors";
                IntentHelper.openUrl(p.getContext(), url);
                return true;
            });
            linkClickable.setOnPreferenceLongClickListener(p -> {
                String toastText = requireContext().getString(R.string.link_copied);
                // Remove the .git if it exists and add /graphs/contributors
                String url = BuildConfig.REMOTE_URL;
                if (url.endsWith(".git")) {
                    url = url.substring(0, url.length() - 4);
                }
                url += "/graphs/contributors";
                clipboard.setPrimaryClip(ClipData.newPlainText(toastText, url));
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
                clipboard.setPrimaryClip(ClipData.newPlainText(toastText, "https://t.me/Fox2Code_Chat"));
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
            String flavor = BuildConfig.FLAVOR;
            String type = BuildConfig.BUILD_TYPE;
            // Set the summary of pref_pkg_info to something like default-debug v1.0 (123) (Official)
            String pkgInfo = getString(R.string.pref_pkg_info_summary, flavor + "-" + type, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, MainApplication.isOfficial ? getString(R.string.official) : getString(R.string.unofficial));
            findPreference("pref_pkg_info").setSummary(pkgInfo);
            // special easter egg :)
            var ref = new Object() {
                int versionClicks = 0;
            };
            findPreference("pref_pkg_info").setOnPreferenceClickListener(p -> {
                ref.versionClicks++;
                Timber.d("Version clicks: %d", ref.versionClicks);
                // if it's been 3 clicks, toast "yer a wizard, harry" or "keep tapping to enter hogwarts"
                if (ref.versionClicks == 3) {
                    // pick 1 or 2
                    int random = new Random().nextInt(2) + 1;
                    Toast.makeText(p.getContext(), random == 1 ? R.string.yer_a_wizard_harry : R.string.keep_tapping_to_enter_hogwarts, Toast.LENGTH_SHORT).show();
                }
                if (ref.versionClicks == 7) {
                    ref.versionClicks = 0;
                    IntentHelper.openUrl(p.getContext(), "https://www.youtube.com/watch?v=dQw4w9WgXcQ");
                }
                return true;
            });
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
            if (!this.getResources().getConfiguration().getLocales().get(0).getLanguage().equals("en") && this.getResources().getString(R.string.notification_update_pref).equals("Background modules update check") && this.getResources().getString(R.string.notification_update_desc).equals("May increase battery usage")) {
                return 0;
            }
            return LANGUAGE_SUPPORT_LEVEL;
        }
    }

    @SuppressWarnings("ConstantConditions")
    public static class RepoFragment extends PreferenceFragmentCompat {

        /**
         * <i>says proudly</i>: I stole it
         * <p>
         * namely, from <a href="https://github.com/NeoApplications/Neo-Wellbeing/blob/9fca4136263780c022f9ec6433c0b43d159166db/app/src/main/java/org/eu/droid_ng/wellbeing/prefs/SettingsActivity.java#L101">neo wellbeing</a>
         */
        public static void applyMaterial3(Preference p) {
            if (p instanceof PreferenceGroup pg) {
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
            // Also require dev mode
            // CaptchaWebview.setVisible(false);
            Preference androidacyTestMode = Objects.requireNonNull(findPreference("pref_androidacy_test_mode"));
            if (!MainApplication.isDeveloper()) {
                androidacyTestMode.setVisible(false);
            } else {
                // Show a warning if user tries to enable test mode
                androidacyTestMode.setOnPreferenceChangeListener((preference, newValue) -> {
                    if (Boolean.parseBoolean(String.valueOf(newValue))) {
                        // Use MaterialAlertDialogBuilder
                        new MaterialAlertDialogBuilder(this.requireContext()).setTitle(R.string.warning).setCancelable(false).setMessage(R.string.androidacy_test_mode_warning).setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            // User clicked OK button
                            MainApplication.getPreferences("mmm").edit().putBoolean("androidacy_test_mode", true).apply();
                            // Check the switch
                            Intent mStartActivity = new Intent(requireContext(), MainActivity.class);
                            mStartActivity.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            int mPendingIntentId = 123456;
                            // If < 23, FLAG_IMMUTABLE is not available
                            PendingIntent mPendingIntent;
                            mPendingIntent = PendingIntent.getActivity(requireContext(), mPendingIntentId, mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                            AlarmManager mgr = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
                            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
                            Timber.d("Restarting app to save staging endpoint preference: %s", newValue);
                            System.exit(0); // Exit app process
                        }).setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                            // User cancelled the dialog
                            // Uncheck the switch
                            SwitchPreferenceCompat switchPreferenceCompat = (SwitchPreferenceCompat) androidacyTestMode;
                            switchPreferenceCompat.setChecked(false);
                            // There's probably a better way to do this than duplicate code but I'm too lazy to figure it out
                            MainApplication.getPreferences("mmm").edit().putBoolean("androidacy_test_mode", false).apply();
                        }).show();
                    } else {
                        MainApplication.getPreferences("mmm").edit().putBoolean("androidacy_test_mode", false).apply();
                        // Show dialog to restart app with ok button
                        new MaterialAlertDialogBuilder(this.requireContext()).setTitle(R.string.warning).setCancelable(false).setMessage(R.string.androidacy_test_mode_disable_warning).setNeutralButton(android.R.string.ok, (dialog, which) -> {
                            // User clicked OK button
                            Intent mStartActivity = new Intent(requireContext(), MainActivity.class);
                            mStartActivity.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            int mPendingIntentId = 123456;
                            // If < 23, FLAG_IMMUTABLE is not available
                            PendingIntent mPendingIntent;
                            mPendingIntent = PendingIntent.getActivity(requireContext(), mPendingIntentId, mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                            AlarmManager mgr = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
                            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
                            Timber.d("Restarting app to save staging endpoint preference: %s", newValue);
                            System.exit(0); // Exit app process
                        }).show();
                    }
                    return true;
                });
            }
            // Get magisk_alt_repo enabled state from realm db
            RealmConfiguration realmConfig = new RealmConfiguration.Builder().name("ReposList.realm").encryptionKey(MainApplication.getINSTANCE().getExistingKey()).allowQueriesOnUiThread(true).allowWritesOnUiThread(true).directory(MainApplication.getINSTANCE().getDataDirWithPath("realms")).schemaVersion(1).build();
            Realm realm1 = Realm.getInstance(realmConfig);
            ReposList reposList = realm1.where(ReposList.class).equalTo("id", "magisk_alt_repo").findFirst();
            if (reposList != null) {
                // Set the switch to the current state
                SwitchPreferenceCompat magiskAltRepoEnabled = Objects.requireNonNull(findPreference("pref_magisk_alt_repo_enabled"));
                magiskAltRepoEnabled.setChecked(reposList.isEnabled());
            }
            // add listener to magisk_alt_repo_enabled switch to update realm db
            Preference magiskAltRepoEnabled = Objects.requireNonNull(findPreference("pref_magisk_alt_repo_enabled"));
            magiskAltRepoEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
                // Update realm db
                Realm realm = Realm.getInstance(realmConfig);
                realm.executeTransaction(realm2 -> {
                    ReposList reposList1 = realm2.where(ReposList.class).equalTo("id", "magisk_alt_repo").findFirst();
                    if (reposList1 != null) {
                        reposList1.setEnabled(Boolean.parseBoolean(String.valueOf(newValue)));
                    }
                });
                return true;
            });
            // Disable toggling the pref_androidacy_repo_enabled on builds without an
            // ANDROIDACY_CLIENT_ID or where the ANDROIDACY_CLIENT_ID is empty
            Preference androidacyRepoEnabled = Objects.requireNonNull(findPreference("pref_androidacy_repo_enabled"));
            if (Objects.equals(BuildConfig.ANDROIDACY_CLIENT_ID, "")) {
                androidacyRepoEnabled.setOnPreferenceClickListener(preference -> {
                    new MaterialAlertDialogBuilder(this.requireContext()).setTitle(R.string.androidacy_repo_disabled).setCancelable(false).setMessage(R.string.androidacy_repo_disabled_message).setPositiveButton(R.string.download_full_app, (dialog, which) -> {
                        // User clicked OK button. Open GitHub releases page
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.androidacy.com/downloads/?view=FoxMMM&utm_source=FoxMMM&utm_medium=app&utm_campaign=FoxMMM"));
                        startActivity(browserIntent);
                    }).show();
                    // Revert the switch to off
                    SwitchPreferenceCompat switchPreferenceCompat = (SwitchPreferenceCompat) androidacyRepoEnabled;
                    switchPreferenceCompat.setChecked(false);
                    // Disable in realm db
                    RealmConfiguration realmConfiguration = new RealmConfiguration.Builder().name("ReposList.realm").encryptionKey(MainApplication.getINSTANCE().getExistingKey()).allowQueriesOnUiThread(true).allowWritesOnUiThread(true).directory(MainApplication.getINSTANCE().getDataDirWithPath("realms")).schemaVersion(1).build();
                    Realm realm = Realm.getInstance(realmConfiguration);
                    realm.executeTransaction(realm2 -> {
                        ReposList repoRealmResults = realm2.where(ReposList.class).equalTo("id", "androidacy_repo").findFirst();
                        assert repoRealmResults != null;
                        repoRealmResults.setEnabled(false);
                        realm2.insertOrUpdate(repoRealmResults);
                        realm2.close();
                    });
                    return false;
                });
            }
            // get if androidacy repo is enabled from realm db
            RealmConfiguration realmConfiguration = new RealmConfiguration.Builder().name("ReposList.realm").encryptionKey(MainApplication.getINSTANCE().getExistingKey()).allowQueriesOnUiThread(true).allowWritesOnUiThread(true).directory(MainApplication.getINSTANCE().getDataDirWithPath("realms")).schemaVersion(1).build();
            Realm realm = Realm.getInstance(realmConfiguration);
            ReposList repoRealmResults = realm.where(ReposList.class).equalTo("id", "androidacy_repo").findFirst();
            if (repoRealmResults == null) {
                // log the entries in the realm db and throw an illegal state exception
                RealmResults<ReposList> reposListRealmResults = realm.where(ReposList.class).findAll();
                if (reposListRealmResults.isEmpty()) {
                    throw new IllegalStateException("Realm db is empty");
                }
                throw new IllegalStateException("Androidacy repo not found in realm db");
            }
            boolean androidacyRepoEnabledPref = repoRealmResults.isEnabled();
            if (androidacyRepoEnabledPref) {
                // get user role from AndroidacyRepoData.userInfo
                String[][] userInfo = AndroidacyRepoData.getInstance().userInfo;
                if (userInfo != null) {
                    String userRole = userInfo[0][1];
                    if (Objects.nonNull(userRole) && !Objects.equals(userRole, "Guest")) {
                        // Disable the pref_androidacy_repo_api_token preference
                        LongClickablePreference prefAndroidacyRepoApiD = Objects.requireNonNull(findPreference("pref_androidacy_repo_donate"));
                        prefAndroidacyRepoApiD.setEnabled(false);
                        prefAndroidacyRepoApiD.setSummary(R.string.upgraded_summary);
                        prefAndroidacyRepoApiD.setTitle(R.string.upgraded);
                        prefAndroidacyRepoApiD.setIcon(R.drawable.baseline_check_24);
                    }
                }
                String[] originalApiKeyRef = new String[]{MainApplication.getINSTANCE().getSharedPreferences("androidacy", 0).getString("pref_androidacy_api_token", "")};
                // Get the dummy pref_androidacy_repo_api_token preference with id pref_androidacy_repo_api_token
                // we have to use the id because the key is different
                EditTextPreference prefAndroidacyRepoApiKey = Objects.requireNonNull(findPreference("pref_androidacy_repo_api_token"));
                // add validation to the EditTextPreference
                // string must be 64 characters long, and only allows alphanumeric characters
                prefAndroidacyRepoApiKey.setTitle(R.string.api_key);
                prefAndroidacyRepoApiKey.setSummary(R.string.api_key_summary);
                prefAndroidacyRepoApiKey.setDialogTitle(R.string.api_key);
                prefAndroidacyRepoApiKey.setDefaultValue(originalApiKeyRef[0]);
                // Set the value to the current value
                prefAndroidacyRepoApiKey.setText(originalApiKeyRef[0]);
                prefAndroidacyRepoApiKey.setVisible(true);
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
                    // validate the api key client side first. should be 64 characters long, and only allow alphanumeric characters
                    if (!newValue.toString().matches("[a-zA-Z0-9]{64}")) {
                        // Show snack bar with error
                        Snackbar.make(requireView(), R.string.api_key_mismatch, BaseTransientBottomBar.LENGTH_LONG).show();
                        // Restore the original api key
                        prefAndroidacyRepoApiKey.setText(originalApiKeyRef[0]);
                        prefAndroidacyRepoApiKey.performClick();
                        return false;
                    }
                    // Make sure originalApiKeyRef is not null
                    if (originalApiKeyRef[0].equals(newValue)) return true;
                    // get original api key
                    String apiKey = String.valueOf(newValue);
                    // Show snack bar with indeterminate progress
                    Snackbar.make(requireView(), R.string.checking_api_key, BaseTransientBottomBar.LENGTH_INDEFINITE).setAction(R.string.cancel, v -> {
                        // Restore the original api key
                        prefAndroidacyRepoApiKey.setText(originalApiKeyRef[0]);
                    }).show();
                    // Check the API key on a background thread
                    new Thread(() -> {
                        // If key is empty, just remove it and change the text of the snack bar
                        if (apiKey.isEmpty()) {
                            MainApplication.getINSTANCE().getSharedPreferences("androidacy", 0).edit().remove("pref_androidacy_api_token").apply();
                            new Handler(Looper.getMainLooper()).post(() -> {
                                Snackbar.make(requireView(), R.string.api_key_removed, BaseTransientBottomBar.LENGTH_SHORT).show();
                                // Show dialog to restart app with ok button
                                new MaterialAlertDialogBuilder(this.requireContext()).setTitle(R.string.restart).setCancelable(false).setMessage(R.string.api_key_restart).setNeutralButton(android.R.string.ok, (dialog, which) -> {
                                    // User clicked OK button
                                    Intent mStartActivity = new Intent(requireContext(), MainActivity.class);
                                    mStartActivity.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                    int mPendingIntentId = 123456;
                                    // If < 23, FLAG_IMMUTABLE is not available
                                    PendingIntent mPendingIntent;
                                    mPendingIntent = PendingIntent.getActivity(requireContext(), mPendingIntentId, mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                                    AlarmManager mgr = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
                                    mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
                                    Timber.d("Restarting app to save token preference: %s", newValue);
                                    System.exit(0); // Exit app process
                                }).show();
                            });
                        } else {
                            // If key < 64 chars, it's not valid
                            if (apiKey.length() < 64) {
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    Snackbar.make(requireView(), R.string.api_key_invalid, BaseTransientBottomBar.LENGTH_SHORT).show();
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
                                    new Handler(Looper.getMainLooper()).post(() -> Snackbar.make(requireView(), R.string.api_key_unchanged, BaseTransientBottomBar.LENGTH_SHORT).show());
                                    return;
                                }
                                boolean valid = false;
                                try {
                                    valid = AndroidacyRepoData.getInstance().isValidToken(apiKey);
                                } catch (IOException ignored) {
                                }
                                // If the key is valid, save it
                                if (valid) {
                                    originalApiKeyRef[0] = apiKey;
                                    RepoManager.getINSTANCE().getAndroidacyRepoData().setToken(apiKey);
                                    MainApplication.getINSTANCE().getSharedPreferences("androidacy", 0).edit().putString("pref_androidacy_api_token", apiKey).apply();
                                    // Snackbar with success and restart button
                                    new Handler(Looper.getMainLooper()).post(() -> {
                                        Snackbar.make(requireView(), R.string.api_key_valid, BaseTransientBottomBar.LENGTH_SHORT).show();
                                        // Show dialog to restart app with ok button
                                        new MaterialAlertDialogBuilder(this.requireContext()).setTitle(R.string.restart).setCancelable(false).setMessage(R.string.api_key_restart).setNeutralButton(android.R.string.ok, (dialog, which) -> {
                                            // User clicked OK button
                                            Intent mStartActivity = new Intent(requireContext(), MainActivity.class);
                                            mStartActivity.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                            int mPendingIntentId = 123456;
                                            // If < 23, FLAG_IMMUTABLE is not available
                                            PendingIntent mPendingIntent;
                                            mPendingIntent = PendingIntent.getActivity(requireContext(), mPendingIntentId, mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                                            AlarmManager mgr = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
                                            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
                                            Timber.d("Restarting app to save token preference: %s", newValue);
                                            System.exit(0); // Exit app process
                                        }).show();
                                    });
                                } else {
                                    new Handler(Looper.getMainLooper()).post(() -> {
                                        Snackbar.make(requireView(), R.string.api_key_invalid, BaseTransientBottomBar.LENGTH_SHORT).show();
                                        // Save the original key
                                        MainApplication.getINSTANCE().getSharedPreferences("androidacy", 0).edit().putString("pref_androidacy_api_token", originalApiKeyRef[0]).apply();
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
        }

        @SuppressLint("RestrictedApi")
        public void updateCustomRepoList(boolean initial) {
            RealmConfiguration realmConfiguration = new RealmConfiguration.Builder().name("ReposList.realm").encryptionKey(MainApplication.getINSTANCE().getExistingKey()).allowQueriesOnUiThread(true).allowWritesOnUiThread(true).directory(MainApplication.getINSTANCE().getDataDirWithPath("realms")).schemaVersion(1).build();
            Realm realm = Realm.getInstance(realmConfiguration);
            // get all repos that are not built-in
            int CUSTOM_REPO_ENTRIES = 0;
            RealmResults<ReposList> customRepoDataDB = realm.where(ReposList.class).findAll();
            for (ReposList repo : customRepoDataDB) {
                if (!repo.getId().equals("androidacy") && !repo.getId().equals("magisk_alt_repo")) {
                    CUSTOM_REPO_ENTRIES++;
                }
            }
            final CustomRepoManager customRepoManager = RepoManager.getINSTANCE().getCustomRepoManager();
            for (int i = 0; i < CUSTOM_REPO_ENTRIES; i++) {
                CustomRepoData repoData = customRepoManager.getRepo(i);
                setRepoData(repoData, "pref_custom_repo_" + i);
                if (initial) {
                    Preference preference = findPreference("pref_custom_repo_" + i + "_delete");
                    if (preference == null) continue;
                    final int index = i;
                    preference.setOnPreferenceClickListener(preference1 -> {
                        if (realm.isInTransaction()) {
                            realm.commitTransaction();
                        }
                        realm.beginTransaction();
                        Objects.requireNonNull(realm.where(ReposList.class).equalTo("id", repoData.id).findFirst()).deleteFromRealm();
                        realm.commitTransaction();
                        customRepoManager.removeRepo(index);
                        updateCustomRepoList(false);
                        return true;
                    });
                }
            }
            Preference preference = findPreference("pref_custom_add_repo");
            if (preference == null) return;
            preference.setVisible(customRepoManager.canAddRepo() && customRepoManager.getRepoCount() < CUSTOM_REPO_ENTRIES);
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
                    // make link in message clickable
                    //noinspection deprecation
                    builder.setMessage(Html.fromHtml(getString(R.string.add_repo_message)));
                    builder.setView(input);
                    builder.setPositiveButton("OK", (dialog, which) -> {
                        String text = String.valueOf(input.getText());
                        // string should not be empty, start with https://, and not contain any spaces. http links are not allowed.
                        if (text.matches("^https://.*") && !text.contains(" ") && !text.isEmpty()) {
                            if (customRepoManager.canAddRepo(text)) {
                                final CustomRepoData customRepoData = customRepoManager.addRepo(text);
                                new Thread("Add Custom Repo Thread") {
                                    @Override
                                    public void run() {
                                        try {
                                            customRepoData.quickPrePopulate();
                                            UiThreadHandler.handler.post(() -> updateCustomRepoList(false));
                                        } catch (Exception e) {
                                            Timber.e(e);
                                            // show new dialog
                                            new Handler(Looper.getMainLooper()).post(() -> new MaterialAlertDialogBuilder(context).setTitle(R.string.error_adding).setMessage(e.getMessage()).setPositiveButton(android.R.string.ok, (dialog1, which1) -> {
                                            }).show());
                                        }
                                    }
                                }.start();
                            } else {
                                Snackbar.make(requireView(), R.string.invalid_repo_url, BaseTransientBottomBar.LENGTH_LONG).show();
                            }
                        } else {
                            Snackbar.make(requireView(), R.string.invalid_repo_url, BaseTransientBottomBar.LENGTH_LONG).show();
                        }
                    });
                    builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
                    builder.setNeutralButton("Docs", (dialog, which) -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Fox2Code/FoxMagiskModuleManager/blob/master/docs/DEVELOPERS.md#custom-repo-format"));
                        startActivity(intent);
                    });
                    AlertDialog alertDialog = builder.show();
                    final Button positiveButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    input.setValidator(new AutoCompleteTextView.Validator() {
                        @Override
                        public boolean isValid(CharSequence charSequence) {
                            Timber.i("checking repo url validity");
                            // show error if string is empty, does not start with https://, or contains spaces
                            if (charSequence.toString().isEmpty()) {
                                input.setError(getString(R.string.empty_field));
                                Timber.d("No input for repo");
                                positiveButton.setEnabled(false);
                                return false;
                            } else if (!charSequence.toString().matches("^https://.*")) {
                                input.setError(getString(R.string.invalid_repo_url));
                                Timber.d("Non https link for repo");
                                return false;
                            } else if (charSequence.toString().contains(" ")) {
                                input.setError(getString(R.string.invalid_repo_url));
                                Timber.d("Repo url has space");
                                positiveButton.setEnabled(false);
                                return false;
                            } else if (!customRepoManager.canAddRepo(charSequence.toString())) {
                                input.setError(getString(R.string.repo_already_added));
                                Timber.d("Could not add repo for misc reason");
                                positiveButton.setEnabled(false);
                                return false;
                            } else {
                                // enable ok button
                                Timber.d("Repo URL is ok");
                                positiveButton.setEnabled(true);
                                return true;
                            }
                        }

                        @Override
                        public CharSequence fixText(CharSequence invalidText) {
                            return invalidText;
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
            if (repoData == null) return;
            Timber.d("Setting preference " + preferenceName + " to " + repoData);
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            Preference preference = findPreference(preferenceName);
            if (preference == null) return;
            if (!preferenceName.contains("androidacy") && !preferenceName.contains("magisk_alt_repo")) {
                if (repoData != null) {
                    RealmConfiguration realmConfiguration = new RealmConfiguration.Builder().name("ReposList.realm").encryptionKey(MainApplication.getINSTANCE().getExistingKey()).allowQueriesOnUiThread(true).allowWritesOnUiThread(true).directory(MainApplication.getINSTANCE().getDataDirWithPath("realms")).schemaVersion(1).build();
                    Realm realm = Realm.getInstance(realmConfiguration);
                    RealmResults<ReposList> repoDataRealmResults = realm.where(ReposList.class).equalTo("id", repoData.id).findAll();
                    Timber.d("Setting preference " + preferenceName + " because it is not the Androidacy repo or the Magisk Alt Repo");
                    if (repoData.isForceHide() || repoDataRealmResults.isEmpty()) {
                        Timber.d("Hiding preference " + preferenceName + " because it is null or force hidden");
                        hideRepoData(preferenceName);
                        return;
                    } else {
                        //noinspection ConstantConditions
                        Timber.d("Showing preference %s because the forceHide status is %s and the RealmResults is %s", preferenceName, repoData.isForceHide(), repoDataRealmResults.toString());
                        preference.setTitle(repoData.getName());
                        preference.setVisible(true);
                    }
                } else {
                    Timber.d("Hiding preference " + preferenceName + " because it's data is null");
                    hideRepoData(preferenceName);
                    return;
                }
            }
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
                        // Show snackbar telling the user to refresh the modules list or restart the app
                        Snackbar.make(requireView(), R.string.repo_enabled_changed, BaseTransientBottomBar.LENGTH_LONG).show();
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
