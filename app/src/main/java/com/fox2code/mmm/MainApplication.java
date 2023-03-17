package com.fox2code.mmm;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.core.app.NotificationManagerCompat;
import androidx.emoji2.text.DefaultEmojiCompatConfig;
import androidx.emoji2.text.EmojiCompat;
import androidx.emoji2.text.FontRequestEmojiCompatConfig;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.fox2code.foxcompat.app.FoxActivity;
import com.fox2code.foxcompat.app.FoxApplication;
import com.fox2code.foxcompat.app.internal.FoxProcessExt;
import com.fox2code.foxcompat.view.FoxThemeWrapper;
import com.fox2code.mmm.installer.InstallerInitializer;
import com.fox2code.mmm.utils.io.GMSProviderInstaller;
import com.fox2code.mmm.utils.io.Http;
import com.fox2code.mmm.utils.sentry.SentryMain;
import com.fox2code.rosettax.LanguageSwitcher;
import com.google.common.hash.Hashing;
import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

import io.noties.markwon.Markwon;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.ImagesPlugin;
import io.noties.markwon.image.network.OkHttpNetworkSchemeHandler;
import io.realm.Realm;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import io.sentry.android.timber.SentryTimberTree;
import timber.log.Timber;

@SuppressWarnings("CommentedOutCode")
public class MainApplication extends FoxApplication implements androidx.work.Configuration.Provider {
    // Warning! Locales that don't exist will crash the app
    // Anything that is commented out is supported but the translation is not complete to at least 60%
    public static final HashSet<String> supportedLocales = new HashSet<>();
    private static final String timeFormatString = "dd MMM yyyy"; // Example: 13 july 2001
    private static final Shell.Builder shellBuilder;
    @SuppressLint("RestrictedApi")
    // Use FoxProcess wrapper helper.
    private static final boolean wrapped = !FoxProcessExt.isRootLoader();
    public static boolean isOfficial = false;
    private static long secret;
    private static Locale timeFormatLocale = Resources.getSystem().getConfiguration().getLocales().get(0);
    private static SimpleDateFormat timeFormat = new SimpleDateFormat(timeFormatString, timeFormatLocale);
    private static String relPackageName = BuildConfig.APPLICATION_ID;
    @SuppressLint("StaticFieldLeak")
    private static MainApplication INSTANCE;
    private static boolean firstBoot;

    static {
        Shell.setDefaultBuilder(shellBuilder = Shell.Builder.create().setFlags(Shell.FLAG_REDIRECT_STDERR).setTimeout(10).setInitializers(InstallerInitializer.class));
        Random random = new Random();
        do {
            secret = random.nextLong();
        } while (secret == 0);
    }

    @StyleRes
    private int managerThemeResId = R.style.Theme_MagiskModuleManager;
    private FoxThemeWrapper markwonThemeContext;
    private Markwon markwon;

    public MainApplication() {
        if (INSTANCE != null && INSTANCE != this)
            throw new IllegalStateException("Duplicate application instance!");
        INSTANCE = this;
    }

    public static Shell build(String... command) {
        return shellBuilder.build(command);
    }

    public static void addSecret(Intent intent) {
        ComponentName componentName = intent.getComponent();
        String packageName = componentName != null ? componentName.getPackageName() : intent.getPackage();
        if (!BuildConfig.APPLICATION_ID.equalsIgnoreCase(packageName) && !relPackageName.equals(packageName)) {
            // Code safeguard, we should never reach here.
            throw new IllegalArgumentException("Can't add secret to outbound Intent");
        }
        intent.putExtra("secret", secret);
    }

    public static SharedPreferences getSharedPreferences(String name) {
        // encryptedSharedPreferences is used
        MasterKey mainKeyAlias;
        try {
            mainKeyAlias = new MasterKey.Builder(MainApplication.getINSTANCE().getApplicationContext()).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
        SharedPreferences mSharedPreferences;
        try {
            mSharedPreferences = EncryptedSharedPreferences.create(MainApplication.getINSTANCE().getApplicationContext(), name, mainKeyAlias, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
        return mSharedPreferences;
    }

    // Is application wrapped, and therefore must reduce it's feature set.
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isWrapped() {
        return wrapped;
    }

    public static boolean checkSecret(Intent intent) {
        return intent != null && intent.getLongExtra("secret", ~secret) == secret;
    }

    public static boolean isShowcaseMode() {
        return getSharedPreferences("mmm").getBoolean("pref_showcase_mode", false);
    }

    public static boolean shouldPreventReboot() {
        return getSharedPreferences("mmm").getBoolean("pref_prevent_reboot", true);
    }

    public static boolean isShowIncompatibleModules() {
        return getSharedPreferences("mmm").getBoolean("pref_show_incompatible", false);
    }

    public static boolean isForceDarkTerminal() {
        return getSharedPreferences("mmm").getBoolean("pref_force_dark_terminal", false);
    }

    public static boolean isTextWrapEnabled() {
        return getSharedPreferences("mmm").getBoolean("pref_wrap_text", false);
    }

    public static boolean isDohEnabled() {
        return getSharedPreferences("mmm").getBoolean("pref_dns_over_https", true);
    }

    public static boolean isMonetEnabled() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && getSharedPreferences("mmm").getBoolean("pref_enable_monet", true);
    }

    public static boolean isBlurEnabled() {
        return getSharedPreferences("mmm").getBoolean("pref_enable_blur", false);
    }

    public static boolean isDeveloper() {
        if (BuildConfig.DEBUG)
            return true;
        return getSharedPreferences("mmm").getBoolean("developer", false);
    }

    public static boolean isDisableLowQualityModuleFilter() {
        return getSharedPreferences("mmm").getBoolean("pref_disable_low_quality_module_filter", false) && isDeveloper();
    }

    public static boolean isUsingMagiskCommand() {
        return InstallerInitializer.peekMagiskVersion() >= Constants.MAGISK_VER_CODE_INSTALL_COMMAND && getSharedPreferences("mmm").getBoolean("pref_use_magisk_install_command", false) && isDeveloper();
    }

    public static boolean isBackgroundUpdateCheckEnabled() {
        return !wrapped && getSharedPreferences("mmm").getBoolean("pref_background_update_check", true);
    }

    public static boolean isAndroidacyTestMode() {
        return isDeveloper() && getSharedPreferences("mmm").getBoolean("pref_androidacy_test_mode", false);
    }

    public static boolean isFirstBoot() {
        return firstBoot;
    }

    public static void setHasGottenRootAccess(boolean bool) {
        getSharedPreferences("mmm").edit().putBoolean("has_root_access", bool).apply();
    }

    public static boolean isCrashReportingEnabled() {
        return SentryMain.IS_SENTRY_INSTALLED && getSharedPreferences("mmm").getBoolean("pref_crash_reporting", BuildConfig.DEFAULT_ENABLE_CRASH_REPORTING);
    }

    public static SharedPreferences getBootSharedPreferences() {
        return getSharedPreferences("mmm_boot");
    }

    public static MainApplication getINSTANCE() {
        return INSTANCE;
    }

    public static String formatTime(long timeStamp) {
        // new Date(x) also get the local timestamp for format
        return timeFormat.format(new Date(timeStamp));
    }

    public static boolean isNotificationPermissionGranted() {
        return NotificationManagerCompat.from(INSTANCE).areNotificationsEnabled();
    }

    public Markwon getMarkwon() {
        if (this.markwon != null)
            return this.markwon;
        FoxThemeWrapper contextThemeWrapper = this.markwonThemeContext;
        if (contextThemeWrapper == null) {
            contextThemeWrapper = this.markwonThemeContext = new FoxThemeWrapper(this, this.managerThemeResId);
        }
        Markwon markwon = Markwon.builder(contextThemeWrapper).usePlugin(HtmlPlugin.create()).usePlugin(ImagesPlugin.create().addSchemeHandler(OkHttpNetworkSchemeHandler.create(Http.getHttpClientWithCache()))).build();
        return this.markwon = markwon;
    }

    @NonNull
    @Override
    public androidx.work.Configuration getWorkManagerConfiguration() {
        return new androidx.work.Configuration.Builder().build();
    }

    public void updateTheme() {
        @StyleRes int themeResId;
        String theme;
        boolean monet = isMonetEnabled();
        switch (theme = getSharedPreferences("mmm").getString("pref_theme", "system")) {
            default:
                Timber.w("Unknown theme id: %s", theme);
            case "system":
                themeResId = monet ? R.style.Theme_MagiskModuleManager_Monet : R.style.Theme_MagiskModuleManager;
                break;
            case "dark":
                themeResId = monet ? R.style.Theme_MagiskModuleManager_Monet_Dark : R.style.Theme_MagiskModuleManager_Dark;
                break;
            case "black":
                themeResId = monet ? R.style.Theme_MagiskModuleManager_Monet_Black : R.style.Theme_MagiskModuleManager_Black;
                break;
            case "light":
                themeResId = monet ? R.style.Theme_MagiskModuleManager_Monet_Light : R.style.Theme_MagiskModuleManager_Light;
                break;
            case "transparent_light":
                if (monet) {
                    Timber.tag("MainApplication").w("Monet is not supported for transparent theme");
                }
                themeResId = R.style.Theme_MagiskModuleManager_Transparent_Light;
                break;
        }
        this.setManagerThemeResId(themeResId);
    }

    @StyleRes
    public int getManagerThemeResId() {
        return managerThemeResId;
    }

    @SuppressLint("NonConstantResourceId")
    public void setManagerThemeResId(@StyleRes int resId) {
        this.managerThemeResId = resId;
        if (this.markwonThemeContext != null) {
            this.markwonThemeContext.setTheme(resId);
        }
        this.markwon = null;
    }

    @SuppressLint("NonConstantResourceId")
    public boolean isLightTheme() {
        return switch (this.managerThemeResId) {
            case R.style.Theme_MagiskModuleManager, R.style.Theme_MagiskModuleManager_Monet ->
                    (this.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
            case R.style.Theme_MagiskModuleManager_Monet_Light, R.style.Theme_MagiskModuleManager_Light ->
                    true;
            case R.style.Theme_MagiskModuleManager_Monet_Dark, R.style.Theme_MagiskModuleManager_Dark, R.style.Theme_MagiskModuleManager_Monet_Black, R.style.Theme_MagiskModuleManager_Black ->
                    false;
            default -> super.isLightTheme();
        };
    }

    @SuppressWarnings("unused")
    @SuppressLint("NonConstantResourceId")
    public boolean isDarkTheme() {
        return !this.isLightTheme();
    }

    @Override
    public void onCreate() {
        // init timber
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        } else {
            if (isCrashReportingEnabled()) {
                //noinspection UnstableApiUsage
                Timber.plant(new SentryTimberTree(Sentry.getCurrentHub(), SentryLevel.ERROR, SentryLevel.ERROR));
            } else {
                Timber.plant(new ReleaseTree());
            }
        }
        // supportedLocales.add("ar");
        // supportedLocales.add("ar_SA");
        supportedLocales.add("cs");
        supportedLocales.add("de");
        // supportedLocales.add("el");
        supportedLocales.add("es");
        supportedLocales.add("es-rMX");
        // supportedLocales.add("et");
        supportedLocales.add("fr");
        supportedLocales.add("id");
        supportedLocales.add("it");
        // supportedLocales.add("ja");
        // supportedLocales.add("nb-rNO");
        supportedLocales.add("pl");
        supportedLocales.add("pt-rBR");
        supportedLocales.add("ro");
        supportedLocales.add("ru");
        supportedLocales.add("sk");
        supportedLocales.add("tr");
        supportedLocales.add("uk");
        // supportedLocales.add("vi");
        supportedLocales.add("zh-rCH");
        // supportedLocales.add("zh-rTW");
        supportedLocales.add("en");
        if (INSTANCE == null)
            INSTANCE = this;
        relPackageName = this.getPackageName();
        Timber.d("Starting FoxMMM version " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + "), commit " + BuildConfig.COMMIT_HASH);
        super.onCreate();
        // Update SSL Ciphers if update is possible
        GMSProviderInstaller.installIfNeeded(this);
        if (BuildConfig.DEBUG) {
            Timber.d("Initializing FoxMMM");
            Timber.d("Started from background: %s", !isInForeground());
            Timber.d("FoxMMM is running in debug mode");
            Timber.d("Initializing Realm");
        }
        Realm.init(this);
        Timber.d("Initialized Realm");
        // Determine if this is an official build based on the signature
        try {
            // Get the signature of the key used to sign the app
            @SuppressLint("PackageManagerGetSignatures") Signature[] signatures = this.getPackageManager().getPackageInfo(this.getPackageName(), PackageManager.GET_SIGNATURES).signatures;
            @SuppressWarnings("SpellCheckingInspection") String[] officialSignatureHashArray = new String[]{"7bec7c4462f4aac616612d9f56a023ee3046e83afa956463b5fab547fd0a0be6", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"};
            String ourSignatureHash = Hashing.sha256().hashBytes(signatures[0].toByteArray()).toString();
            isOfficial = Arrays.asList(officialSignatureHashArray).contains(ourSignatureHash);
        } catch (
                PackageManager.NameNotFoundException ignored) {
        }
        SharedPreferences sharedPreferences = MainApplication.getSharedPreferences("mmm");
        // We are only one process so it's ok to do this
        SharedPreferences bootPrefs = MainApplication.getSharedPreferences("mmm_boot");
        long lastBoot = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        long lastBootPrefs = bootPrefs.getLong("last_boot", 0);
        if (lastBootPrefs == 0 || Math.abs(lastBoot - lastBootPrefs) > 100) {
            boolean firstBoot = sharedPreferences.getBoolean("first_boot", true);
            bootPrefs.edit().clear().putLong("last_boot", lastBoot).putBoolean("first_boot", firstBoot).apply();
            if (firstBoot) {
                sharedPreferences.edit().putBoolean("first_boot", false).apply();
            }
            MainApplication.firstBoot = firstBoot;
        } else {
            MainApplication.firstBoot = bootPrefs.getBoolean("first_boot", false);
        }
        // Force initialize language early.
        new LanguageSwitcher(this);
        this.updateTheme();
        // Update emoji config
        FontRequestEmojiCompatConfig fontRequestEmojiCompatConfig = DefaultEmojiCompatConfig.create(this);
        if (fontRequestEmojiCompatConfig != null) {
            fontRequestEmojiCompatConfig.setReplaceAll(true);
            fontRequestEmojiCompatConfig.setMetadataLoadStrategy(EmojiCompat.LOAD_STRATEGY_MANUAL);
            EmojiCompat emojiCompat = EmojiCompat.init(fontRequestEmojiCompatConfig);
            new Thread(() -> {
                Timber.i("Loading emoji compat...");
                emojiCompat.load();
                Timber.i("Emoji compat loaded!");
            }, "Emoji compat init.").start();
        }
        SentryMain.initialize(this);
        if (Objects.equals(BuildConfig.ANDROIDACY_CLIENT_ID, "")) {
            Timber.w("Androidacy client id is empty! Please set it in androidacy.properties. Will not enable Androidacy.");
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("pref_androidacy_repo_enabled", false);
            editor.apply();
        }
    }

    @SuppressWarnings("unused")
    private Intent getIntent() {
        return this.getPackageManager().getLaunchIntentForPackage(this.getPackageName());
    }

    @Override
    public void onCreateFoxActivity(FoxActivity compatActivity) {
        super.onCreateFoxActivity(compatActivity);
        compatActivity.setTheme(this.managerThemeResId);
    }

    @Override
    public void onRefreshUI(FoxActivity compatActivity) {
        super.onRefreshUI(compatActivity);
        compatActivity.setThemeRecreate(this.managerThemeResId);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        Locale newTimeFormatLocale = newConfig.getLocales().get(0);
        if (timeFormatLocale != newTimeFormatLocale) {
            timeFormatLocale = newTimeFormatLocale;
            timeFormat = new SimpleDateFormat(timeFormatString, timeFormatLocale);
        }
        super.onConfigurationChanged(newConfig);
    }

    // getDataDir wrapper with optional path parameter
    public File getDataDirWithPath(String path) {
        File dataDir = this.getDataDir();
        // for path with / somewhere in the middle, its a subdirectory
        if (path != null) {
            if (path.startsWith("/"))
                path = path.substring(1);
            if (path.endsWith("/"))
                path = path.substring(0, path.length() - 1);
            if (path.contains("/")) {
                String[] dirs = path.split("/");
                for (String dir : dirs) {
                    dataDir = new File(dataDir, dir);
                    // make sure the directory exists
                    if (!dataDir.exists()) {
                        if (!dataDir.mkdirs()) {
                            if (BuildConfig.DEBUG)
                                Timber.w("Failed to create directory %s", dataDir);
                        }
                    }
                }
            } else {
                dataDir = new File(dataDir, path);
                // create the directory if it doesn't exist
                if (!dataDir.exists()) {
                    if (!dataDir.mkdirs()) {
                        if (BuildConfig.DEBUG)
                            Timber.w("Failed to create directory %s", dataDir);
                    }
                }
            }
            return dataDir;
        } else {
            throw new IllegalArgumentException("Path cannot be null");
        }
    }

    @SuppressLint("RestrictedApi")
    // view is nullable because it's called from xml
    public void resetApp() {
        // cant show a dialog because android is throwing a fit so here's hoping anybody who calls this method is otherwise confirming that the user wants to reset the app
        Timber.w("Resetting app...");
        // recursively delete the app's data
        ((ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE)).clearApplicationUserData();
    }

    public boolean isInForeground() {
        // determine if the app is in the foreground
        ActivityManager activityManager = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null) {
            Timber.d("appProcesses is null");
            return false;
        }
        final String packageName = this.getPackageName();
        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            Timber.d("Process: %s, Importance: %d", appProcess.processName, appProcess.importance);
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    // returns if background execution is restricted
    @SuppressWarnings("unused")
    public boolean isBackgroundRestricted() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return am.isBackgroundRestricted();
        } else {
            return false;
        }
    }

    private static class ReleaseTree extends Timber.Tree {
        @Override
        protected void log(int priority, String tag, @NonNull String message, Throwable t) {
            // basically silently drop all logs below error, and write the rest to logcat
            if (priority >= Log.ERROR) {
                if (t != null) {
                    Log.println(priority, tag, message);
                    t.printStackTrace();
                } else {
                    Log.println(priority, tag, message);
                }
            }
        }
    }
}
