package com.fox2code.mmm.background;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.fox2code.mmm.MainActivity;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.manager.LocalModuleInfo;
import com.fox2code.mmm.manager.ModuleManager;
import com.fox2code.mmm.repo.RepoManager;
import com.fox2code.mmm.repo.RepoModule;
import com.fox2code.mmm.utils.io.PropUtils;

import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class BackgroundUpdateChecker extends Worker {
    public static final String NOTIFICATION_CHANNEL_ID = "background_update";
    public static final int NOTIFICATION_ID = 1;
    static final Object lock = new Object(); // Avoid concurrency issues
    private static boolean easterEggActive = false;

    public BackgroundUpdateChecker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    static void doCheck(Context context) {
        Thread.currentThread().setPriority(2);
        ModuleManager.getINSTANCE().scanAsync();
        RepoManager.getINSTANCE().update(null);
        ModuleManager.getINSTANCE().runAfterScan(() -> {
            int moduleUpdateCount = 0;
            HashMap<String, RepoModule> repoModules = RepoManager.getINSTANCE().getModules();
            for (LocalModuleInfo localModuleInfo : ModuleManager.getINSTANCE().getModules().values()) {
                if ("twrp-keep".equals(localModuleInfo.id))
                    continue;
                // exclude all modules with id's stored in the pref pref_background_update_check_excludes
                if (MainApplication.getSharedPreferences().getStringSet("pref_background_update_check_excludes", null).contains(localModuleInfo.id))
                    continue;
                RepoModule repoModule = repoModules.get(localModuleInfo.id);
                localModuleInfo.checkModuleUpdate();
                if (localModuleInfo.updateVersionCode > localModuleInfo.versionCode && !PropUtils.isNullString(localModuleInfo.updateVersion)) {
                    moduleUpdateCount++;
                } else if (repoModule != null && repoModule.moduleInfo.versionCode > localModuleInfo.versionCode && !PropUtils.isNullString(repoModule.moduleInfo.version)) {
                    moduleUpdateCount++;
                }
            }
            if (moduleUpdateCount != 0) {
                postNotification(context, moduleUpdateCount, false);
            }
        });
    }

    public static void postNotification(Context context, int updateCount, boolean test) {
        if (!easterEggActive)
            easterEggActive = new Random().nextInt(100) <= updateCount;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID).setContentTitle(context.getString(easterEggActive ? R.string.notification_update_title_easter_egg : R.string.notification_update_title).replace("%i", String.valueOf(updateCount))).setContentText(context.getString(R.string.notification_update_subtitle)).setSmallIcon(R.drawable.ic_baseline_extension_24).setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK), PendingIntent.FLAG_IMMUTABLE)).setAutoCancel(true);
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
        if (MainApplication.getSharedPreferences().getBoolean("first_time_setup_done", true))
            return;
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
        notificationManagerCompat.createNotificationChannel(new NotificationChannelCompat.Builder(NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH).setShowBadge(true).setName(context.getString(R.string.notification_update_pref)).build());
        notificationManagerCompat.cancel(BackgroundUpdateChecker.NOTIFICATION_ID);
        BackgroundUpdateChecker.easterEggActive = false;
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("background_checker", ExistingPeriodicWorkPolicy.REPLACE, new PeriodicWorkRequest.Builder(BackgroundUpdateChecker.class, 6, TimeUnit.HOURS).setConstraints(new Constraints.Builder().setRequiresBatteryNotLow(true).setRequiredNetworkType(NetworkType.UNMETERED).build()).build());
    }

    public static void onMainActivityResume(Context context) {
        NotificationManagerCompat.from(context).cancel(BackgroundUpdateChecker.NOTIFICATION_ID);
        BackgroundUpdateChecker.easterEggActive = false;
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
