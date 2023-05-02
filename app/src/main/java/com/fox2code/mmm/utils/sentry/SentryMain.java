package com.fox2code.mmm.utils.sentry;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import com.fox2code.mmm.CrashHandler;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.androidacy.AndroidacyUtil;

import org.matomo.sdk.extra.TrackHelper;

import java.util.Objects;

import io.sentry.Sentry;
import io.sentry.android.core.SentryAndroid;
import io.sentry.android.fragment.FragmentLifecycleIntegration;
import io.sentry.android.timber.SentryTimberIntegration;
import timber.log.Timber;

public class SentryMain {
    public static final boolean IS_SENTRY_INSTALLED = true;
    public static boolean isCrashing = false;
    private static boolean sentryEnabled = false;

    /**
     * Initialize Sentry
     * Sentry is used for crash reporting and performance monitoring.
     */
    @SuppressLint({"RestrictedApi", "UnspecifiedImmutableFlag"})
    public static void initialize(final MainApplication mainApplication) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            isCrashing = true;
            TrackHelper.track().exception(throwable).with(MainApplication.getINSTANCE().getTracker());
            SharedPreferences.Editor editor = MainApplication.getINSTANCE().getSharedPreferences("sentry", Context.MODE_PRIVATE).edit();
            editor.putString("lastExitReason", "crash");
            editor.putLong("lastExitTime", System.currentTimeMillis());
            editor.putString("lastExitReason", "crash");
            editor.putString("lastExitId", String.valueOf(Sentry.getLastEventId()));
            editor.apply();
            Timber.e("Uncaught exception with sentry ID %s and stacktrace %s", Sentry.getLastEventId(), throwable.getStackTrace());
            // open crash handler and exit
            Intent intent = new Intent(mainApplication, CrashHandler.class);
            // pass the entire exception to the crash handler
            intent.putExtra("exception", throwable);
            // add stacktrace as string
            intent.putExtra("stacktrace", throwable.getStackTrace());
            // put lastEventId in intent (get from preferences)
            intent.putExtra("lastEventId", String.valueOf(Sentry.getLastEventId()));
            // serialize Sentry.captureException and pass it to the crash handler
            intent.putExtra("sentryException", throwable);
            // pass crashReportingEnabled to crash handler
            intent.putExtra("crashReportingEnabled", isSentryEnabled());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            Timber.e("Starting crash handler");
            mainApplication.startActivity(intent);
            Timber.e("Exiting");
            android.os.Process.killProcess(android.os.Process.myPid());
        });
        // If first_launch pref is not false, refuse to initialize Sentry
        SharedPreferences sharedPreferences = MainApplication.getSharedPreferences("mmm");
        if (!Objects.equals(sharedPreferences.getString("last_shown_setup", null), "v2")) {
            return;
        }
        sentryEnabled = sharedPreferences.getBoolean("pref_crash_reporting_enabled", false);
        // set sentryEnabled on preference change of pref_crash_reporting_enabled
        sharedPreferences.registerOnSharedPreferenceChangeListener((sharedPreferences1, s) -> {
            if (s.equals("pref_crash_reporting_enabled")) {
                sentryEnabled = sharedPreferences1.getBoolean(s, false);
            }
        });
        SentryAndroid.init(mainApplication, options -> {
            // If crash reporting is disabled, stop here.
            if (!MainApplication.isCrashReportingEnabled()) {
                sentryEnabled = false; // Set sentry state to disabled
                options.setDsn("");
            } else {
                // get pref_crash_reporting_pii pref
                boolean crashReportingPii = sharedPreferences.getBoolean("crashReportingPii", false);
                sentryEnabled = true; // Set sentry state to enabled
                options.addIntegration(new FragmentLifecycleIntegration(mainApplication, true, true));
                // Enable automatic activity lifecycle breadcrumbs
                options.setEnableActivityLifecycleBreadcrumbs(true);
                // Enable automatic fragment lifecycle breadcrumbs
                options.addIntegration(new SentryTimberIntegration());
                options.setCollectAdditionalContext(true);
                options.setAttachThreads(true);
                options.setAttachStacktrace(true);
                options.setEnableNdk(true);
                options.addInAppInclude("com.fox2code.mmm");
                options.addInAppInclude("com.fox2code.mmm.debug");
                options.addInAppInclude("com.fox2code.mmm.fdroid");
                options.addInAppExclude("com.fox2code.mmm.utils.sentry.SentryMain");
                // Respect user preference for sending PII. default is true on non fdroid builds, false on fdroid builds
                options.setSendDefaultPii(crashReportingPii);
                options.enableAllAutoBreadcrumbs(true);
                // in-app screenshots are only sent if the app crashes, and it only shows the last activity. so no, we won't see your, ahem, "private" stuff
                options.setAttachScreenshot(true);
                // It just tell if sentry should ping the sentry dsn to tell the app is running. Useful for performance and profiling.
                options.setEnableAutoSessionTracking(true);
                // disable crash tracking - we handle that ourselves
                options.setEnableUncaughtExceptionHandler(false);
                // Add a callback that will be used before the event is sent to Sentry.
                // With this callback, you can modify the event or, when returning null, also discard the event.
                options.setBeforeSend((event, hint) -> {
                    // in the rare event that crash reporting has been disabled since we started the app, we don't want to send the crash report
                    if (!sentryEnabled) {
                        return null;
                    }
                    if (isCrashing) {
                        return null;
                    }
                    return event;
                });
                // Filter breadcrumb content from crash report.
                options.setBeforeBreadcrumb((breadcrumb, hint) -> {
                    String url = (String) breadcrumb.getData("url");
                    if (url == null || url.isEmpty()) return breadcrumb;
                    if ("cloudflare-dns.com".equals(Uri.parse(url).getHost())) return null;
                    if (AndroidacyUtil.isAndroidacyLink(url)) {
                        breadcrumb.setData("url", AndroidacyUtil.hideToken(url));
                    }
                    return breadcrumb;
                });
            }
        });
    }

    public static void addSentryBreadcrumb(SentryBreadcrumb sentryBreadcrumb) {
        if (MainApplication.isCrashReportingEnabled()) {
            Sentry.addBreadcrumb(sentryBreadcrumb.breadcrumb);
        }
    }

    @SuppressWarnings("unused")
    public static boolean isSentryEnabled() {
        return sentryEnabled;
    }
}
