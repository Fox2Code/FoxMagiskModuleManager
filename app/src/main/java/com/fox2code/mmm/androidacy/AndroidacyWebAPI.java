package com.fox2code.mmm.androidacy;

import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.installer.InstallerInitializer;
import com.fox2code.mmm.manager.LocalModuleInfo;
import com.fox2code.mmm.manager.ModuleInfo;
import com.fox2code.mmm.manager.ModuleManager;
import com.fox2code.mmm.utils.Files;
import com.fox2code.mmm.utils.Hashes;
import com.fox2code.mmm.utils.IntentHelper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class AndroidacyWebAPI {
    private static final String TAG = "AndroidacyWebAPI";
    private final AndroidacyActivity activity;
    private final boolean allowInstall;
    boolean consumedAction;

    public AndroidacyWebAPI(AndroidacyActivity activity, boolean allowInstall) {
        this.activity = activity;
        this.allowInstall = allowInstall;
    }

    public void forceQuitRaw(String error) {
        Toast.makeText(this.activity, error, Toast.LENGTH_LONG).show();
        this.activity.runOnUiThread(this.activity::forceBackPressed);
        this.activity.backOnResume = true; // Set backOnResume just in case
    }

    @JavascriptInterface
    public void forceQuit(String error) {
        if (this.consumedAction) return;
        this.consumedAction = true;
        this.forceQuitRaw(error);
    }

    @JavascriptInterface
    public void cancel() {
        if (this.consumedAction) return;
        this.consumedAction = true;
        this.activity.runOnUiThread(
                this.activity::forceBackPressed);
    }

    /**
     * Open an url always in an external page or browser.
     */
    @JavascriptInterface
    public void openUrl(String url) {
        if (this.consumedAction) return;
        this.consumedAction = true;
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
        // With lockdown mode enabled or lack of root, install should not have any effect
        return this.allowInstall && this.hasRoot() &&
                !MainApplication.isShowcaseMode();
    }

    /**
     * install a module via url, with the file checked with the md5 checksum value.
     */
    @JavascriptInterface
    public void install(String moduleUrl, String installTitle,String checksum) {
        if (this.consumedAction || !this.canInstall()) {
            return;
        }
        this.consumedAction = true;
        Log.d(TAG, "Received install request: " +
                moduleUrl + " " + installTitle + " " + checksum);
        Uri uri = Uri.parse(moduleUrl);
        if (!AndroidacyUtil.isAndroidacyLink(moduleUrl, uri)) {
            this.forceQuitRaw("Non Androidacy module link used on Androidacy");
            return;
        }
        if (checksum != null) checksum = checksum.trim();
        if (!Hashes.checkSumValid(checksum)) {
            this.forceQuitRaw("Androidacy didn't provided a valid checksum");
            return;
        }
        this.activity.backOnResume = true;
        IntentHelper.openInstaller(this.activity,
                moduleUrl, installTitle, null, checksum);
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
        return localModuleInfo != null ? localModuleInfo.versionCode : -1L;
    }

    /**
     * Hide action bar if visible, the action bar is only visible by default on notes.
     */
    @JavascriptInterface
    public void hideActionBar() {
        if (this.consumedAction) return;
        this.consumedAction = true;
        this.activity.runOnUiThread(() -> {
            this.activity.hideActionBar();
            this.consumedAction = false;
        });
    }

    /**
     * Show action bar if not visible, the action bar is only visible by default on notes.
     * Optional title param to set action bar title.
     */
    @JavascriptInterface
    public void showActionBar(final String title) {
        if (this.consumedAction) return;
        this.consumedAction = true;
        this.activity.runOnUiThread(() -> {
            this.activity.showActionBar();
            if (title != null && !title.isEmpty()) {
                this.activity.setTitle(title);
            }
            this.consumedAction = false;
        });
    }

    /**
     * Return true if the module is an Andoridacy module.
     */
    @JavascriptInterface
    public boolean isAndroidacyModule(String moduleId) {
        LocalModuleInfo localModuleInfo = ModuleManager.getINSTANCE().getModules().get(moduleId);
        return localModuleInfo != null && ("Androidacy".equals(localModuleInfo.author) ||
                AndroidacyUtil.isAndroidacyLink(localModuleInfo.config));
    }

    /**
     * get a module file, return an empty string if not
     * an Androidacy module or if file doesn't exists.
     */
    @JavascriptInterface
    public String getAndroidacyModuleFile(String moduleId, String moduleFile) {
        if (moduleFile == null || this.consumedAction ||
                !this.isAndroidacyModule(moduleId)) return "";
        File moduleFolder = new File("/data/adb/modules/" + moduleId);
        File absModuleFile = new File(moduleFolder, moduleFile).getAbsoluteFile();
        if (!absModuleFile.getPath().startsWith(moduleFolder.getPath())) return "";
        try {
            return new String(Files.readSU(absModuleFile
                    .getAbsoluteFile()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Create an ".androidacy" file with {@param content} as content
     * Return true if action succeeded
     */
    @JavascriptInterface
    public boolean setAndroidacyModuleMeta(String moduleId, String content) {
        if (content == null || this.consumedAction ||
                !this.isAndroidacyModule(moduleId)) return false;
        File androidacyMetaFile = new File(
                "/data/adb/modules/" + moduleId + "/.androidacy");
        try {
            Files.writeSU(androidacyMetaFile,
                    content.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Return current app version code
     */
    @JavascriptInterface
    public int getAppVersionCode() {
        return BuildConfig.VERSION_CODE;
    }

    /**
     * Return current app version name
     */
    @JavascriptInterface
    public String getAppVersionName() {
        return BuildConfig.VERSION_NAME;
    }

    /**
     * Return current magisk version code or 0 if not applicable
     */
    @JavascriptInterface
    public int getMagiskVersionCode() {
        return InstallerInitializer.peekMagiskPath() == null ? 0 :
                InstallerInitializer.peekMagiskVersion();
    }

    /**
     * Return current android sdk-int version code, see:
     * https://source.android.com/setup/start/build-numbers
     */
    @JavascriptInterface
    public int getAndroidVersionCode() {
        return Build.VERSION.SDK_INT;
    }

    /**
     * Return current navigation bar height or 0 if not visible
     */
    @JavascriptInterface
    public int getNavigationBarHeight() {
        return this.activity.getNavigationBarHeight();
    }
}
