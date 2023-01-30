package com.fox2code.mmm;

import android.view.View;
import android.widget.Toast;

import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.fox2code.foxcompat.app.FoxActivity;
import com.fox2code.mmm.installer.InstallerInitializer;
import com.fox2code.mmm.module.ModuleViewListBuilder;
import com.fox2code.mmm.repo.RepoManager;
import com.fox2code.mmm.utils.IntentHelper;
import com.fox2code.mmm.utils.io.Files;
import com.fox2code.mmm.utils.io.Http;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import timber.log.Timber;

interface NotificationTypeCst {
}

public enum NotificationType implements NotificationTypeCst {
    DEBUG(R.string.debug_build, R.drawable.ic_baseline_bug_report_24, R.attr.colorSecondary, R.attr.colorOnSecondary) {
        @Override
        public boolean shouldRemove() {
            return !BuildConfig.DEBUG;
        }
    },
    SHOWCASE_MODE(R.string.showcase_mode, R.drawable.ic_baseline_lock_24,
            R.attr.colorPrimary, R.attr.colorOnPrimary) {
        @Override
        public boolean shouldRemove() {
            return !MainApplication.isShowcaseMode();
        }
    },
    NO_MAGISK(R.string.fail_magisk_missing, R.drawable.ic_baseline_numbers_24, v ->
            IntentHelper.openUrl(v.getContext(),
                    "https://github.com/topjohnwu/Magisk/blob/master/docs/install.md")) {
        @Override
        public boolean shouldRemove() {
            return InstallerInitializer.getErrorNotification() != this;
        }
    },
    NO_ROOT(R.string.fail_root_magisk, R.drawable.ic_baseline_numbers_24) {
        @Override
        public boolean shouldRemove() {
            return InstallerInitializer.getErrorNotification() != this;
        }
    },
    ROOT_DENIED(R.string.fail_root_denied, R.drawable.ic_baseline_numbers_24) {
        @Override
        public boolean shouldRemove() {
            return InstallerInitializer.getErrorNotification() != this;
        }
    },
    MAGISK_OUTDATED(R.string.magisk_outdated, R.drawable.ic_baseline_update_24, v -> IntentHelper.openUrl(v.getContext(), "https://github.com/topjohnwu/Magisk/releases")) {
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
            return RepoManager.getINSTANCE().hasConnectivity();
        }
    },
    REPO_UPDATE_FAILED(R.string.repo_update_failed, R.drawable.ic_baseline_cloud_off_24) {
        @Override
        public boolean shouldRemove() {
            return RepoManager.getINSTANCE().isLastUpdateSuccess();
        }
    },
    NEED_CAPTCHA_ANDROIDACY(R.string.androidacy_need_captcha, R.drawable.ic_baseline_refresh_24, v ->
            IntentHelper.openUrlAndroidacy(v.getContext(),
                    "https://" + Http.needCaptchaAndroidacyHost() + "/", false)) {
        @Override
        public boolean shouldRemove() {
            return !RepoManager.isAndroidacyRepoEnabled()
                    || !Http.needCaptchaAndroidacy();
        }
    },
    NO_WEB_VIEW(R.string.no_web_view, R.drawable.ic_baseline_android_24) {
        @Override
        public boolean shouldRemove() {
            return Http.hasWebView();
        }
    },
    UPDATE_AVAILABLE(R.string.app_update_available, R.drawable.ic_baseline_system_update_24,
            R.attr.colorPrimary, R.attr.colorOnPrimary, v -> IntentHelper.openUrl(v.getContext(),
                    "https://github.com/Fox2Code/FoxMagiskModuleManager/releases"), false) {
        @Override
        public boolean shouldRemove() {
            return !AppUpdateManager.getAppUpdateManager().peekShouldUpdate();
        }
    },
    INSTALL_FROM_STORAGE(R.string.install_from_storage, R.drawable.ic_baseline_storage_24,
            R.attr.colorBackgroundFloating, R.attr.colorOnBackground, v -> {
        FoxActivity compatActivity = FoxActivity.getFoxActivity(v);
        final File module = new File(compatActivity.getCacheDir(),
                "installer" + File.separator + "module.zip");
        IntentHelper.openFileTo(compatActivity, module, (d, u, s) -> {
            if (s == IntentHelper.RESPONSE_FILE) {
                try {
                    if (needPatch(d)) {
                        Files.patchModuleSimple(Files.read(d),
                                new FileOutputStream(d));
                    }
                    if (needPatch(d)) {
                        if (d.exists() && !d.delete())
                            Timber.w("Failed to delete non module zip");
                        Toast.makeText(compatActivity,
                                R.string.invalid_format, Toast.LENGTH_SHORT).show();
                    } else {
                        IntentHelper.openInstaller(compatActivity, d.getAbsolutePath(),
                                compatActivity.getString(
                                        R.string.local_install_title), null, null, false,
                                BuildConfig.DEBUG && // Use debug mode if no root
                                        InstallerInitializer.peekMagiskPath() == null);
                    }
                } catch (IOException ignored) {
                    if (d.exists() && !d.delete())
                        Timber.w("Failed to delete invalid module");
                    Toast.makeText(compatActivity,
                            R.string.invalid_format, Toast.LENGTH_SHORT).show();
                }
            } else if (s == IntentHelper.RESPONSE_URL) {
                IntentHelper.openInstaller(compatActivity, u.toString(),
                        compatActivity.getString(
                                R.string.remote_install_title), null, null, false,
                        BuildConfig.DEBUG && // Use debug mode if no root
                                InstallerInitializer.peekMagiskPath() == null);
            }
        });
    }, false) {
        @Override
        public boolean shouldRemove() {
            return !BuildConfig.DEBUG &&
                    (MainApplication.isShowcaseMode() ||
                            InstallerInitializer.peekMagiskPath() == null);
        }
    };

    public static boolean needPatch(File target) {
        try (ZipFile zipFile = new ZipFile(target)) {
            boolean validEntries = zipFile.getEntry("module.prop") != null;
            // ensure there's no anykernel.sh
            validEntries &= zipFile.getEntry("anykernel.sh") == null;
            if (validEntries) {
                // Ensure id of module is not empty and matches ^[a-zA-Z][a-zA-Z0-9._-]+$ regex
                // We need to get the module.prop and parse the id= line
                ZipEntry moduleProp = zipFile.getEntry("module.prop");
                // Parse the module.prop
                if (moduleProp != null) {
                    // Find the line with id=, and check if it matches the regex
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(moduleProp)))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("id=")) {
                                String id = line.substring(3);
                                return id.isEmpty() || !id.matches("^[a-zA-Z][a-zA-Z0-9._-]+$");
                            }
                        }
                    }
                } else {
                    return true;
                }
            } else {
                return true;
            }
        } catch (IOException e) {
            return true;
        }
        return false;
    }

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
        this(textId, iconId, R.attr.colorError, R.attr.colorOnPrimary);
    }

    NotificationType(@StringRes int textId, int iconId, View.OnClickListener onClickListener) {
        this(textId, iconId, R.attr.colorError, R.attr.colorOnPrimary, onClickListener);
    }

    NotificationType(@StringRes int textId, int iconId, int backgroundAttr, int foregroundAttr) {
        this(textId, iconId, backgroundAttr, foregroundAttr, null);
    }

    NotificationType(@StringRes int textId, int iconId, int backgroundAttr, int foregroundAttr,
                     View.OnClickListener onClickListener) {
        this(textId, iconId, backgroundAttr, foregroundAttr, onClickListener, false);
    }

    NotificationType(@StringRes int textId, int iconId, int backgroundAttr, int foregroundAttr,
                     View.OnClickListener onClickListener, @SuppressWarnings("SameParameterValue") boolean special) {
        this.textId = textId;
        this.iconId = iconId;
        this.backgroundAttr = backgroundAttr;
        this.foregroundAttr = foregroundAttr;
        this.onClickListener = onClickListener;
        this.special = special;
    }

    public boolean shouldRemove() {
        // By default, remove the notification`
        return false;
    }

    public final void autoAdd(ModuleViewListBuilder moduleViewListBuilder) {
        if (!shouldRemove()) moduleViewListBuilder.addNotification(this);
    }
}
