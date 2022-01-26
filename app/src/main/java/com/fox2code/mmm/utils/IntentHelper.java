package com.fox2code.mmm.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityOptionsCompat;

import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.Constants;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.compat.CompatActivity;
import com.fox2code.mmm.installer.InstallerActivity;
import com.fox2code.mmm.markdown.MarkdownActivity;
import com.topjohnwu.superuser.io.SuFileInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class IntentHelper {
    public static void openUrl(Context context, String url) {
        try {
            Intent myIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            context.startActivity(myIntent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "No application can handle this request."
                    + " Please install a web-browser",  Toast.LENGTH_SHORT).show();
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
        openInstaller(context, url, title, config, false, false);
    }

    public static void openInstaller(Context context, String url, String title, String config,
                                       boolean noPatch,boolean testDebug) {
        try {
            Intent intent = new Intent(context, InstallerActivity.class);
            intent.setAction(Constants.INTENT_INSTALL_INTERNAL);
            MainApplication.addSecret(intent);
            intent.putExtra(Constants.EXTRA_INSTALL_PATH, url);
            intent.putExtra(Constants.EXTRA_INSTALL_NAME, title);
            if (config != null && !config.isEmpty())
                intent.putExtra(Constants.EXTRA_INSTALL_CONFIG, config);
            if (noPatch)
                intent.putExtra(Constants.EXTRA_INSTALL_NO_PATCH, true);
            if (testDebug && BuildConfig.DEBUG)
                intent.putExtra(Constants.EXTRA_INSTALL_TEST_ROOTLESS, true);
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

    public static final int RESPONSE_ERROR = 0;
    public static final int RESPONSE_FILE = 1;
    public static final int RESPONSE_URL = 2;

    @SuppressLint("SdCardPath")
    public static void openFileTo(CompatActivity compatActivity, File destination,
                                  OnFileReceivedCallback callback) {
        File destinationFolder;
        if (destination == null || (destinationFolder = destination.getParentFile()) == null ||
                (!destinationFolder.isDirectory() && !destinationFolder.mkdirs())) {
            callback.onReceived(destination, null, RESPONSE_ERROR);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT).setType("application/zip");
        intent.setFlags(intent.getFlags() & ~Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, false);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        Bundle param = ActivityOptionsCompat.makeCustomAnimation(compatActivity,
                android.R.anim.fade_in, android.R.anim.fade_out).toBundle();
        compatActivity.startActivityForResult(intent, param, (result, data) -> {
            Uri uri = data == null ? null : data.getData();
            if (uri == null || (result == Activity.RESULT_CANCELED && !((
                    ContentResolver.SCHEME_FILE.equals(uri.getScheme())
                            && uri.getPath() != null &&
                            (uri.getPath().startsWith("/sdcard/") ||
                                    uri.getPath().startsWith("/data/"))
                    ) || ContentResolver.SCHEME_ANDROID_RESOURCE.equals(uri.getScheme())))) {
                callback.onReceived(destination, null, RESPONSE_ERROR);
                return;
            }
            Log.d("IntentHelper", "FilePicker returned " + uri.toString());
            if ("http".equals(uri.getScheme()) ||
                    "https".equals(uri.getScheme())) {
                callback.onReceived(destination, uri, RESPONSE_URL);
                return;
            }
            if (ContentResolver.SCHEME_FILE.equals(uri.getScheme()) ||
                    (result != Activity.RESULT_OK && result != Activity.RESULT_FIRST_USER)) {
                Toast.makeText(compatActivity,
                        R.string.file_picker_wierd,
                        Toast.LENGTH_SHORT).show();
            }
            InputStream inputStream = null;
            OutputStream outputStream = null;
            boolean success = false;
            try {
                if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
                    String path = uri.getPath();
                    if (path.startsWith("/sdcard/")) { // Fix file paths
                        path = Environment.getExternalStorageDirectory()
                                .getAbsolutePath() + path.substring(7);
                    }
                    inputStream = SuFileInputStream.open(
                            new File(path).getAbsoluteFile());
                } else {
                    inputStream = compatActivity.getContentResolver()
                            .openInputStream(uri);
                }
                outputStream = new FileOutputStream(destination);
                Files.copy(inputStream, outputStream);
                success = true;
            } catch (Exception e) {
                Log.e("IntentHelper", "failed copy of " + uri, e);
                Toast.makeText(compatActivity,
                        R.string.file_picker_failure,
                        Toast.LENGTH_SHORT).show();
            } finally {
                Files.closeSilently(inputStream);
                Files.closeSilently(outputStream);
                if (!success && destination.exists() && !destination.delete())
                    Log.e("IntentHelper", "Failed to delete artefact!");
            }
            callback.onReceived(destination, uri, success ? RESPONSE_FILE : RESPONSE_ERROR);
        });
    }

    public interface OnFileReceivedCallback {
        void onReceived(File target,Uri uri,int response);
    }
}
