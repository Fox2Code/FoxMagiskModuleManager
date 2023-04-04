package com.fox2code.mmm.utils.sentry;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import com.fox2code.mmm.CrashHandler;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.androidacy.AndroidacyUtil;

import java.util.Objects;

import io.sentry.Sentry;
import io.sentry.android.core.SentryAndroid;
import io.sentry.android.fragment.FragmentLifecycleIntegration;
import io.sentry.android.timber.SentryTimberIntegration;
import timber.log.Timber;

public class SentryMain {
    public static final boolean IS_SENTRY_INSTALLED = true;
    private static boolean sentryEnabled = false;

    /**
     * Initialize Sentry
     * Sentry is used for crash reporting and performance monitoring.
     */
    @SuppressLint({"RestrictedApi", "UnspecifiedImmutableFlag"})
    public static void initialize(final MainApplication mainApplication) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            SharedPreferences.Editor editor = MainApplication.getINSTANCE().getSharedPreferences("sentry", Context.MODE_PRIVATE).edit();
            editor.putString("lastExitReason", "crash");
            editor.putLong("lastExitTime", System.currentTimeMillis());
            editor.apply();
            Timber.e(throwable, "Uncaught exception");
            // open crash handler and exit
            Intent intent = new Intent(mainApplication, CrashHandler.class);
            // pass the entire exception to the crash handler
            intent.putExtra("exception", throwable);
            // add stacktrace as string
            intent.putExtra("stacktrace", throwable.getStackTrace());
            // put lastEventId in intent (get from preferences)
            intent.putExtra("lastEventId", String.valueOf(Sentry.getLastEventId()));
            intent.putExtra("crashReportingEnabled", isSentryEnabled());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            Timber.e("Starting crash handler");
            mainApplication.startActivity(intent);
            Timber.e("Exiting");
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        });
        // If first_launch pref is not false, refuse to initialize Sentry
        SharedPreferences sharedPreferences = MainApplication.getPreferences("sentry");
        if (!Objects.equals(MainApplication.getPreferences("mmm").getString("last_shown_setup", null), "v1")) {
            return;
        }
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
                // Intercept okhttp requests to add sentry headers
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
                // Add a callback that will be used before the event is sent to Sentry.
                // With this callback, you can modify the event or, when returning null, also discard the event.
                options.setBeforeSend((event, hint) -> {
                    // in the rare event that crash reporting has been disabled since we started the app, we don't want to send the crash report
                    if (!MainApplication.isCrashReportingEnabled()) {
                        return null;
                    }
                    // Save lastEventId to private shared preferences
                    SharedPreferences sentryPrefs = MainApplication.getPreferences("sentry");
                    String lastEventId = Objects.requireNonNull(event.getEventId()).toString();
                    SharedPreferences.Editor editor = sentryPrefs.edit();
                    editor.putString("lastEventId", lastEventId);
                    editor.apply();
                    return event;
                });
                // Filter breadcrumb content from crash report.
                options.setBeforeBreadcrumb((breadcrumb, hint) -> {
                    String url = (String) breadcrumb.getData("url");
                    if (url == null || url.isEmpty())
                        return breadcrumb;
                    if ("cloudflare-dns.com".equals(Uri.parse(url).getHost()))
                        return null;
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
