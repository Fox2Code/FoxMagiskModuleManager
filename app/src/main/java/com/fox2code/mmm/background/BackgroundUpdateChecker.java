package com.fox2code.mmm.background;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.fox2code.mmm.AppUpdateManager;
import com.fox2code.mmm.MainActivity;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.UpdateActivity;
import com.fox2code.mmm.manager.LocalModuleInfo;
import com.fox2code.mmm.manager.ModuleManager;
import com.fox2code.mmm.repo.RepoManager;
import com.fox2code.mmm.repo.RepoModule;
import com.fox2code.mmm.utils.io.PropUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

public class BackgroundUpdateChecker extends Worker {
    public static final String NOTIFICATION_CHANNEL_ID = "background_update";
    public static final String NOTIFICATION_CHANNEL_ID_APP = "background_update_app";
    public static final String NOTIFICATION_CHANNEL_ID_ONGOING = "background_update_status";
    public static final int NOTIFICATION_ID = 1;
    public static final int NOTIFICATION_ID_ONGOING = 2;
    public static final String NOTFIICATION_GROUP = "updates";
    static final Object lock = new Object(); // Avoid concurrency issues
    private static final int NOTIFICATION_ID_APP = 3;

    public BackgroundUpdateChecker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    static void doCheck(Context context) {
        // first, check if the user has enabled background update checking
        if (!MainApplication.getSharedPreferences("mmm").getBoolean("pref_background_update_check", false)) {
            return;
        }
        if (MainApplication.getINSTANCE().isInForeground()) {
            // don't check if app is in foreground, this is a background check
            return;
        }
        // next, check if user requires wifi
        if (MainApplication.getSharedPreferences("mmm").getBoolean("pref_background_update_check_wifi", true)) {
            // check if wifi is connected
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network networkInfo = connectivityManager.getActiveNetwork();
            if (networkInfo == null || !connectivityManager.getNetworkCapabilities(networkInfo).hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                Timber.w("Background update check: wifi not connected but required");
                return;
            }
        }
        // post checking notification if notofiications are enabled
        if (ContextCompat.checkSelfPermission(MainApplication.getINSTANCE(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
                notificationManager.createNotificationChannel(new NotificationChannelCompat.Builder(NOTIFICATION_CHANNEL_ID_ONGOING, NotificationManagerCompat.IMPORTANCE_LOW).setName(context.getString(R.string.notification_channel_category_background_update)).setDescription(context.getString(R.string.notification_channel_category_background_update_description)).setGroup(NOTFIICATION_GROUP).build());
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID);
                builder.setSmallIcon(R.drawable.ic_baseline_update_24);
                builder.setPriority(NotificationCompat.PRIORITY_LOW);
                builder.setCategory(NotificationCompat.CATEGORY_RECOMMENDATION);
                builder.setShowWhen(false);
                builder.setOnlyAlertOnce(true);
                builder.setOngoing(true);
                builder.setAutoCancel(false);
                builder.setGroup("update");
                builder.setContentTitle(context.getString(R.string.notification_channel_background_update));
                builder.setContentText(context.getString(R.string.notification_channel_background_update_description));
                notificationManager.notify(NOTIFICATION_ID_ONGOING, builder.build());
        }
        Thread.currentThread().setPriority(2);
        ModuleManager.getINSTANCE().scanAsync();
        RepoManager.getINSTANCE().update(null);
        ModuleManager.getINSTANCE().runAfterScan(() -> {
            int moduleUpdateCount = 0;
            HashMap<String, RepoModule> repoModules = RepoManager.getINSTANCE().getModules();
            // hasmap of updateable modules names
            HashMap<String, String> updateableModules = new HashMap<>();
            for (LocalModuleInfo localModuleInfo : ModuleManager.getINSTANCE().getModules().values()) {
                if ("twrp-keep".equals(localModuleInfo.id))
                    continue;
                // exclude all modules with id's stored in the pref pref_background_update_check_excludes
                try {
                    if (MainApplication.getSharedPreferences("mmm").getStringSet("pref_background_update_check_excludes", null).contains(localModuleInfo.id))
                        continue;
                } catch (
                        Exception ignored) {
                }
                RepoModule repoModule = repoModules.get(localModuleInfo.id);
                localModuleInfo.checkModuleUpdate();
                if (localModuleInfo.updateVersionCode > localModuleInfo.versionCode && !PropUtils.isNullString(localModuleInfo.updateVersion)) {
                    moduleUpdateCount++;
                    updateableModules.put(localModuleInfo.name, localModuleInfo.version);
                } else if (repoModule != null && repoModule.moduleInfo.versionCode > localModuleInfo.versionCode && !PropUtils.isNullString(repoModule.moduleInfo.version)) {
                    moduleUpdateCount++;
                    updateableModules.put(localModuleInfo.name, localModuleInfo.version);
                }
            }
            if (moduleUpdateCount != 0) {
                postNotification(context, updateableModules, moduleUpdateCount, false);
            }
        });
        // check for app updates
        if (MainApplication.getSharedPreferences("mmm").getBoolean("pref_background_update_check_app", false)) {
            try {
                boolean shouldUpdate = AppUpdateManager.getAppUpdateManager().checkUpdate(true);
                if (shouldUpdate) {
                    postNotificationForAppUpdate(context);
                }
            } catch (
                    Exception e) {
                e.printStackTrace();
            }
        }
        // remove checking notification
        if (ContextCompat.checkSelfPermission(MainApplication.getINSTANCE(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            notificationManager.cancel(NOTIFICATION_ID_ONGOING);
        }
    }

    @SuppressLint("RestrictedApi")
    private static void postNotificationForAppUpdate(Context context) {
        // create the notification channel if not already created
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.createNotificationChannel(new NotificationChannelCompat.Builder(NOTIFICATION_CHANNEL_ID_APP, NotificationManagerCompat.IMPORTANCE_HIGH).setName(context.getString(R.string.notification_channel_category_app_update)).setDescription(context.getString(R.string.notification_channel_category_app_update_description)).setGroup(NOTFIICATION_GROUP).build());
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_APP);
        builder.setSmallIcon(R.drawable.baseline_system_update_24);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setCategory(NotificationCompat.CATEGORY_RECOMMENDATION);
        builder.setShowWhen(false);
        builder.setOnlyAlertOnce(true);
        builder.setOngoing(false);
        builder.setAutoCancel(true);
        builder.setGroup(NOTFIICATION_GROUP);
        // open app on click
        Intent intent = new Intent(context, UpdateActivity.class);
        // set action to ACTIONS.DOWNLOAD
        intent.setAction(String.valueOf(UpdateActivity.ACTIONS.DOWNLOAD));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        builder.setContentIntent(android.app.PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE));
        // set summary to Found X
        builder.setContentTitle(context.getString(R.string.notification_channel_background_update_app));
        builder.setContentText(context.getString(R.string.notification_channel_background_update_app_description));
        if (ContextCompat.checkSelfPermission(MainApplication.getINSTANCE(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(NOTIFICATION_ID_APP, builder.build());
        }
    }

    public static void postNotification(Context context, HashMap<String, String> updateable, int updateCount, boolean test) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID);
        builder.setSmallIcon(R.drawable.baseline_system_update_24);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setCategory(NotificationCompat.CATEGORY_RECOMMENDATION);
        builder.setShowWhen(false);
        builder.setOnlyAlertOnce(true);
        builder.setOngoing(false);
        builder.setAutoCancel(true);
        builder.setGroup(NOTFIICATION_GROUP);
        // open app on click
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        builder.setContentIntent(android.app.PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE));
        // set summary to Found X updates: <module name> <module version> <module name> <module version> ...
        StringBuilder summary = new StringBuilder();
        summary.append(context.getString(R.string.notification_update_summary));
        // use notification_update_module_template string to set name and version
        for (Map.Entry<String, String> entry : updateable.entrySet()) {
            summary.append("\n").append(context.getString(R.string.notification_update_module_template, entry.getKey(), entry.getValue()));
        }
        builder.setContentTitle(context.getString(R.string.notification_update_title, updateCount));
        builder.setContentText(summary);
        // set long text to summary so it doesn't get cut off
        builder.setStyle(new NotificationCompat.BigTextStyle().bigText(summary));
        if (ContextCompat.checkSelfPermission(MainApplication.getINSTANCE(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        // check if app is in foreground. if so, don't show notification
        if (MainApplication.getINSTANCE().isInForeground() && !test)
            return;
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
    }

    public static void onMainActivityCreate(Context context) {
        // Refuse to run if first_launch pref is not false
        if (MainApplication.getSharedPreferences("mmm").getBoolean("first_time_setup_done", true))
            return;
        // create notification channel group
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence groupName = context.getString(R.string.notification_group_updates);
            NotificationManager mNotificationManager = ContextCompat.getSystemService(context, NotificationManager.class);
            Objects.requireNonNull(mNotificationManager).createNotificationChannelGroup(new NotificationChannelGroup(NOTFIICATION_GROUP, groupName));
        }
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
        notificationManagerCompat.createNotificationChannel(new NotificationChannelCompat.Builder(NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH).setShowBadge(true).setName(context.getString(R.string.notification_update_pref)).setDescription(context.getString(R.string.auto_updates_notifs)).setGroup(NOTFIICATION_GROUP).build());
        notificationManagerCompat.cancel(BackgroundUpdateChecker.NOTIFICATION_ID);
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("background_checker", ExistingPeriodicWorkPolicy.REPLACE, new PeriodicWorkRequest.Builder(BackgroundUpdateChecker.class, 6, TimeUnit.HOURS).setConstraints(new Constraints.Builder().setRequiresBatteryNotLow(true).build()).build());
    }

    public static void onMainActivityResume(Context context) {
        NotificationManagerCompat.from(context).cancel(BackgroundUpdateChecker.NOTIFICATION_ID);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!NotificationManagerCompat.from(this.getApplicationContext()).areNotificationsEnabled() || !MainApplication.isBackgroundUpdateCheckEnabled())
            return Result.success();
        synchronized (lock) {
            doCheck(this.getApplicationContext());
        }
        return Result.success();
    }
}
