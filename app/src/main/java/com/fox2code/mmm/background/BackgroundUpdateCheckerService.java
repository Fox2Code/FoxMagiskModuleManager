package com.fox2code.mmm.background;

import static com.fox2code.mmm.background.BackgroundUpdateChecker.NOTFIICATION_GROUP;
import static com.fox2code.mmm.background.BackgroundUpdateChecker.postNotification;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.fox2code.foxcompat.app.internal.FoxIntentActivity;
import com.fox2code.mmm.AppUpdateManager;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.UpdateActivity;
import com.fox2code.mmm.manager.LocalModuleInfo;
import com.fox2code.mmm.manager.ModuleManager;
import com.fox2code.mmm.repo.RepoManager;
import com.fox2code.mmm.repo.RepoModule;
import com.fox2code.mmm.utils.io.PropUtils;

import java.util.HashMap;

import timber.log.Timber;

@SuppressLint("RestrictedApi")
public class BackgroundUpdateCheckerService extends FoxIntentActivity {
    public static final String NOTIFICATION_CHANNEL_ID = "background_update";
    public static final String NOTIFICATION_CHANNEL_ID_APP = "background_update_app";
    public static final String ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE";
    static final Object lock = new Object(); // Avoid concurrency issues
    private static final int NOTIFICATION_ID_ONGOING = 2;
    private static final String NOTIFICATION_CHANNEL_ID_ONGOING = "mmm_background_update";
    private static final int NOTIFICATION_ID_APP = 3;

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

    public void onCreate() {
        Context context = MainApplication.getINSTANCE().getApplicationContext();
        // check if action is ACTION_START_FOREGROUND_SERVICE, bail out if not
        if (!ACTION_START_FOREGROUND_SERVICE.equals(getIntent().getAction())) {
            return;
        }
        Timber.d("Starting background update checker service");
        // acquire lock
        synchronized (lock) {
            // post checking notification if notofiications are enabled
            if (ContextCompat.checkSelfPermission(MainApplication.getINSTANCE(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
                notificationManager.createNotificationChannel(new NotificationChannelCompat.Builder(NOTIFICATION_CHANNEL_ID_ONGOING, NotificationManagerCompat.IMPORTANCE_MIN).setName(context.getString(R.string.notification_channel_category_background_update)).setDescription(context.getString(R.string.notification_channel_category_background_update_description)).setGroup(NOTFIICATION_GROUP).build());
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID);
                builder.setSmallIcon(R.drawable.ic_baseline_update_24);
                builder.setPriority(NotificationCompat.PRIORITY_MIN);
                builder.setCategory(NotificationCompat.CATEGORY_SERVICE);
                builder.setShowWhen(false);
                builder.setOnlyAlertOnce(true);
                builder.setOngoing(true);
                builder.setAutoCancel(false);
                builder.setGroup("update");
                builder.setContentTitle(context.getString(R.string.notification_channel_background_update));
                builder.setContentText(context.getString(R.string.notification_channel_background_update_description));
                notificationManager.notify(NOTIFICATION_ID_ONGOING, builder.build());
            } else {
                Timber.d("Not posting notification because of missing permission");
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
                    if ("twrp-keep".equals(localModuleInfo.id)) continue;
                    // exclude all modules with id's stored in the pref pref_background_update_check_excludes
                    try {
                        if (MainApplication.getPreferences("mmm").getStringSet("pref_background_update_check_excludes", null).contains(localModuleInfo.id))
                            continue;
                    } catch (Exception ignored) {
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
                    Timber.d("Found %d updates", moduleUpdateCount);
                    postNotification(context, updateableModules, moduleUpdateCount, false);
                }
            });
            // check for app updates
            if (MainApplication.getPreferences("mmm").getBoolean("pref_background_update_check_app", false)) {
                try {
                    boolean shouldUpdate = AppUpdateManager.getAppUpdateManager().checkUpdate(true);
                    if (shouldUpdate) {
                        Timber.d("Found app update");
                        postNotificationForAppUpdate(context);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // remove checking notification
            if (ContextCompat.checkSelfPermission(MainApplication.getINSTANCE(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                Timber.d("Removing notification");
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
                notificationManager.cancel(NOTIFICATION_ID_ONGOING);
            }
        }
    }
}
