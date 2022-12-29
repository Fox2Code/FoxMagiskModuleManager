package com.fox2code.mmm.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.TypedValue;
import android.widget.Toast;

import androidx.core.app.ActivityOptionsCompat;
import androidx.core.app.BundleCompat;

import com.fox2code.foxcompat.app.FoxActivity;
import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.Constants;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.XHooks;
import com.fox2code.mmm.androidacy.AndroidacyActivity;
import com.fox2code.mmm.installer.InstallerActivity;
import com.fox2code.mmm.markdown.MarkdownActivity;
import com.topjohnwu.superuser.CallbackList;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.io.SuFileInputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;

public class IntentHelper {
    private static final String TAG = "IntentHelper";
    private static final String EXTRA_TAB_SESSION =
            "android.support.customtabs.extra.SESSION";
    private static final String EXTRA_TAB_COLOR_SCHEME =
            "androidx.browser.customtabs.extra.COLOR_SCHEME";
    private static final int EXTRA_TAB_COLOR_SCHEME_DARK = 2;
    private static final int EXTRA_TAB_COLOR_SCHEME_LIGHT = 1;
    private static final String EXTRA_TAB_TOOLBAR_COLOR =
            "android.support.customtabs.extra.TOOLBAR_COLOR";
    private static final String EXTRA_TAB_EXIT_ANIMATION_BUNDLE =
            "android.support.customtabs.extra.EXIT_ANIMATION_BUNDLE";
    static final int FLAG_GRANT_URI_PERMISSION = Intent.FLAG_GRANT_READ_URI_PERMISSION;

    public static void openUri(Context context, String uri) {
        if (uri.startsWith("intent://")) {
            try {
                startActivity(context, Intent.parseUri(uri, Intent.URI_INTENT_SCHEME), false);
            } catch (URISyntaxException | ActivityNotFoundException e) {
                Log.e(TAG, "Failed launch of " + uri, e);
            }
        } else openUrl(context, uri);
    }

    public static void openUrl(Context context, String url) {
        openUrl(context, url, false);
    }

    public static void openUrl(Context context, String url, boolean forceBrowser) {
        try {
            Intent myIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            myIntent.setFlags(FLAG_GRANT_URI_PERMISSION);
            if (forceBrowser) {
                myIntent.addCategory(Intent.CATEGORY_BROWSABLE);
            }
            startActivity(context, myIntent, false);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "No application can handle this request.\n"
                    + " Please install a web-browser", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    public static void openCustomTab(Context context, String url) {
        try {
            Intent viewIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            viewIntent.setFlags(FLAG_GRANT_URI_PERMISSION);
            Intent tabIntent = new Intent(viewIntent);
            tabIntent.setFlags(FLAG_GRANT_URI_PERMISSION);
            tabIntent.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivityEx(context, tabIntent, viewIntent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "No application can handle this request.\n"
                    + " Please install a web-browser", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    public static void openUrlAndroidacy(Context context, String url,boolean allowInstall) {
        openUrlAndroidacy(context, url, allowInstall, null, null);
    }

    public static void openUrlAndroidacy(Context context, String url, boolean allowInstall,
                                         String title,String config) {
        if (!Http.hasWebView()) {
            Log.w(TAG, "Using custom tab for: " + url);
            openCustomTab(context, url);
            return;
        }
        Uri uri = Uri.parse(url);
        try {
            Intent myIntent = new Intent(
                    Constants.INTENT_ANDROIDACY_INTERNAL,
                    uri, context, AndroidacyActivity.class);
            myIntent.putExtra(Constants.EXTRA_ANDROIDACY_ALLOW_INSTALL, allowInstall);
            if (title != null)
                myIntent.putExtra(Constants.EXTRA_ANDROIDACY_ACTIONBAR_TITLE, title);
            if (config != null)
                myIntent.putExtra(Constants.EXTRA_ANDROIDACY_ACTIONBAR_CONFIG, config);
            MainApplication.addSecret(myIntent);
            startActivity(context, myIntent, true);
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
            Intent intent = XHooks.getConfigIntent(context, pkg, config);
            if (intent == null) {
                if ("org.lsposed.manager".equals(config) && (
                        XHooks.isModuleActive("riru_lsposed") ||
                        XHooks.isModuleActive("zygisk_lsposed"))) {
                    Shell.getShell().newJob().add(
                            "am start -a android.intent.action.MAIN " +
                                    "-c org.lsposed.manager.LAUNCH_MANAGER " +
                                    "com.android.shell/.BugreportWarningActivity")
                            .to(new CallbackList<>() {
                                @Override
                                public void onAddElement(String str) {
                                    Log.i(TAG, "LSPosed: " + str);
                                }
                            }).submit();
                    return;
                }
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

    public static void openMarkdown(Context context, String url, String title, String config, Boolean changeBoot, Boolean needsRamdisk,int minMagisk, int minApi, int maxApi) {
        try {
            Intent intent = new Intent(context, MarkdownActivity.class);
            MainApplication.addSecret(intent);
            intent.putExtra(Constants.EXTRA_MARKDOWN_URL, url);
            intent.putExtra(Constants.EXTRA_MARKDOWN_TITLE, title);
            intent.putExtra(Constants.EXTRA_MARKDOWN_CHANGE_BOOT, changeBoot);
            intent.putExtra(Constants.EXTRA_MARKDOWN_NEEDS_RAMDISK, needsRamdisk);
            intent.putExtra(Constants.EXTRA_MARKDOWN_MIN_MAGISK, minMagisk);
            intent.putExtra(Constants.EXTRA_MARKDOWN_MIN_API, minApi);
            intent.putExtra(Constants.EXTRA_MARKDOWN_MAX_API, maxApi);
            if (config != null && !config.isEmpty())
                intent.putExtra(Constants.EXTRA_MARKDOWN_CONFIG, config);
            startActivity(context, intent, true);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context,
                    "Failed to launch markdown activity",  Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    public static void openInstaller(Context context, String url, String title, String config,
                                       String checksum, boolean mmtReborn) {
        openInstaller(context, url, title, config, checksum, mmtReborn, false);
    }

    public static void openInstaller(Context context, String url, String title, String config,
                                     String checksum, boolean mmtReborn, boolean testDebug) {
        try {
            Intent intent = new Intent(context, InstallerActivity.class);
            intent.setAction(Constants.INTENT_INSTALL_INTERNAL);
            MainApplication.addSecret(intent);
            intent.putExtra(Constants.EXTRA_INSTALL_PATH, url);
            intent.putExtra(Constants.EXTRA_INSTALL_NAME, title);
            if (config != null && !config.isEmpty())
                intent.putExtra(Constants.EXTRA_INSTALL_CONFIG, config);
            if (checksum != null && !checksum.isEmpty())
                intent.putExtra(Constants.EXTRA_INSTALL_CHECKSUM, checksum);
            if (mmtReborn) // Allow early styling of install process
                intent.putExtra(Constants.EXTRA_INSTALL_MMT_REBORN, true);
            if (testDebug && BuildConfig.DEBUG)
                intent.putExtra(Constants.EXTRA_INSTALL_TEST_ROOTLESS, true);
            startActivity(context, intent, true);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context,
                    "Failed to launch markdown activity",  Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    public static void startActivity(Context context, Class<? extends Activity> activityClass) {
        startActivity(context, new Intent(context, activityClass), true);
    }

    public static void startActivity(Context context, Intent intent,boolean sameApp)
            throws ActivityNotFoundException {
        if (sameApp) {
            startActivityEx(context, intent, null);
        } else {
            startActivityEx(context, null, intent);
        }
    }

    public static void startActivityEx(Context context, Intent intent1,Intent intent2)
            throws ActivityNotFoundException {
        if (intent1 == null && intent2 == null)
            throw new NullPointerException("No intent defined for activity!");
        changeFlags(intent1, true);
        changeFlags(intent2, false);
        Activity activity = getActivity(context);
        Bundle param = ActivityOptionsCompat.makeCustomAnimation(context,
                android.R.anim.fade_in, android.R.anim.fade_out).toBundle();
        if (activity == null) {
            if (intent1 != null) {
                try {
                    context.startActivity(intent1, param);
                    return;
                } catch (ActivityNotFoundException e) {
                    if (intent2 == null) throw e;
                }
            }
            context.startActivity(intent2, param);
        } else {
            if (intent1 != null) {
                // Support Custom Tabs as sameApp intent
                if (intent1.hasCategory(Intent.CATEGORY_BROWSABLE)) {
                    if (!intent1.hasExtra(EXTRA_TAB_SESSION)) {
                        Bundle bundle = new Bundle();
                        BundleCompat.putBinder(bundle, EXTRA_TAB_SESSION, null);
                        intent1.putExtras(bundle);
                    }
                    intent1.putExtra(IntentHelper.EXTRA_TAB_EXIT_ANIMATION_BUNDLE, param);
                    if (activity instanceof FoxActivity) {
                        TypedValue typedValue = new TypedValue();
                        activity.getTheme().resolveAttribute(
                                android.R.attr.background, typedValue, true);
                        if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
                                typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                            intent1.putExtra(IntentHelper.EXTRA_TAB_TOOLBAR_COLOR, typedValue.data);
                            intent1.putExtra(IntentHelper.EXTRA_TAB_COLOR_SCHEME,
                                    ((FoxActivity) activity).isLightTheme() ?
                                    IntentHelper.EXTRA_TAB_COLOR_SCHEME_LIGHT :
                                    IntentHelper.EXTRA_TAB_COLOR_SCHEME_DARK);
                        }
                    }
                }
                try {
                    intent1.putExtra(Constants.EXTRA_FADE_OUT, true);
                    activity.overridePendingTransition(
                            android.R.anim.fade_in, android.R.anim.fade_out);
                    activity.startActivity(intent1, param);
                    return;
                } catch (ActivityNotFoundException e) {
                    if (intent2 == null) throw e;
                }
            }
            activity.startActivity(intent2, param);
        }
    }

    private static void changeFlags(Intent intent,boolean sameApp) {
        if (intent == null) return;
        int flags = intent.getFlags() &
                ~(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        if (!sameApp) {
            flags &= ~Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
            if (intent.getData() == null) {
                flags |= Intent.FLAG_ACTIVITY_NEW_TASK;
            } else {
                flags |= Intent.FLAG_ACTIVITY_NEW_DOCUMENT;
            }
        }
        intent.setFlags(flags);
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
    public static void openFileTo(FoxActivity compatActivity, File destination,
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
            Log.i(TAG, "FilePicker returned " + uri);
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
                Log.i(TAG, "File saved at " + destination);
                success = true;
            } catch (Exception e) {
                Log.e(TAG, "failed copy of " + uri, e);
                Toast.makeText(compatActivity,
                        R.string.file_picker_failure,
                        Toast.LENGTH_SHORT).show();
            } finally {
                Files.closeSilently(inputStream);
                Files.closeSilently(outputStream);
                if (!success && destination.exists() && !destination.delete())
                    Log.e(TAG, "Failed to delete artefact!");
            }
            callback.onReceived(destination, uri, success ? RESPONSE_FILE : RESPONSE_ERROR);
        });
    }

    public interface OnFileReceivedCallback {
        void onReceived(File target,Uri uri,int response);
    }
}
