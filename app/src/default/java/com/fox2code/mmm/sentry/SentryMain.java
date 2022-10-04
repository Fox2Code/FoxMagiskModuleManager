package com.fox2code.mmm.sentry;

import android.util.Log;

import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.MainApplication;

import java.io.IOException;
import java.io.Writer;

import io.sentry.JsonObjectWriter;
import io.sentry.NoOpLogger;
import io.sentry.Sentry;
import io.sentry.TypeCheckHint;
import io.sentry.android.core.SentryAndroid;
import io.sentry.android.fragment.FragmentLifecycleIntegration;
import io.sentry.hints.DiskFlushNotification;

public class SentryMain {
    public static final boolean IS_SENTRY_INSTALLED = true;
    private static final String TAG = "SentryMain";

    public static void initialize(final MainApplication mainApplication) {
        SentryAndroid.init(mainApplication, options -> {
            // If crash reporting is disabled, stop here.
            if (!MainApplication.isCrashReportingEnabled()) {
                options.setDsn("");
            } else {
                options.addIntegration(new FragmentLifecycleIntegration(mainApplication, true, true));
                // Sentry sends ABSOLUTELY NO Personally Identifiable Information (PII) by default.
                // Already set to false by default, just set it again to make peoples feel safer.
                options.setSendDefaultPii(false);
                // It just tell if sentry should ping the sentry dsn to tell the app is running.
                // This is not needed at all for crash reporting purposes, so disable it.
                options.setEnableAutoSessionTracking(false);
                // A screenshot of the app itself is only sent if the app crashes, and it only shows the last activity
                // In addition, sentry is configured with a trusted third party other than sentry.io, and only trusted people have access to the sentry instance
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
                        return event;
                    } else {
                        // We need to do this to avoid crash delay on crash when the event is dropped
                        DiskFlushNotification diskFlushNotification = hint.getAs(
                                TypeCheckHint.SENTRY_TYPE_CHECK_HINT, DiskFlushNotification.class);
                        if (diskFlushNotification != null) diskFlushNotification.markFlushed();
                        return null;
                    }
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
