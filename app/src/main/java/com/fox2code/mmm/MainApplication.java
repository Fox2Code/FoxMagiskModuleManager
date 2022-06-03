package com.fox2code.mmm;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.emoji2.text.DefaultEmojiCompatConfig;
import androidx.emoji2.text.EmojiCompat;
import androidx.emoji2.text.FontRequestEmojiCompatConfig;

import com.fox2code.mmm.compat.CompatActivity;
import com.fox2code.mmm.compat.CompatApplication;
import com.fox2code.mmm.compat.CompatThemeWrapper;
import com.fox2code.mmm.installer.InstallerInitializer;
import com.fox2code.mmm.utils.GMSProviderInstaller;
import com.fox2code.mmm.utils.Http;
import com.fox2code.rosettax.LanguageSwitcher;
import com.google.android.material.color.DynamicColors;
import com.topjohnwu.superuser.Shell;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
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

@PrismBundle(
        includeAll = true,
        grammarLocatorClassName = ".Prism4jGrammarLocator"
)
public class MainApplication extends CompatApplication {
    private static final String timeFormatString = "dd MMM yyyy"; // Example: 13 july 2001
    private static Locale timeFormatLocale =
            Resources.getSystem().getConfiguration().locale;
    private static SimpleDateFormat timeFormat =
            new SimpleDateFormat(timeFormatString, timeFormatLocale);
    private static final Shell.Builder shellBuilder;
    private static final long secret;
    private static SharedPreferences bootSharedPreferences;
    private static MainApplication INSTANCE;
    private static boolean firstBoot;

    static {
        Shell.setDefaultBuilder(shellBuilder = Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10).setInitializers(InstallerInitializer.class)
        );
        secret = new Random().nextLong();
    }

    public MainApplication() {
        if (INSTANCE != null)
            throw new IllegalStateException("Duplicate application instance!");
        INSTANCE = this;
    }

    public static Shell build(String... command) {
        return shellBuilder.build(command);
    }

    public static void addSecret(Intent intent) {
        intent.putExtra("secret", secret);
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

    public static boolean isForceEnglish() {
        return getSharedPreferences().getBoolean("pref_force_english", false);
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
        return getSharedPreferences().getBoolean("pref_enable_monet", false);
    }

    public static boolean isBlurEnabled() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                getSharedPreferences().getBoolean("pref_enable_blur", false);
    }

    public static boolean isDeveloper() {
        return BuildConfig.DEBUG ||
                getSharedPreferences().getBoolean("developer", false);
    }

    public static boolean isDisableLowQualityModuleFilter() {
        return getSharedPreferences().getBoolean("pref_disable_low_quality_module_filter",
                false) && isDeveloper();
    }

    public static boolean isUsingMagiskCommand() {
        return InstallerInitializer.peekMagiskVersion() >= Constants.MAGISK_VER_CODE_INSTALL_COMMAND
                && getSharedPreferences().getBoolean("pref_use_magisk_install_command", false)
                && isDeveloper();
    }

    public static boolean isFirstBoot() {
        return firstBoot;
    }

    public static void notifyBootListenerCompleted() {
        if (MainApplication.bootSharedPreferences != null) {
            MainApplication.bootSharedPreferences.edit()
                    .putBoolean("first_boot", false).apply();
        } else if (MainApplication.INSTANCE != null) {
            MainApplication.getSharedPreferences().edit()
                    .putBoolean("first_boot", false).apply();
        }
        firstBoot = false;
    }

    public static boolean hasGottenRootAccess() {
        return getSharedPreferences().getBoolean("has_root_access", false);
    }

    public static void setHasGottenRootAccess(boolean bool) {
        getSharedPreferences().edit().putBoolean("has_root_access", bool).apply();
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

    @StyleRes
    private int managerThemeResId = R.style.Theme_MagiskModuleManager;
    private CompatThemeWrapper markwonThemeContext;
    private Markwon markwon;

    public Markwon getMarkwon() {
        if (this.markwon != null)
            return this.markwon;
        CompatThemeWrapper contextThemeWrapper = this.markwonThemeContext;
        if (contextThemeWrapper == null) {
            contextThemeWrapper = this.markwonThemeContext =
                    new CompatThemeWrapper(this, this.managerThemeResId);
            contextThemeWrapper.setForceEnglish(isForceEnglish());
        }
        Markwon markwon = Markwon.builder(contextThemeWrapper).usePlugin(HtmlPlugin.create())
                .usePlugin(SyntaxHighlightPlugin.create(
                        new Prism4j(new Prism4jGrammarLocator()), new Prism4jSwitchTheme()))
                .usePlugin(ImagesPlugin.create().addSchemeHandler(
                        OkHttpNetworkSchemeHandler.create(Http.getHttpClientWithCache()))).build();
        return this.markwon = markwon;
    }

    public CompatThemeWrapper getMarkwonThemeContext() {
        return markwonThemeContext;
    }

    private class Prism4jSwitchTheme implements Prism4jTheme {
        private final Prism4jTheme light = new Prism4jThemeDefault(Color.TRANSPARENT);
        private final Prism4jTheme dark = new Prism4jThemeDarkula(Color.TRANSPARENT);

        private Prism4jTheme getTheme() {
            return isLightTheme() ? this.light : this.dark;
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
        public void apply(@NonNull String language, @NonNull Prism4j.Syntax syntax,
                          @NonNull SpannableStringBuilder builder, int start, int end) {
            this.getTheme().apply(language, syntax, builder, start, end);
        }
    }

    @SuppressLint("NonConstantResourceId")
    public void setManagerThemeResId(@StyleRes int resId) {
        this.managerThemeResId = resId;
        if (this.markwonThemeContext != null) {
            this.markwonThemeContext.setTheme(resId);
        }
        this.markwon = null;
    }

    public void updateTheme() {
        @StyleRes int themeResId;
        String theme;
        switch (theme = getSharedPreferences().getString("pref_theme", "system")) {
            default:
                Log.w("MainApplication", "Unknown theme id: " + theme);
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
        this.setManagerThemeResId(themeResId);
    }

    @StyleRes
    public int getManagerThemeResId() {
        return managerThemeResId;
    }

    @SuppressLint("NonConstantResourceId")
    public boolean isLightTheme() {
        switch (this.managerThemeResId) {
            case R.style.Theme_MagiskModuleManager:
                return (this.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK)
                        != Configuration.UI_MODE_NIGHT_YES;
            case R.style.Theme_MagiskModuleManager_Light:
                return true;
            case R.style.Theme_MagiskModuleManager_Dark:
                return false;
            default:
                throw new IllegalStateException("Non manager theme!");
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (MainApplication.isMonetEnabled()) {
                DynamicColors.applyToActivitiesIfAvailable(this);
            }
        }
        SharedPreferences sharedPreferences = MainApplication.getSharedPreferences();
        // We are only one process so it's ok to do this
        SharedPreferences bootPrefs = MainApplication.bootSharedPreferences =
                this.getSharedPreferences("mmm_boot", MODE_PRIVATE);
        long lastBoot = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        long lastBootPrefs = bootPrefs.getLong("last_boot", 0);
        if (lastBootPrefs == 0 || Math.abs(lastBoot - lastBootPrefs) > 100) {
            boolean firstBoot = sharedPreferences.getBoolean("first_boot", true);
            bootPrefs.edit().clear().putLong("last_boot", lastBoot)
                    .putBoolean("first_boot", firstBoot).apply();
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
        FontRequestEmojiCompatConfig fontRequestEmojiCompatConfig =
                DefaultEmojiCompatConfig.create(this);
        if (fontRequestEmojiCompatConfig != null) {
            fontRequestEmojiCompatConfig.setReplaceAll(true);
            fontRequestEmojiCompatConfig
                    .setMetadataLoadStrategy(EmojiCompat.LOAD_STRATEGY_MANUAL);
            EmojiCompat emojiCompat = EmojiCompat.init(fontRequestEmojiCompatConfig);
            new Thread(() -> {
                Log.d("MainApplication", "Loading emoji compat...");
                emojiCompat.load();
                Log.d("MainApplication", "Emoji compat loaded!");
            }, "Emoji compat init.").start();
        }
    }

    @Override
    public void onCreateCompatActivity(CompatActivity compatActivity) {
        this.setForceEnglish(isForceEnglish());
        super.onCreateCompatActivity(compatActivity);
        compatActivity.setTheme(this.managerThemeResId);
    }

    @Override
    public void onRefreshUI(CompatActivity compatActivity) {
        this.setForceEnglish(isForceEnglish());
        super.onRefreshUI(compatActivity);
        compatActivity.setThemeRecreate(this.managerThemeResId);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        Locale newTimeFormatLocale = newConfig.locale;
        if (timeFormatLocale != newTimeFormatLocale) {
            timeFormatLocale = newTimeFormatLocale;
            timeFormat = new SimpleDateFormat(
                    timeFormatString, timeFormatLocale);
        }
        super.onConfigurationChanged(newConfig);
    }
}
