package com.fox2code.mmm.sentry;

import static io.sentry.TypeCheckHint.SENTRY_TYPE_CHECK_HINT;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.EditText;

import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.androidacy.AndroidacyUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.io.Writer;

import io.sentry.JsonObjectWriter;
import io.sentry.NoOpLogger;
import io.sentry.Sentry;
import io.sentry.UserFeedback;
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
    @SuppressLint("RestrictedApi")
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
                        // Get user feedback
                        SentryId sentryId = event.getEventId();
                        if (sentryId != null) {
                            UserFeedback userFeedback = new UserFeedback(sentryId);
                            // Get the current activity
                            Activity context = MainApplication.getINSTANCE().getLastCompatActivity();
                            // Create a material dialog
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (context != null) {
                                    // Show fields for name, email, and comment, and two buttons: "Submit" and "Cancel"
                                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
                                    builder.setTitle(R.string.sentry_dialogue_title);
                                    builder.setMessage(R.string.sentry_dialogue_message);
                                    // Add the text fields, set the text to the previously entered values
                                    EditText name = new EditText(context);
                                    name.setHint(R.string.name);
                                    builder.setView(name);
                                    EditText email = new EditText(context);
                                    email.setHint(R.string.email);
                                    builder.setView(email);
                                    EditText comment = new EditText(context);
                                    comment.setHint(R.string.additional_info);
                                    builder.setView(comment);
                                    // Add the buttons
                                    builder.setPositiveButton(R.string.submit, (dialog, id) -> {
                                        // User clicked "Submit"
                                        userFeedback.setName(name.getText().toString());
                                        userFeedback.setEmail(email.getText().toString());
                                        userFeedback.setComments(comment.getText().toString());
                                        // Send the feedback
                                        Sentry.captureUserFeedback(userFeedback);
                                    });
                                    builder.setNegativeButton(R.string.cancel, (dialog, id) -> {
                                        // User cancelled the dialog
                                    });
                                    // Create and show the AlertDialog
                                    builder.create().show();
                                }
                            });
                        }
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
            }
        });
    }

    public static void addSentryBreadcrumb(SentryBreadcrumb sentryBreadcrumb) {
        if (MainApplication.isCrashReportingEnabled()) {
            Sentry.addBreadcrumb(sentryBreadcrumb.breadcrumb);
        }
    }
}
