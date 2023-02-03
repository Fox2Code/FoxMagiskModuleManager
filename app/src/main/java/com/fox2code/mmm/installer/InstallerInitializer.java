package com.fox2code.mmm.installer;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fox2code.mmm.Constants;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.NotificationType;
import com.fox2code.mmm.utils.io.Files;
import com.topjohnwu.superuser.NoShellException;
import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.util.ArrayList;

import timber.log.Timber;

public class InstallerInitializer extends Shell.Initializer {
    private static final File MAGISK_SBIN =
            new File("/sbin/magisk");
    private static final File MAGISK_SYSTEM =
            new File("/system/bin/magisk");
    private static final File MAGISK_SYSTEM_EX =
            new File("/system/xbin/magisk");
    private static final boolean HAS_MAGISK = MAGISK_SBIN.exists() ||
            MAGISK_SYSTEM.exists() || MAGISK_SYSTEM_EX.exists();
    private static String MAGISK_PATH;
    private static int MAGISK_VERSION_CODE;
    private static boolean HAS_RAMDISK;

    public static final int ERROR_NO_PATH = 1;
    public static final int ERROR_NO_SU = 2;
    public static final int ERROR_OTHER = 3;

    public interface Callback {
        void onPathReceived(String path);

        void onFailure(int error);
    }

    @Nullable
    public static NotificationType getErrorNotification() {
        Boolean hasRoot = Shell.isAppGrantedRoot();
        if (MAGISK_PATH != null &&
                hasRoot != Boolean.FALSE) {
            return null;
        }
        if (!HAS_MAGISK) {
            return NotificationType.NO_MAGISK;
        } else if (hasRoot != Boolean.TRUE) {
            return NotificationType.ROOT_DENIED;
        }
        return NotificationType.NO_ROOT;
    }

    public static String peekMagiskPath() {
        return InstallerInitializer.MAGISK_PATH;
    }

    public static String peekMirrorPath() {
        return InstallerInitializer.MAGISK_PATH == null ? null :
                InstallerInitializer.MAGISK_PATH + "/.magisk/mirror";
    }

    public static String peekModulesPath() {
        return InstallerInitializer.MAGISK_PATH == null ? null :
                InstallerInitializer.MAGISK_PATH + "/.magisk/modules";
    }

    public static int peekMagiskVersion() {
        return InstallerInitializer.MAGISK_VERSION_CODE;
    }

    public static boolean peekHasRamdisk() {
        return InstallerInitializer.HAS_RAMDISK;
    }

    public static void tryGetMagiskPathAsync(Callback callback) {
        tryGetMagiskPathAsync(callback, false);
    }

    public static void tryGetMagiskPathAsync(Callback callback,boolean forceCheck) {
        final String MAGISK_PATH = InstallerInitializer.MAGISK_PATH;
        Thread thread = new Thread("Magisk GetPath Thread") {
            @Override
            public void run() {
                if (MAGISK_PATH != null && !forceCheck) {
                    callback.onPathReceived(MAGISK_PATH);
                    return;
                }
                int error;
                String MAGISK_PATH = null;
                try {
                    MAGISK_PATH = tryGetMagiskPath(forceCheck);
                    error = ERROR_NO_PATH;
                } catch (NoShellException e) {
                    error = ERROR_NO_SU;
                    Timber.w(e);
                } catch (Exception e) {
                    error = ERROR_OTHER;
                    Timber.e(e);
                }
                if (forceCheck) {
                    InstallerInitializer.MAGISK_PATH = MAGISK_PATH;
                    if (MAGISK_PATH == null) {
                        InstallerInitializer.MAGISK_VERSION_CODE = 0;
                    }
                }
                if (MAGISK_PATH != null) {
                    MainApplication.setHasGottenRootAccess(true);
                    callback.onPathReceived(MAGISK_PATH);
                } else {
                    MainApplication.setHasGottenRootAccess(false);
                    callback.onFailure(error);
                }
            }
        };
        thread.start();
    }

    private static String tryGetMagiskPath(boolean forceCheck) {
        String MAGISK_PATH = InstallerInitializer.MAGISK_PATH;
        int MAGISK_VERSION_CODE;
        boolean HAS_RAMDISK = InstallerInitializer.HAS_RAMDISK;
        if (MAGISK_PATH != null && !forceCheck) return MAGISK_PATH;
        ArrayList<String> output = new ArrayList<>();
        if(!Shell.cmd("if grep ' / ' /proc/mounts | grep -q '/dev/root' &> /dev/null; " +
                        "then echo true; else echo false; fi", "magisk -V", "magisk --path")
                .to(output).exec().isSuccess()) {
            if (output.size() != 0) {
                HAS_RAMDISK = "false".equals(output.get(0)) ||
                        "true".equalsIgnoreCase(System.getProperty("ro.build.ab_update"));
            }
            InstallerInitializer.HAS_RAMDISK = HAS_RAMDISK;
            return null;
        }
        MAGISK_PATH = output.size() < 3 ? "" : output.get(2);
        Timber.i("Magisk runtime path: %s", MAGISK_PATH);
        MAGISK_VERSION_CODE = Integer.parseInt(output.get(1));
        Timber.i("Magisk version code: %s", MAGISK_VERSION_CODE);
        if (MAGISK_VERSION_CODE >= Constants.MAGISK_VER_CODE_FLAT_MODULES &&
                MAGISK_VERSION_CODE < Constants.MAGISK_VER_CODE_PATH_SUPPORT &&
                (MAGISK_PATH.isEmpty() || !new File(MAGISK_PATH).exists())) {
            MAGISK_PATH = "/sbin";
        }
        if (MAGISK_PATH.length() != 0 && Files.existsSU(new File(MAGISK_PATH))) {
            InstallerInitializer.MAGISK_PATH = MAGISK_PATH;
        } else {
            Timber.e("Failed to get Magisk path (Got " + MAGISK_PATH + ")");
            MAGISK_PATH = null;
        }
        InstallerInitializer.MAGISK_VERSION_CODE = MAGISK_VERSION_CODE;
        return MAGISK_PATH;
    }

    @Override
    public boolean onInit(@NonNull Context context, @NonNull Shell shell) {
        if (!shell.isRoot())
            return true;
        // switch to global namespace using the setns syscall
        return shell.newJob().add("export ASH_STANDALONE=1; nsenter -t 1 -m -u /data/adb/magisk/busybox ash").exec().isSuccess();
    }
}
