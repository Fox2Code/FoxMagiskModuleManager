package com.fox2code.mmm.androidacy;

import android.net.Uri;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.installer.InstallerInitializer;
import com.fox2code.mmm.manager.LocalModuleInfo;
import com.fox2code.mmm.manager.ModuleInfo;
import com.fox2code.mmm.manager.ModuleManager;
import com.fox2code.mmm.utils.IntentHelper;

public class AndroidacyWebAPI {
    private static final String TAG = "AndroidacyWebAPI";
    private final AndroidacyActivity activity;
    private final boolean allowInstall;

    public AndroidacyWebAPI(AndroidacyActivity activity, boolean allowInstall) {
        this.activity = activity;
        this.allowInstall = allowInstall;
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

    /**
     * Open an url always in an external page or browser.
     */
    @JavascriptInterface
    public void openUrl(String url) {
        Log.d(TAG, "Received openUrl request: " + url);
        if (Uri.parse(url).getScheme().equals("https")) {
            IntentHelper.openUrl(this.activity, url);
        }
    }

    @JavascriptInterface
    public boolean isLightTheme() {
        return MainApplication.getINSTANCE().isLightTheme();
    }

    /**
     * Check if the manager has received root access
     * (Note: hasRoot only return true on Magisk rooted phones)
     */
    @JavascriptInterface
    public boolean hasRoot() {
        return InstallerInitializer.peekMagiskPath() != null;
    }

    /**
     * Check if the install API can be used
     */
    @JavascriptInterface
    public boolean canInstall() {
        return this.allowInstall && this.hasRoot() &&
                !MainApplication.isShowcaseMode();
    }

    /**
     * install a module via url, with the file checked with the md5 checksum value.
     */
    @JavascriptInterface
    public void install(String moduleUrl, String installTitle,String checksum) {
        if (!this.allowInstall || !this.hasRoot() ||
                MainApplication.isShowcaseMode()) {
            // With lockdown mode enabled or lack of root, install should not have any effect
            return;
        }
        Log.d(TAG, "Received install request: " +
                moduleUrl + " " + installTitle + " " + checksum);
        Uri uri = Uri.parse(moduleUrl);
        if (uri.getScheme().equals("https") && uri.getHost().endsWith(".androidacy.com")) {
            IntentHelper.openInstaller(this.activity,
                    moduleUrl, installTitle, null, checksum);
        } else {
            this.activity.forceBackPressed();
        }
    }

    /**
     * Tell if the moduleId is installed on the device
     */
    @JavascriptInterface
    public boolean isModuleInstalled(String moduleId) {
        return ModuleManager.getINSTANCE().getModules().get(moduleId) != null;
    }

    /**
     * Tell if the moduleId is updating and waiting a reboot to update
     */
    @JavascriptInterface
    public boolean isModuleUpdating(String moduleId) {
        LocalModuleInfo localModuleInfo = ModuleManager.getINSTANCE().getModules().get(moduleId);
        return localModuleInfo != null && localModuleInfo.hasFlag(ModuleInfo.FLAG_MODULE_UPDATING);
    }

    /**
     * Return the module version name or null if not installed.
     */
    @JavascriptInterface
    public String getModuleVersion(String moduleId) {
        LocalModuleInfo localModuleInfo = ModuleManager.getINSTANCE().getModules().get(moduleId);
        return localModuleInfo != null ? localModuleInfo.version : null;
    }

    /**
     * Return the module version code or -1 if not installed.
     */
    @JavascriptInterface
    public long getModuleVersionCode(String moduleId) {
        LocalModuleInfo localModuleInfo = ModuleManager.getINSTANCE().getModules().get(moduleId);
        return localModuleInfo != null ? localModuleInfo.updateVersionCode : -1L;
    }

    /**
     * Hide action bar if visible, the action bar is only visible by default on notes.
     */
    @JavascriptInterface
    public void hideActionBar() {
        this.activity.hideActionBar();
    }
}
