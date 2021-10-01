package com.fox2code.mmm.manager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.installer.InstallerInitializer;

public class ModuleBootReceive extends BroadcastReceiver {
    private static final String BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !BOOT_COMPLETED.equals(intent.getAction())
                || !MainApplication.hasGottenRootAccess()) {
            return;
        }
        InstallerInitializer.tryGetMagiskPathAsync(new InstallerInitializer.Callback() {
            @Override
            public void onPathReceived(String path) {
                ModuleManager.getINSTANCE().scan();
            }

            @Override
            public void onFailure(int error) {
                MainApplication.setHasGottenRootAccess(false);
            }
        });
    }
}
