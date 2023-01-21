package com.fox2code.mmm;

import android.annotation.SuppressLint;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.fox2code.foxcompat.app.FoxActivity;
import com.google.android.material.textview.MaterialTextView;

import java.io.StringWriter;

import io.sentry.Sentry;
import io.sentry.UserFeedback;
import io.sentry.protocol.SentryId;

public class CrashHandler extends FoxActivity {

    @SuppressLint("RestrictedApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash_handler);
        // set crash_details MaterialTextView to the exception passed in the intent or unknown if null
        // convert stacktrace from array to string, and pretty print it (first line is the exception, the rest is the stacktrace, with each line indented by 4 spaces)
        // first line is the exception, the rest is the stacktrace, with each line indented by 4 spaces. empty out the material text view first
        MaterialTextView crashDetails = findViewById(R.id.crash_details);
        crashDetails.setText("");
        // get the exception from the intent
        Throwable exception = (Throwable) getIntent().getSerializableExtra("exception");
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
        // disable feedback if sentry is disabled
        if (MainApplication.isCrashReportingEnabled()) {
            SharedPreferences preferences = getSharedPreferences("sentry", MODE_PRIVATE);
            // get lastEventId from intent
            SentryId lastEventId = Sentry.captureException((Throwable) getIntent().getSerializableExtra("exception"));
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
                String nameString = name.getText().toString().equals("") ? "Anonymous" : name.getText().toString();
                String emailString = email.getText().toString().equals("") ? "Anonymous" : email.getText().toString();
                // Prevent strict mode violation
                new Thread(() -> {
                    // create sentry userFeedback request
                    UserFeedback userFeedback = new UserFeedback(lastEventId);
                    userFeedback.setName(nameString);
                    userFeedback.setEmail(emailString);
                    userFeedback.setComments(description.getText().toString());
                    // send the request
                    Sentry.captureUserFeedback(userFeedback);
                }).start();
                // Close the activity
                finish();
                // start the main activity
                startActivity(getPackageManager().getLaunchIntentForPackage(getPackageName()));
            });
            // get restart button
            findViewById(R.id.restart).setOnClickListener(v -> {
                // Save the user's name and email
                preferences.edit().putString("name", name.getText().toString()).putString("email", email.getText().toString()).apply();
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
                e.printStackTrace();
            }
            runOnUiThread(() -> view.setBackgroundResource(R.drawable.baseline_copy_all_24));
        }).start();
    }
}