package com.fox2code.mmm.utils.sentry;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.androidacy.AndroidacyUtil;

import java.util.Objects;

import io.sentry.Sentry;
import io.sentry.android.core.SentryAndroid;
import io.sentry.android.fragment.FragmentLifecycleIntegration;

public class SentryMain {
    public static final boolean IS_SENTRY_INSTALLED = true;
    private static final String TAG = "SentryMain";
    private static boolean sentryEnabled = false;

    /**
     * Initialize Sentry
     * Sentry is used for crash reporting and performance monitoring. The SDK is explcitly configured not to send PII, and server side scrubbing of sensitive data is enabled (which also removes IP addresses)
     */
    @SuppressLint({"RestrictedApi", "UnspecifiedImmutableFlag"})
    public static void initialize(final MainApplication mainApplication) {
        // If first_launch pref is not false, refuse to initialize Sentry
        SharedPreferences sharedPreferences = MainApplication.getSharedPreferences();
        if (sharedPreferences.getBoolean("first_time_user", true)) {
            return;
        }
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            SharedPreferences.Editor editor = mainApplication.getSharedPreferences("sentry", Context.MODE_PRIVATE).edit();
            editor.putString("lastExitReason", "crash");
            editor.putLong("lastExitTime", System.currentTimeMillis());
            editor.apply();
            // If we just let the default uncaught exception handler handle the
            // exception, the app will hang and never close.
            // So we need to kill the app ourselves.
            System.exit(1);
        });
        SentryAndroid.init(mainApplication, options -> {
            // If crash reporting is disabled, stop here.
            if (!MainApplication.isCrashReportingEnabled()) {
                options.setDsn("");
            } else {
                sentryEnabled = true; // Set sentry state to enabled
                options.addIntegration(new FragmentLifecycleIntegration(mainApplication, true, true));
                options.setCollectAdditionalContext(true);
                options.setAttachThreads(true);
                options.setAttachStacktrace(true);
                options.setEnableNdk(true);
                // Intercept okhttp requests to add sentry headers
                options.addInAppInclude("com.fox2code.mmm");
                options.addInAppInclude("com.fox2code.mmm.debug");
                options.addInAppInclude("com.fox2code.mmm.fdroid");
                options.addInAppExclude("com.fox2code.mmm.utils.sentry.SentryMain");
                // Sentry sends ABSOLUTELY NO Personally Identifiable Information (PII) by default.
                // Already set to false by default, just set it again to make peoples feel safer.
                options.setSendDefaultPii(false);
                // It just tell if sentry should ping the sentry dsn to tell the app is running. Useful for performance and profiling.
                options.setEnableAutoSessionTracking(true);
                // A screenshot of the app itself is only sent if the app crashes, and it only shows the last activity
                // Add a callback that will be used before the event is sent to Sentry.
                // With this callback, you can modify the event or, when returning null, also discard the event.
                options.setBeforeSend((event, hint) -> {
                    // Save lastEventId to private shared preferences
                    SharedPreferences sentryPrefs = MainApplication.getINSTANCE().getSharedPreferences("sentry", Context.MODE_PRIVATE);
                    String lastEventId = Objects.requireNonNull(event.getEventId()).toString();
                    SharedPreferences.Editor editor = sentryPrefs.edit();
                    editor.putString("lastEventId", lastEventId);
                    editor.apply();
                    return event;
                });
                // Filter breadrcrumb content from crash report.
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

    public static boolean isSentryEnabled() {
        return sentryEnabled;
    }
}
