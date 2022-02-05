package com.fox2code.mmm;

import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.fox2code.mmm.compat.CompatActivity;
import com.fox2code.mmm.installer.InstallerInitializer;
import com.fox2code.mmm.repo.RepoManager;
import com.fox2code.mmm.utils.Files;
import com.fox2code.mmm.utils.IntentHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipFile;

interface NotificationTypeCst {
    String TAG = "NotificationType";
}

public enum NotificationType implements NotificationTypeCst {
    SHOWCASE_MODE(R.string.showcase_mode, R.drawable.ic_baseline_lock_24,
            R.attr.colorPrimary, R.attr.colorOnPrimary) {
        @Override
        public boolean shouldRemove() {
            return !MainApplication.isShowcaseMode();
        }
    },
    NO_ROOT(R.string.fail_root_magisk, R.drawable.ic_baseline_numbers_24) {
        @Override
        public boolean shouldRemove() {
            return InstallerInitializer.peekMagiskPath() != null;
        }
    },
    MAGISK_OUTDATED(R.string.magisk_outdated, R.drawable.ic_baseline_update_24) {
        @Override
        public boolean shouldRemove() {
            return InstallerInitializer.peekMagiskPath() == null ||
                    InstallerInitializer.peekMagiskVersion() >=
                            Constants.MAGISK_VER_CODE_INSTALL_COMMAND;
        }
    },
    NO_INTERNET(R.string.fail_internet, R.drawable.ic_baseline_cloud_off_24) {
        @Override
        public boolean shouldRemove() {
            return AppUpdateManager.getAppUpdateManager().isLastCheckSuccess() ||
                    RepoManager.getINSTANCE().hasConnectivity();
        }
    },
    UPDATE_AVAILABLE(R.string.app_update_available, R.drawable.ic_baseline_system_update_24,
            R.attr.colorPrimary, R.attr.colorOnPrimary, v -> {
        IntentHelper.openUrl(v.getContext(),
                "https://github.com/Fox2Code/FoxMagiskModuleManager/releases");
    }, false) {
        @Override
        public boolean shouldRemove() {
            return !AppUpdateManager.getAppUpdateManager().peekShouldUpdate();
        }
    },
    INSTALL_FROM_STORAGE(R.string.install_from_storage, R.drawable.ic_baseline_storage_24,
            R.attr.colorBackgroundFloating, R.attr.colorOnBackground, v -> {
        CompatActivity compatActivity = CompatActivity.getCompatActivity(v);
        final File module = new File(compatActivity.getCacheDir(),
                "installer" + File.separator + "module.zip");
        IntentHelper.openFileTo(compatActivity, module, (d, u, s) -> {
            if (s == IntentHelper.RESPONSE_FILE) {
                try {
                    boolean needPatch;
                    try (ZipFile zipFile = new ZipFile(d)) {
                        needPatch = zipFile.getEntry("module.prop") == null;
                    }
                    if (needPatch) {
                        Files.patchModuleSimple(Files.read(d),
                                new FileOutputStream(d));
                    }
                    try (ZipFile zipFile = new ZipFile(d)) {
                        needPatch = zipFile.getEntry("module.prop") == null;
                    }
                    if (needPatch) {
                        if (d.exists() && !d.delete())
                            Log.w(TAG, "Failed to delete non module zip");
                        Toast.makeText(compatActivity,
                                R.string.invalid_format, Toast.LENGTH_SHORT).show();
                    } else {
                        IntentHelper.openInstaller(compatActivity, d.getAbsolutePath(),
                                compatActivity.getString(
                                        R.string.local_install_title), null, null,
                                BuildConfig.DEBUG && // Use debug mode if no root
                                        InstallerInitializer.peekMagiskPath() == null);
                    }
                } catch (IOException ignored) {
                    if (d.exists() && !d.delete())
                        Log.w(TAG, "Failed to delete invalid module");
                    Toast.makeText(compatActivity,
                            R.string.invalid_format, Toast.LENGTH_SHORT).show();
                }
            } else if (s == IntentHelper.RESPONSE_URL) {
                IntentHelper.openInstaller(compatActivity, u.toString(),
                        compatActivity.getString(
                                R.string.remote_install_title), null, null,
                        BuildConfig.DEBUG && // Use debug mode if no root
                                InstallerInitializer.peekMagiskPath() == null);
            }
        });
    }, true) {
        @Override
        public boolean shouldRemove() {
            return !BuildConfig.DEBUG &&
                    (MainApplication.isShowcaseMode() ||
                            InstallerInitializer.peekMagiskPath() == null);
        }
    };

    @StringRes
    public final int textId;
    @DrawableRes
    public final int iconId;
    @AttrRes
    public final int backgroundAttr;
    @AttrRes
    public final int foregroundAttr;
    public final View.OnClickListener onClickListener;
    public final boolean special;

    NotificationType(@StringRes int textId, int iconId) {
        this(textId, iconId, R.attr.colorError, R.attr.colorOnPrimary); //R.attr.colorOnError);
    }

    NotificationType(@StringRes int textId, int iconId, int backgroundAttr, int foregroundAttr) {
        this(textId, iconId, backgroundAttr, foregroundAttr, null);
    }

    NotificationType(@StringRes int textId, int iconId, int backgroundAttr, int foregroundAttr,
                     View.OnClickListener onClickListener) {
        this(textId, iconId, backgroundAttr, foregroundAttr, onClickListener, false);
    }

    NotificationType(@StringRes int textId, int iconId, int backgroundAttr, int foregroundAttr,
                     View.OnClickListener onClickListener, boolean special) {
        this.textId = textId;
        this.iconId = iconId;
        this.backgroundAttr = backgroundAttr;
        this.foregroundAttr = foregroundAttr;
        this.onClickListener = onClickListener;
        this.special = special;
    }

    public boolean shouldRemove() {
        return false;
    }
}
