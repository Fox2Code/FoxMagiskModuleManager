package com.fox2code.mmm.sentry;

import static io.sentry.TypeCheckHint.SENTRY_TYPE_CHECK_HINT;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.androidacy.AndroidacyUtil;

import java.io.IOException;
import java.io.Writer;
import java.util.Objects;

import io.sentry.JsonObjectWriter;
import io.sentry.NoOpLogger;
import io.sentry.Sentry;
import io.sentry.android.core.SentryAndroid;
import io.sentry.android.fragment.FragmentLifecycleIntegration;
import io.sentry.hints.DiskFlushNotification;
import io.sentry.protocol.SentryId;

public class SentryMain {
    public static final boolean IS_SENTRY_INSTALLED = true;
    private static final String TAG = "SentryMain";

    /**
     * Initialize Sentry
     * Sentry is used for crash reporting and performance monitoring. The SDK is explcitly configured not to send PII, and server side scrubbing of sensitive data is enabled (which also removes IP addresses)
     */
    @SuppressLint({"RestrictedApi", "UnspecifiedImmutableFlag"})
    public static void initialize(final MainApplication mainApplication) {
        SentryAndroid.init(mainApplication, options -> {
            // If crash reporting is disabled, stop here.
            if (!MainApplication.isCrashReportingEnabled()) {
                options.setDsn("");
            } else {
                options.addIntegration(new FragmentLifecycleIntegration(mainApplication, true, true));
                options.setCollectAdditionalContext(true);
                options.setAttachThreads(true);
                options.setAttachStacktrace(true);
                options.setEnableNdk(true);
                // Intercept okhttp requests to add sentry headers
                options.addInAppInclude("com.fox2code.mmm");
                // Sentry sends ABSOLUTELY NO Personally Identifiable Information (PII) by default.
                // Already set to false by default, just set it again to make peoples feel safer.
                options.setSendDefaultPii(false);
                // It just tell if sentry should ping the sentry dsn to tell the app is running. Useful for performance and profiling.
                options.setEnableAutoSessionTracking(true);
                // A screenshot of the app itself is only sent if the app crashes, and it only shows the last activity
                // Add a callback that will be used before the event is sent to Sentry.
                // With this callback, you can modify the event or, when returning null, also discard the event.
                options.setBeforeSend((event, hint) -> {
                    if (BuildConfig.DEBUG) { // Debug sentry events for debug.
                        StringBuilder stringBuilder = new StringBuilder("Sentry report debug: ");
                        try {
                            event.serialize(new JsonObjectWriter(new Writer() {
                                @Override
                                public void write(char[] cbuf) {
                                    stringBuilder.append(cbuf);
                                }

                                @Override
                                public void write(String str) {
                                    stringBuilder.append(str);
                                }

                                @Override
                                public void write(char[] chars, int i, int i1) {
                                    stringBuilder.append(chars, i, i1);
                                }

                                @Override
                                public void write(String str, int off, int len) {
                                    stringBuilder.append(str, off, len);
                                }

                                @Override
                                public void flush() {
                                }

                                @Override
                                public void close() {
                                }
                            }, 4), NoOpLogger.getInstance());
                        } catch (IOException ignored) {
                        }
                        Log.i(TAG, stringBuilder.toString());
                    }
                    if (MainApplication.isCrashReportingEnabled()) {
                        // Save lastEventId to private shared preferences
                        SharedPreferences sharedPreferences = mainApplication.getSharedPreferences("sentry", Context.MODE_PRIVATE);
                        sharedPreferences.edit().putString("lastEventId",
                                Objects.requireNonNull(event.getEventId()).toString()).apply();
                        return event;
                    } else {
                        // We need to do this to avoid crash delay on crash when the event is dropped
                        DiskFlushNotification diskFlushNotification = hint.getAs(SENTRY_TYPE_CHECK_HINT, DiskFlushNotification.class);
                        if (diskFlushNotification != null) diskFlushNotification.markFlushed();
                        return null;
                    }
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
                // On uncaught exception, set the lastEventId in private sentry preferences
                Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                    SentryId lastEventId = Sentry.captureException(throwable);
                    SharedPreferences.Editor editor = mainApplication.getSharedPreferences(
                            "sentry", Context.MODE_PRIVATE).edit();
                    editor.putString("lastExitReason", "crash");
                    editor.apply();
                    // Kill the app
                    System.exit(2);
                });
            }
        });
    }

    public static void addSentryBreadcrumb(SentryBreadcrumb sentryBreadcrumb) {
        if (MainApplication.isCrashReportingEnabled()) {
            Sentry.addBreadcrumb(sentryBreadcrumb.breadcrumb);
        }
    }
}
