package com.fox2code.mmm;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.StyleRes;
import androidx.core.app.NotificationManagerCompat;
import androidx.emoji2.text.DefaultEmojiCompatConfig;
import androidx.emoji2.text.EmojiCompat;
import androidx.emoji2.text.FontRequestEmojiCompatConfig;

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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

import io.noties.markwon.Markwon;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.ImagesPlugin;
import io.noties.markwon.image.network.OkHttpNetworkSchemeHandler;
import io.noties.markwon.syntax.Prism4jTheme;
import io.noties.markwon.syntax.Prism4jThemeDarkula;
import io.noties.markwon.syntax.Prism4jThemeDefault;
import io.noties.markwon.syntax.SyntaxHighlightPlugin;
import io.noties.prism4j.Prism4j;
import io.noties.prism4j.annotations.PrismBundle;

@PrismBundle(includeAll = true, grammarLocatorClassName = ".Prism4jGrammarLocator")
public class MainApplication extends FoxApplication implements androidx.work.Configuration.Provider {
    private static final String timeFormatString = "dd MMM yyyy"; // Example: 13 july 2001
    private static final Shell.Builder shellBuilder;
    private static final long secret;
    @SuppressLint("RestrictedApi")
    // Use FoxProcess wrapper helper.
    private static final boolean wrapped = !FoxProcessExt.isRootLoader();
    public static boolean isOfficial = false;
    private static Locale timeFormatLocale = Resources.getSystem().getConfiguration().locale;
    private static SimpleDateFormat timeFormat = new SimpleDateFormat(timeFormatString, timeFormatLocale);
    private static SharedPreferences bootSharedPreferences;
    private static String relPackageName = BuildConfig.APPLICATION_ID;
    @SuppressLint("StaticFieldLeak")
    private static MainApplication INSTANCE;
    private static boolean firstBoot;

    static {
        Shell.setDefaultBuilder(shellBuilder = Shell.Builder.create().setFlags(Shell.FLAG_REDIRECT_STDERR).setTimeout(10).setInitializers(InstallerInitializer.class));
        secret = new Random().nextLong();
    }

    @StyleRes
    private int managerThemeResId = R.style.Theme_MagiskModuleManager;
    private FoxThemeWrapper markwonThemeContext;
    private Markwon markwon;

    // Warning! Locales that are't exist will crash the app
    // Anything that is commented out is supported but the translation is not complete to at least 60%
    public static HashSet<String> supportedLocales = new HashSet<>();

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

    // Is application wrapped, and therefore must reduce it's feature set.
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isWrapped() {
        return wrapped;
    }

    public static boolean checkSecret(Intent intent) {
        return intent != null && intent.getLongExtra("secret", ~secret) == secret;
    }

    public static SharedPreferences getSharedPreferences() {
        return INSTANCE.getSharedPreferences("mmm", MODE_PRIVATE);
    }

    public static boolean isShowcaseMode() {
        return getSharedPreferences().getBoolean("pref_showcase_mode", false);
    }

    public static boolean shouldPreventReboot() {
        return getSharedPreferences().getBoolean("pref_prevent_reboot", true);
    }

    public static boolean isShowIncompatibleModules() {
        return getSharedPreferences().getBoolean("pref_show_incompatible", false);
    }

    public static boolean isForceDarkTerminal() {
        return getSharedPreferences().getBoolean("pref_force_dark_terminal", false);
    }

    public static boolean isTextWrapEnabled() {
        return getSharedPreferences().getBoolean("pref_wrap_text", false);
    }

    public static boolean isDohEnabled() {
        return getSharedPreferences().getBoolean("pref_dns_over_https", true);
    }

    public static boolean isMonetEnabled() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && getSharedPreferences().getBoolean("pref_enable_monet", true);
    }

    public static boolean isBlurEnabled() {
        return getSharedPreferences().getBoolean("pref_enable_blur", false);
    }

    public static boolean isDeveloper() {
        if (BuildConfig.DEBUG)
            return true;
        return getSharedPreferences().getBoolean("developer", false);
    }

    public static boolean isDisableLowQualityModuleFilter() {
        return getSharedPreferences().getBoolean("pref_disable_low_quality_module_filter", false) && isDeveloper();
    }

    public static boolean isUsingMagiskCommand() {
        return InstallerInitializer.peekMagiskVersion() >= Constants.MAGISK_VER_CODE_INSTALL_COMMAND && getSharedPreferences().getBoolean("pref_use_magisk_install_command", false) && isDeveloper();
    }

    public static boolean isBackgroundUpdateCheckEnabled() {
        return !wrapped && getSharedPreferences().getBoolean("pref_background_update_check", true);
    }

    public static boolean isAndroidacyTestMode() {
        return isDeveloper() && getSharedPreferences().getBoolean("pref_androidacy_test_mode", false);
    }

    public static boolean isFirstBoot() {
        return firstBoot;
    }

    public static void setHasGottenRootAccess(boolean bool) {
        getSharedPreferences().edit().putBoolean("has_root_access", bool).apply();
    }

    public static boolean isCrashReportingEnabled() {
        return SentryMain.IS_SENTRY_INSTALLED && getSharedPreferences().getBoolean("pref_crash_reporting", BuildConfig.DEFAULT_ENABLE_CRASH_REPORTING);
    }

    public static SharedPreferences getBootSharedPreferences() {
        return bootSharedPreferences;
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
        Markwon markwon = Markwon.builder(contextThemeWrapper).usePlugin(HtmlPlugin.create()).usePlugin(SyntaxHighlightPlugin.create(new Prism4j(new Prism4jGrammarLocator()), new Prism4jSwitchTheme())).usePlugin(ImagesPlugin.create().addSchemeHandler(OkHttpNetworkSchemeHandler.create(Http.getHttpClientWithCache()))).build();
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
        switch (theme = getSharedPreferences().getString("pref_theme", "system")) {
            default:
                Log.w("MainApplication", "Unknown theme id: " + theme);
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
                    Log.w("MainApplication", "Monet is not supported for transparent theme");
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
        switch (this.managerThemeResId) {
            case R.style.Theme_MagiskModuleManager:
            case R.style.Theme_MagiskModuleManager_Monet:
                return (this.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
            case R.style.Theme_MagiskModuleManager_Monet_Light:
            case R.style.Theme_MagiskModuleManager_Light:
                return true;
            case R.style.Theme_MagiskModuleManager_Monet_Dark:
            case R.style.Theme_MagiskModuleManager_Dark:
                return false;
            default:
                return super.isLightTheme();
        }
    }

    @SuppressLint("NonConstantResourceId")
    public boolean isDarkTheme() {
        return !this.isLightTheme();
    }

    @Override
    public void onCreate() {
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
        super.onCreate();
        if (BuildConfig.DEBUG) {
            Log.d("MainApplication", "Starting FoxMMM version " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + "), commit " + BuildConfig.COMMIT_HASH);
        }
        // Determine if this is an official build based on the signature
        try {
            // Get the signature of the key used to sign the app
            @SuppressLint("PackageManagerGetSignatures") Signature[] signatures = this.getPackageManager().getPackageInfo(this.getPackageName(), PackageManager.GET_SIGNATURES).signatures;
            String officialSignatureHash = "7bec7c4462f4aac616612d9f56a023ee3046e83afa956463b5fab547fd0a0be6";
            String ourSignatureHash = Hashing.sha256().hashBytes(signatures[0].toByteArray()).toString();
            isOfficial = ourSignatureHash.equals(officialSignatureHash);
        } catch (
                PackageManager.NameNotFoundException ignored) {
        }
        if (!isOfficial) {
            Log.w("MainApplication", "This is not an official build of FoxMMM. This warning can be safely ignored if this is expected, otherwise you may be running an untrusted build.");
            // Show a toast to warn the user
            Toast.makeText(this, R.string.not_official_build, Toast.LENGTH_LONG).show();
        }
        SharedPreferences sharedPreferences = MainApplication.getSharedPreferences();
        // We are only one process so it's ok to do this
        SharedPreferences bootPrefs = MainApplication.bootSharedPreferences = this.getSharedPreferences("mmm_boot", MODE_PRIVATE);
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
        // Update SSL Ciphers if update is possible
        GMSProviderInstaller.installIfNeeded(this);
        // Update emoji config
        FontRequestEmojiCompatConfig fontRequestEmojiCompatConfig = DefaultEmojiCompatConfig.create(this);
        if (fontRequestEmojiCompatConfig != null) {
            fontRequestEmojiCompatConfig.setReplaceAll(true);
            fontRequestEmojiCompatConfig.setMetadataLoadStrategy(EmojiCompat.LOAD_STRATEGY_MANUAL);
            EmojiCompat emojiCompat = EmojiCompat.init(fontRequestEmojiCompatConfig);
            new Thread(() -> {
                Log.i("MainApplication", "Loading emoji compat...");
                emojiCompat.load();
                Log.i("MainApplication", "Emoji compat loaded!");
            }, "Emoji compat init.").start();
        }
        SentryMain.initialize(this);
        if (Objects.equals(BuildConfig.ANDROIDACY_CLIENT_ID, "")) {
            Log.w("MainApplication", "Androidacy client id is empty! Please set it in androidacy.properties. Will not enable Androidacy.");
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
        Locale newTimeFormatLocale = newConfig.locale;
        if (timeFormatLocale != newTimeFormatLocale) {
            timeFormatLocale = newTimeFormatLocale;
            timeFormat = new SimpleDateFormat(timeFormatString, timeFormatLocale);
        }
        super.onConfigurationChanged(newConfig);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void clearAppData() {
        // Clear app data
        try {
            // Clearing app data
            // We have to manually delete the files and directories
            // because the cache directory is not cleared by the following method
            File cacheDir = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                cacheDir = this.getDataDir();
            }
            if (cacheDir != null && cacheDir.isDirectory()) {
                String[] children = cacheDir.list();
                if (children != null) {
                    for (String s : children) {
                        if (BuildConfig.DEBUG)
                            Log.w("MainApplication", "Deleting " + s);
                        if (!s.equals("lib")) {
                            if (!new File(cacheDir, s).delete()) {
                                if (BuildConfig.DEBUG)
                                    Log.w("MainApplication", "Failed to delete " + s);
                            }
                        }
                    }
                }
            }
            if (BuildConfig.DEBUG)
                Log.w("MainApplication", "Deleting cache dir");
            this.deleteSharedPreferences("mmm_boot");
            this.deleteSharedPreferences("mmm");
            this.deleteSharedPreferences("sentry");
            this.deleteSharedPreferences("androidacy");
            if (BuildConfig.DEBUG)
                Log.w("MainApplication", "Deleting shared prefs");
            this.getPackageManager().clearPackagePreferredActivities(this.getPackageName());
            if (BuildConfig.DEBUG)
                Log.w("MainApplication", "Done clearing app data");
        } catch (
                Exception e) {
            Log.e("MainApplication", "Failed to clear app data", e);
        }
    }

    private class Prism4jSwitchTheme implements Prism4jTheme {
        private final Prism4jTheme light = new Prism4jThemeDefault(Color.TRANSPARENT);
        private final Prism4jTheme dark = new Prism4jThemeDarkula(Color.TRANSPARENT);
        // Black theme
        private final Prism4jTheme black = new Prism4jThemeDefault(Color.BLACK);

        private Prism4jTheme getTheme() {
            // isLightTheme() means light, isDarkTheme() means dark, and isBlackTheme() means black
            return isLightTheme() ? light : isDarkTheme() ? dark : black;
        }

        @Override
        public int background() {
            return this.getTheme().background();
        }

        @Override
        public int textColor() {
            return this.getTheme().textColor();
        }

        @Override
        public void apply(@NonNull String language, @NonNull Prism4j.Syntax syntax, @NonNull SpannableStringBuilder builder, int start, int end) {
            this.getTheme().apply(language, syntax, builder, start, end);
        }
    }
}
