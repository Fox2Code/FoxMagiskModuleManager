package com.fox2code.mmm.utils;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityOptionsCompat;

import com.fox2code.mmm.Constants;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.compat.CompatActivity;
import com.fox2code.mmm.installer.InstallerActivity;
import com.fox2code.mmm.markdown.MarkdownActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class IntentHelper {
    public static void openUrl(Context context, String url) {
        try {
            Intent myIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            context.startActivity(myIntent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "No application can handle this request."
                    + " Please install a webbrowser",  Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    public static String getPackageOfConfig(String config) {
        int i = config.indexOf(' ');
        if (i != -1)
            config = config.substring(0, i);
        i = config.indexOf('/');
        if (i != -1)
            config = config.substring(0, i);
        return config;
    }

    public static void openConfig(Context context, String config) {
        String pkg = getPackageOfConfig(config);
        try {
            Intent intent = context.getPackageManager()
                    .getLaunchIntentForPackage(pkg);
            if (intent == null) {
                intent = new Intent("android.intent.action.APPLICATION_PREFERENCES");
                intent.setPackage(pkg);
            }
            intent.putExtra(Constants.EXTRA_FROM_MANAGER, true);
            startActivity(context, intent, false);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context,
                    "Failed to launch module config activity",  Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    public static void openMarkdown(Context context, String url, String title, String config) {
        try {
            Intent intent = new Intent(context, MarkdownActivity.class);
            MainApplication.addSecret(intent);
            intent.putExtra(Constants.EXTRA_MARKDOWN_URL, url);
            intent.putExtra(Constants.EXTRA_MARKDOWN_TITLE, title);
            if (config != null && !config.isEmpty())
                intent.putExtra(Constants.EXTRA_MARKDOWN_CONFIG, config);
            startActivity(context, intent, true);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context,
                    "Failed to launch markdown activity",  Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    public static void openInstaller(Context context, String url, String title, String config) {
        try {
            Intent intent = new Intent(context, InstallerActivity.class);
            intent.setAction(Constants.INTENT_INSTALL_INTERNAL);
            MainApplication.addSecret(intent);
            intent.putExtra(Constants.EXTRA_INSTALL_PATH, url);
            intent.putExtra(Constants.EXTRA_INSTALL_NAME, title);
            if (config != null && !config.isEmpty())
                intent.putExtra(Constants.EXTRA_INSTALL_CONFIG, config);
            startActivity(context, intent, true);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context,
                    "Failed to launch markdown activity",  Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    public static void startActivity(Context context, Intent intent) {
        ComponentName componentName = intent.getComponent();
        String packageName = context.getPackageName();
        startActivity(context, intent, packageName.equals(intent.getPackage()) ||
                        (componentName != null &&
                                packageName.equals(componentName.getPackageName())));
    }

    public static void startActivity(Context context, Class<? extends Activity> activityClass) {
        startActivity(context, new Intent(context, activityClass), true);
    }

    public static void startActivity(Context context, Intent intent,boolean sameApp)
            throws ActivityNotFoundException {
        int flags = intent.getFlags();
        if (sameApp) {
            flags &= ~Intent.FLAG_ACTIVITY_NEW_TASK;
            // flags |= Intent.FLAG_ACTIVITY_REORDER_TO_FRONT;
        } else {
            flags &= ~Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
            flags |= Intent.FLAG_ACTIVITY_NEW_TASK;
        }
        intent.setFlags(flags);
        Activity activity = getActivity(context);
        Bundle param = ActivityOptionsCompat.makeCustomAnimation(context,
                android.R.anim.fade_in, android.R.anim.fade_out).toBundle();
        if (activity == null) {
            context.startActivity(intent, param);
        } else {
            if (sameApp) {
                intent.putExtra(Constants.EXTRA_FADE_OUT, true);
                activity.overridePendingTransition(
                        android.R.anim.fade_in, android.R.anim.fade_out);
            }
            activity.startActivity(intent, param);
        }
    }

    public static Activity getActivity(Context context) {
        while (!(context instanceof Activity)) {
            if (context instanceof ContextWrapper) {
                context = ((ContextWrapper) context).getBaseContext();
            } else return null;
        }
        return (Activity) context;
    }

    public static void openFileTo(CompatActivity compatActivity, File destination,
                                      OnFileReceivedCallback callback) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT).setType("application/zip");
        intent.setFlags(intent.getFlags() & ~Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        Bundle param = ActivityOptionsCompat.makeCustomAnimation(compatActivity,
                android.R.anim.fade_in, android.R.anim.fade_out).toBundle();
        compatActivity.startActivityForResult(intent, param, (result, data) -> {
            String name = destination.getName();
            if (data == null || result != Activity.RESULT_OK) {
                callback.onReceived(destination, false);
                return;
            }
            Uri uri = data.getData();
            if (uri == null || "http".equals(uri.getScheme()) ||
                    "https".equals(uri.getScheme())) {
                callback.onReceived(destination, false);
                return;
            }
            InputStream inputStream = null;
            OutputStream outputStream = null;
            boolean success = false;
            try {
                inputStream = compatActivity.getContentResolver()
                        .openInputStream(uri);
                outputStream = new FileOutputStream(destination);
                Files.copy(inputStream, outputStream);
                String newName = uri.getLastPathSegment();
                if (newName.endsWith(".zip")) name = newName;
                success = true;
            } catch (Exception e) {
                Log.e("IntentHelper", "fail copy", e);
            } finally {
                Files.closeSilently(inputStream);
                Files.closeSilently(outputStream);
                if (!success && destination.exists() && !destination.delete())
                    Log.e("IntentHelper", "Failed to delete artefact!");
            }
            callback.onReceived(destination, success);
        });
    }

    public interface OnFileReceivedCallback {
        void onReceived(File target,boolean success);
    }
}
