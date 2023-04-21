package com.fox2code.mmm;

import android.annotation.SuppressLint;
import android.content.ClipboardManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.Toast;

import com.fox2code.foxcompat.app.FoxActivity;
import com.fox2code.foxcompat.app.FoxApplication;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;

import java.io.StringWriter;

import io.sentry.Sentry;
import io.sentry.UserFeedback;
import timber.log.Timber;

public class CrashHandler extends FoxActivity {

    @SuppressLint("RestrictedApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Timber.i("CrashHandler.onCreate(%s)", savedInstanceState);
        // log intent with extras
        Timber.d("CrashHandler.onCreate: intent=%s", getIntent());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash_handler);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                // per process webview data dir
                WebView.setDataDirectorySuffix(FoxApplication.getProcessName());
            } catch (Exception e) {
                Timber.e(e, "CrashHandler.onCreate: Failed to set webview data directory suffix");
            }
        }
        // set crash_details MaterialTextView to the exception passed in the intent or unknown if null
        // convert stacktrace from array to string, and pretty print it (first line is the exception, the rest is the stacktrace, with each line indented by 4 spaces)
        MaterialTextView crashDetails = findViewById(R.id.crash_details);
        crashDetails.setText("");
        // get the exception from the intent
        Throwable exception = (Throwable) getIntent().getSerializableExtra("exception");
        // get the crashReportingEnabled from the intent
        boolean crashReportingEnabled = getIntent().getBooleanExtra("crashReportingEnabled", false);
        // if the exception is null, set the crash details to "Unknown"
        if (exception == null) {
            crashDetails.setText(R.string.crash_details);
        } else {
            // if the exception is not null, set the crash details to the exception and stacktrace
            // stacktrace is an StacktraceElement, so convert it to a string and replace the commas with newlines
            StringWriter stringWriter = new StringWriter();
            exception.printStackTrace(new java.io.PrintWriter(stringWriter));
            String stacktrace = stringWriter.toString();
            stacktrace = stacktrace.replace(",", "\n     ");
            crashDetails.setText(getString(R.string.crash_full_stacktrace, stacktrace));
        }
        String lastEventId = getIntent().getStringExtra("lastEventId");
        Timber.d("CrashHandler.onCreate: lastEventId=%s, crashReportingEnabled=%s", lastEventId, crashReportingEnabled);
        if (lastEventId == null && crashReportingEnabled) {
            // if lastEventId is null, hide the feedback button
            findViewById(R.id.feedback).setVisibility(View.GONE);
            Timber.d("CrashHandler.onCreate: lastEventId is null but crash reporting is enabled. This may indicate a bug in the crash reporting system.");
        } else {
            // if lastEventId is not null, show the feedback button
            findViewById(R.id.feedback).setVisibility(View.VISIBLE);
        }
        // disable feedback if sentry is disabled
        //noinspection ConstantConditions
        if (crashReportingEnabled && lastEventId != null) {
            // get name, email, and message fields
            EditText name = findViewById(R.id.feedback_name);
            EditText email = findViewById(R.id.feedback_email);
            EditText description = findViewById(R.id.feedback_message);
            // get submit button
            findViewById(R.id.feedback_submit).setOnClickListener(v -> {
                // require the feedback_message, rest is optional
                if (description.getText().toString().equals("")) {
                    Toast.makeText(this, R.string.sentry_dialogue_empty_message, Toast.LENGTH_LONG).show();
                    return;
                }
                // if email or name is empty, use "Anonymous"
                final String[] nameString = {name.getText().toString().equals("") ? "Anonymous" : name.getText().toString()};
                final String[] emailString = {email.getText().toString().equals("") ? "Anonymous" : email.getText().toString()};
                // get sentryException passed in intent
                Throwable sentryException = (Throwable) getIntent().getSerializableExtra("sentryException");
                new Thread(() -> {
                    try {
                        UserFeedback userFeedback;
                        if (sentryException != null) {
                            userFeedback = new UserFeedback(Sentry.captureException(sentryException));
                            // Setups the JSON body
                            if (nameString[0].equals("")) nameString[0] = "Anonymous";
                            if (emailString[0].equals("")) emailString[0] = "Anonymous";
                            userFeedback.setName(nameString[0]);
                            userFeedback.setEmail(emailString[0]);
                            userFeedback.setComments(description.getText().toString());
                            Sentry.captureUserFeedback(userFeedback);
                        }
                        Timber.i("Submitted user feedback: name %s email %s comment %s", nameString[0], emailString[0], description.getText().toString());
                        runOnUiThread(() -> Toast.makeText(this, R.string.sentry_dialogue_success, Toast.LENGTH_LONG).show());
                        // Close the activity
                        finish();
                        // start the main activity
                        startActivity(getPackageManager().getLaunchIntentForPackage(getPackageName()));
                    } catch (Exception e) {
                        Timber.e(e, "Failed to submit user feedback");
                        // Show a toast if the user feedback could not be submitted
                        runOnUiThread(() -> Toast.makeText(this, R.string.sentry_dialogue_failed_toast, Toast.LENGTH_LONG).show());
                    }
                }).start();
            });
            // get restart button
            findViewById(R.id.restart).setOnClickListener(v -> {
                // Restart the app
                finish();
                startActivity(getPackageManager().getLaunchIntentForPackage(getPackageName()));
            });
        } else {
            // disable feedback if sentry is disabled
            findViewById(R.id.feedback_name).setEnabled(false);
            findViewById(R.id.feedback_email).setEnabled(false);
            findViewById(R.id.feedback_message).setEnabled(false);
            // fade out all the fields
            findViewById(R.id.feedback_name).setAlpha(0.5f);
            findViewById(R.id.feedback_email).setAlpha(0.5f);
            findViewById(R.id.feedback_message).setAlpha(0.5f);
            // fade out the submit button
            findViewById(R.id.feedback_submit).setAlpha(0.5f);
            // set feedback_text to "Crash reporting is disabled"
            ((MaterialTextView) findViewById(R.id.feedback_text)).setText(R.string.sentry_enable_nag);
            findViewById(R.id.feedback_submit).setOnClickListener(v -> Toast.makeText(this, R.string.sentry_dialogue_disabled, Toast.LENGTH_LONG).show());
            // handle restart button
            // we have to explicitly enable it because it's disabled by default
            findViewById(R.id.restart).setEnabled(true);
            findViewById(R.id.restart).setOnClickListener(v -> {
                // Restart the app
                finish();
                startActivity(getPackageManager().getLaunchIntentForPackage(getPackageName()));
            });
        }
        // handle reset button
        findViewById(R.id.reset).setOnClickListener(v -> {
            // show a confirmation material dialog
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
            builder.setTitle(R.string.reset_app);
            builder.setMessage(R.string.reset_app_confirmation);
            builder.setPositiveButton(R.string.reset, (dialog, which) -> {
                // reset the app
                MainApplication.getINSTANCE().resetApp();
            });
            builder.setNegativeButton(R.string.cancel, (dialog, which) -> {
                // do nothing
            });
            builder.show();
        });
    }

    public void copyCrashDetails(View view) {
        // change view to a checkmark
        view.setBackgroundResource(R.drawable.baseline_check_24);
        // copy crash_details to clipboard
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        String crashDetails = ((MaterialTextView) findViewById(R.id.crash_details)).getText().toString();
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("crash_details", crashDetails));
        // show a toast
        Toast.makeText(this, R.string.crash_details_copied, Toast.LENGTH_LONG).show();
        // after 1 second, change the view back to a copy button
        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            runOnUiThread(() -> view.setBackgroundResource(R.drawable.baseline_copy_all_24));
        }).start();
    }
}