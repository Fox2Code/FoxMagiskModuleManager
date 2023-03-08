package com.fox2code.mmm;

import android.annotation.SuppressLint;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;

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
            // per process webview data dir
            WebView.setDataDirectorySuffix(FoxApplication.getProcessName());
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
        SharedPreferences preferences = getSharedPreferences("sentry", MODE_PRIVATE);
        // get lastEventId from intent
        String lastEventId = getIntent().getStringExtra("lastEventId");
        Timber.d("CrashHandler.onCreate: lastEventId=%s, crashReportingEnabled=%s", lastEventId, crashReportingEnabled);
        if (lastEventId == null && crashReportingEnabled) {
            // if lastEventId is null, hide the feedback button
            findViewById(R.id.feedback).setVisibility(View.GONE);
            Timber.d("CrashHandler.onCreate: lastEventId is null but crash reporting is enabled. This may indicate a bug in the crash reporting system.");
        } else {
            // if lastEventId is not null, show the feedback button
            findViewById(R.id.feedback).setVisibility(View.VISIBLE);
            // set the name and email fields to the saved values
            EditText name = findViewById(R.id.feedback_name);
            EditText email = findViewById(R.id.feedback_email);
            name.setText(preferences.getString("name", ""));
            email.setText(preferences.getString("email", ""));
        }
        // disable feedback if sentry is disabled
        //noinspection ConstantConditions
        if (crashReportingEnabled && !BuildConfig.SENTRY_TOKEN.equals("") && lastEventId != null) {
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
                // Prevent strict mode violation
                // create sentry userFeedback request
                new Thread(() -> {
                    try {
                        HttpURLConnection connection = (HttpURLConnection) new URL("https" + "://sentry.io/api/0/projects/androidacy-i6/foxmmm/user-feedback/").openConnection();
                        connection.setRequestMethod("POST");
                        connection.setRequestProperty("Content-Type", "application/json");
                        connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.SENTRY_TOKEN);
                        // Setups the JSON body
                        if (nameString[0].equals(""))
                            nameString[0] = "Anonymous";
                        if (emailString[0].equals(""))
                            emailString[0] = "Anonymous";
                        JSONObject body = new JSONObject();
                        body.put("event_id", lastEventId);
                        body.put("name", nameString[0]);
                        body.put("email", emailString[0]);
                        body.put("comments", description.getText().toString());
                        // Send the request
                        connection.setDoOutput(true);
                        OutputStream outputStream = connection.getOutputStream();
                        outputStream.write(body.toString().getBytes());
                        outputStream.flush();
                        outputStream.close();
                        // close and disconnect the connection
                        connection.getInputStream().close();
                        connection.disconnect();
                        // For debug builds, log the response code and response body
                        if (BuildConfig.DEBUG) {
                            Timber.d("Response Code: %s", connection.getResponseCode());
                        }
                        // Check if the request was successful
                        if (connection.getResponseCode() == 200) {
                            runOnUiThread(() -> Toast.makeText(this, R.string.sentry_dialogue_success, Toast.LENGTH_LONG).show());
                        } else {
                            runOnUiThread(() -> Toast.makeText(this, R.string.sentry_dialogue_failed_toast, Toast.LENGTH_LONG).show());
                        }
                    } catch (
                            JSONException |
                            IOException ignored) {
                        // Show a toast if the user feedback could not be submitted
                        runOnUiThread(() -> Toast.makeText(this, R.string.sentry_dialogue_failed_toast, Toast.LENGTH_LONG).show());
                    }
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
            } catch (
                    InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            runOnUiThread(() -> view.setBackgroundResource(R.drawable.baseline_copy_all_24));
        }).start();
    }
}