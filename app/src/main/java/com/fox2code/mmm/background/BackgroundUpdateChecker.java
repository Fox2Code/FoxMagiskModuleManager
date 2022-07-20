package com.fox2code.mmm.background;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
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

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class BackgroundUpdateChecker extends Worker {
    private static boolean easterEggActive = false;
    public static final String NOTIFICATION_CHANNEL_ID = "background_update";
    public static final int NOTIFICATION_ID = 1;

    public BackgroundUpdateChecker(@NonNull Context context,
                                   @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!NotificationManagerCompat.from(this.getApplicationContext()).areNotificationsEnabled()
                || !MainApplication.isBackgroundUpdateCheckEnabled()) return Result.success();

        doCheck(this.getApplicationContext());

        return Result.success();
    }

    static void doCheck(Context context) {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        RepoManager.getINSTANCE().update(null);
        ModuleManager.getINSTANCE().scan();
        ModuleManager.getINSTANCE().scan();
        int moduleUpdateCount = 0;
        for (LocalModuleInfo localModuleInfo :
                ModuleManager.getINSTANCE().getModules().values()) {
            RepoModule repoModule = RepoManager.getINSTANCE()
                    .getModules().get(localModuleInfo.id);
            localModuleInfo.checkModuleUpdate();
            if (localModuleInfo.updateVersionCode > localModuleInfo.versionCode) {
                moduleUpdateCount++;
            } else if (repoModule != null &&
                    repoModule.moduleInfo.versionCode > localModuleInfo.versionCode) {
                moduleUpdateCount++;
            }
        }
        if (moduleUpdateCount != 0) {
            postNotification(context, moduleUpdateCount);
        }
    }

    public static void postNotification(Context context, int updateCount) {
        if (!easterEggActive) easterEggActive = new Random().nextInt(100) <= updateCount;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(context.getString(easterEggActive ?
                                R.string.notification_update_title_easter_egg :
                                R.string.notification_update_title)
                        .replace("%i", String.valueOf(updateCount)))
                .setContentText(context.getString(R.string.notification_update_subtitle))
                .setSmallIcon(R.drawable.ic_baseline_extension_24)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(PendingIntent.getActivity(context, 0,
                        new Intent(context, MainActivity.class).setFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                                PendingIntent.FLAG_IMMUTABLE : 0)).setAutoCancel(true);
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
    }

    public static void onMainActivityCreate(Context context) {
        NotificationManagerCompat notificationManagerCompat =
                NotificationManagerCompat.from(context);
        notificationManagerCompat.createNotificationChannel(
                new NotificationChannelCompat.Builder(NOTIFICATION_CHANNEL_ID,
                        NotificationManagerCompat.IMPORTANCE_HIGH).setShowBadge(true)
                        .setName(context.getString(R.string.notification_update_pref)).build());
        notificationManagerCompat.cancel(BackgroundUpdateChecker.NOTIFICATION_ID);
        BackgroundUpdateChecker.easterEggActive = false;
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("background_checker",
                ExistingPeriodicWorkPolicy.REPLACE, new PeriodicWorkRequest.Builder(
                        BackgroundUpdateChecker.class, 6, TimeUnit.HOURS)
                        .setConstraints(new Constraints.Builder().setRequiresBatteryNotLow(true)
                                .setRequiredNetworkType(NetworkType.UNMETERED).build()).build());
    }

    public static void onMainActivityResume(Context context) {
        NotificationManagerCompat.from(context).cancel(
                BackgroundUpdateChecker.NOTIFICATION_ID);
        BackgroundUpdateChecker.easterEggActive = false;
    }
}
