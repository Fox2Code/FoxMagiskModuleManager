package com.fox2code.mmm.background;

import android.Manifest;
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

import com.fox2code.mmm.MainActivity;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

@SuppressWarnings("SpellCheckingInspection")
public class BackgroundUpdateChecker extends Worker {
    public static final String NOTIFICATION_CHANNEL_ID = "background_update";
    public static final int NOTIFICATION_ID = 1;
    public static final String NOTFIICATION_GROUP = "updates";
    static final Object lock = new Object(); // Avoid concurrency issues

    public BackgroundUpdateChecker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    static void doCheck(Context context) {
        // first, check if the user has enabled background update checking
        if (!MainApplication.getPreferences("mmm").getBoolean("pref_background_update_check", false)) {
            return;
        }
        if (MainApplication.getINSTANCE().isInForeground()) {
            // don't check if app is in foreground, this is a background check
            return;
        }
        // next, check if user requires wifi
        if (MainApplication.getPreferences("mmm").getBoolean("pref_background_update_check_wifi", true)) {
            // check if wifi is connected
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network networkInfo = connectivityManager.getActiveNetwork();
            if (networkInfo == null || !connectivityManager.getNetworkCapabilities(networkInfo).hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                Timber.w("Background update check: wifi not connected but required");
                return;
            }
        }
        // start foreground service
        Intent intent = new Intent(context, BackgroundUpdateCheckerService.class);
        intent.setAction(BackgroundUpdateCheckerService.ACTION_START_FOREGROUND_SERVICE);
        ContextCompat.startForegroundService(context, intent);
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
        if (MainApplication.getINSTANCE().isInForeground() && !test) return;
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
    }

    public static void onMainActivityCreate(Context context) {
        // Refuse to run if first_launch pref is not false
        if (!Objects.equals(MainApplication.getPreferences("mmm").getString("last_shown_setup", null), "v1"))
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
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("background_checker", ExistingPeriodicWorkPolicy.UPDATE, new PeriodicWorkRequest.Builder(BackgroundUpdateChecker.class, 6, TimeUnit.HOURS).setConstraints(new Constraints.Builder().setRequiresBatteryNotLow(true).build()).build());
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
