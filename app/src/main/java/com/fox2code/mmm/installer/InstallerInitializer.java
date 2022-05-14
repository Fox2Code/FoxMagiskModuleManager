package com.fox2code.mmm.installer;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.fox2code.mmm.Constants;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.utils.Files;
import com.topjohnwu.superuser.NoShellException;
import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.util.ArrayList;

public class InstallerInitializer extends Shell.Initializer {
    private static final String TAG = "InstallerInitializer";
    private static final File MAGISK_SYSTEM =
            new File("/system/bin/magisk");
    private static String MAGISK_PATH;
    private static int MAGISK_VERSION_CODE;
    private static boolean HAS_RAMDISK;

    public static final int ERROR_OK = 0;
    public static final int ERROR_NO_PATH = 1;
    public static final int ERROR_NO_SU = 2;
    public static final int ERROR_OTHER = 3;

    public interface Callback {
        void onPathReceived(String path);

        void onFailure(int error);
    }

    public static String peekMagiskPath() {
        return InstallerInitializer.MAGISK_PATH;
    }

    public static String peekMirrorPath() {
        return InstallerInitializer.MAGISK_PATH == null ? null :
                InstallerInitializer.MAGISK_PATH + "/.magisk/mirror";
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
                    Log.w(TAG, "Device don't have root!", e);
                } catch (Throwable e) {
                    error = ERROR_OTHER;
                    Log.e(TAG, "Something happened", e);
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
        Log.d(TAG, "Magisk runtime path: " + MAGISK_PATH);
        MAGISK_VERSION_CODE = Integer.parseInt(output.get(1));
        Log.d(TAG, "Magisk version code: " + MAGISK_VERSION_CODE);
        if (MAGISK_VERSION_CODE >= Constants.MAGISK_VER_CODE_FLAT_MODULES &&
                MAGISK_VERSION_CODE < Constants.MAGISK_VER_CODE_PATH_SUPPORT &&
                (MAGISK_PATH.isEmpty() || !new File(MAGISK_PATH).exists())) {
            MAGISK_PATH = "/sbin";
        }
        if (MAGISK_PATH.length() != 0 && Files.existsSU(new File(MAGISK_PATH))) {
            InstallerInitializer.MAGISK_PATH = MAGISK_PATH;
        } else {
            Log.e(TAG, "Failed to get Magisk path (Got " + MAGISK_PATH + ")");
            MAGISK_PATH = null;
        }
        InstallerInitializer.MAGISK_VERSION_CODE = MAGISK_VERSION_CODE;
        return MAGISK_PATH;
    }

    @Override
    public boolean onInit(@NonNull Context context, @NonNull Shell shell) {
        if (!shell.isRoot())
            return true;
        return shell.newJob().add("export ASH_STANDALONE=1").exec().isSuccess();
    }
}
