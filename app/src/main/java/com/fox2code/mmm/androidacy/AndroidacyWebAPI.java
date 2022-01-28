package com.fox2code.mmm.androidacy;

import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.installer.InstallerInitializer;
import com.fox2code.mmm.manager.ModuleManager;
import com.fox2code.mmm.utils.IntentHelper;

public class AndroidacyWebAPI {
    private final AndroidacyActivity activity;

    public AndroidacyWebAPI(AndroidacyActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void forceQuit(String error) {
        Toast.makeText(this.activity, error, Toast.LENGTH_LONG).show();
        this.activity.forceBackPressed();
    }

    @JavascriptInterface
    public void cancel() {
        this.activity.forceBackPressed();
    }

    @JavascriptInterface
    public void openUrl(String url) {
        if (Uri.parse(url).getScheme().equals("https")) {
            IntentHelper.openUrl(this.activity, url);
        }
    }

    @JavascriptInterface
    public boolean isLightTheme() {
        return MainApplication.getINSTANCE().isLightTheme();
    }

    @JavascriptInterface
    public boolean hasRoot() {
        return InstallerInitializer.peekMagiskPath() != null;
    }

    @JavascriptInterface
    public boolean canInstall() {
        return InstallerInitializer.peekMagiskPath() != null &&
                !MainApplication.isShowcaseMode();
    }

    @JavascriptInterface
    public void install(String moduleUrl, String installTitle) {
        if (MainApplication.isShowcaseMode() ||
                InstallerInitializer.peekMagiskPath() != null) {
            // With lockdown mode enabled or lack of root, install should not have any effect
            return;
        }
        Uri uri = Uri.parse(moduleUrl);
        if (uri.getScheme().equals("https") && uri.getHost().endsWith(".androidacy.com")) {
            IntentHelper.openInstaller(this.activity, moduleUrl, installTitle, null);
        } else {
            this.activity.forceBackPressed();
        }
    }

    @JavascriptInterface
    public boolean isModuleInstalled(String moduleId) {
        return ModuleManager.getINSTANCE().getModules().get(moduleId) != null;
    }
}
