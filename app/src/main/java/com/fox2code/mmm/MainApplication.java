package com.fox2code.mmm;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.appcompat.view.ContextThemeWrapper;

import com.fox2code.mmm.compat.CompatActivity;
import com.fox2code.mmm.installer.InstallerInitializer;
import com.fox2code.mmm.utils.GMSProviderInstaller;
import com.fox2code.mmm.utils.Http;
import com.topjohnwu.superuser.Shell;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

import io.noties.markwon.Markwon;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.ImagesPlugin;
import io.noties.markwon.image.network.OkHttpNetworkSchemeHandler;

public class MainApplication extends Application implements CompatActivity.ApplicationCallbacks {
    private static final String timeFormatString = "dd MMM yyyy"; // Example: 13 july 2001
    private static Locale timeFormatLocale =
            Resources.getSystem().getConfiguration().locale;
    private static SimpleDateFormat timeFormat =
            new SimpleDateFormat(timeFormatString, timeFormatLocale);
    private static final Shell.Builder shellBuilder;
    private static final int secret;
    private static SharedPreferences bootSharedPreferences;
    private static MainApplication INSTANCE;

    static {
        Shell.setDefaultBuilder(shellBuilder = Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10).setInitializers(InstallerInitializer.class)
        );
        secret = new Random().nextInt();
    }

    public static Shell build(String... command) {
        return shellBuilder.build(command);
    }

    public static void addSecret(Intent intent) {
        intent.putExtra("secret", secret);
    }

    public static boolean checkSecret(Intent intent) {
        return intent.getIntExtra("secret", ~secret) == secret;
    }

    public static SharedPreferences getSharedPreferences() {
        return INSTANCE.getSharedPreferences("mmm", MODE_PRIVATE);
    }

    public static boolean isShowcaseMode() {
        return getSharedPreferences().getBoolean("pref_showcase_mode", false);
    }

    public static boolean isShowIncompatibleModules() {
        return getSharedPreferences().getBoolean("pref_show_incompatible", false);
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
    private ContextThemeWrapper markwonThemeContext;
    private Markwon markwon;

    public Markwon getMarkwon() {
        if (this.markwon != null)
            return this.markwon;
        ContextThemeWrapper contextThemeWrapper = this.markwonThemeContext =
                new ContextThemeWrapper(this, this.managerThemeResId);
        Markwon markwon = Markwon.builder(contextThemeWrapper).usePlugin(HtmlPlugin.create())
                .usePlugin(ImagesPlugin.create().addSchemeHandler(
                        OkHttpNetworkSchemeHandler.create(Http.getHttpclientWithCache()))).build();
        return this.markwon = markwon;
    }

    public void setManagerThemeResId(@StyleRes int resId) {
        this.managerThemeResId = resId;
        if (this.markwonThemeContext != null)
            this.markwonThemeContext.setTheme(resId);
    }

    @StyleRes
    public int getManagerThemeResId() {
        return managerThemeResId;
    }

    @Override
    public void onCreate() {
        INSTANCE = this;
        super.onCreate();
        // We are only one process so it's ok to do this
        SharedPreferences bootPrefs = MainApplication.bootSharedPreferences =
                this.getSharedPreferences("mmm_boot", MODE_PRIVATE);
        long lastBoot = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        long lastBootPrefs = bootPrefs.getLong("last_boot", 0);
        if (lastBootPrefs == 0 || Math.abs(lastBoot - lastBootPrefs) > 100) {
            bootPrefs.edit().clear().putLong("last_boot", lastBoot).apply();
        }
        @StyleRes int themeResId;
        switch (getSharedPreferences().getString("pref_theme", "system")) {
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
        this.setManagerThemeResId(themeResId);
        // Update SSL Ciphers if update is possible
        GMSProviderInstaller.installIfNeeded(this);
    }

    @Override
    public void onCreateCompatActivity(CompatActivity compatActivity) {
        compatActivity.setTheme(this.managerThemeResId);
    }

    @Override
    public void onRefreshUI(CompatActivity compatActivity) {
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
