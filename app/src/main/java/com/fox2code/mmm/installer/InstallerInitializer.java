package com.fox2code.mmm.installer;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.fox2code.mmm.Constants;
import com.fox2code.mmm.MainApplication;
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

    public static int peekMagiskVersion() {
        return InstallerInitializer.MAGISK_VERSION_CODE;
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
        if (MAGISK_PATH != null && !forceCheck) return MAGISK_PATH;
        ArrayList<String> output = new ArrayList<>();
        if(!Shell.su( "magisk -V", "magisk --path").to(output).exec().isSuccess()) {
            return null;
        }
        MAGISK_PATH = output.size() < 2 ? "" : output.get(1);
        MAGISK_VERSION_CODE = Integer.parseInt(output.get(0));
        if (MAGISK_VERSION_CODE >= Constants.MAGISK_VER_CODE_FLAT_MODULES &&
                MAGISK_VERSION_CODE < Constants.MAGISK_VER_CODE_PATH_SUPPORT &&
                (MAGISK_PATH.isEmpty() || !new File(MAGISK_PATH).exists())) {
            MAGISK_PATH = "/sbin";
        }
        if (MAGISK_PATH.length() != 0 && new File(MAGISK_PATH).exists()) {
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
        Shell.Job newJob = shell.newJob();
        String MAGISK_PATH = InstallerInitializer.MAGISK_PATH;
        if (MAGISK_PATH == null) {
            Log.w(TAG, "Unable to detect magisk path!");
        } else {
            newJob.add("export ASH_STANDALONE=1");
            if (!MAGISK_PATH.equals("/sbin") && !MAGISK_SYSTEM.exists()) {
                newJob.add("export PATH=" + MAGISK_PATH + ";$PATH;" +
                        MAGISK_PATH + "/.magisk/busybox");
            } else {
                newJob.add("export PATH=$PATH;" +
                        MAGISK_PATH + "/.magisk/busybox");
            }
            newJob.add("export MAGISKTMP=\"" + MAGISK_PATH + "/.magisk\"");
            newJob.add("$(which busybox 2> /dev/null) sh");
        }
        return true;
    }
}
