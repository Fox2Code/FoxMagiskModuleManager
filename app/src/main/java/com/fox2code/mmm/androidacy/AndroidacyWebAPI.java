package com.fox2code.mmm.androidacy;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.util.TypedValue;
import android.webkit.JavascriptInterface;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.fox2code.foxcompat.view.FoxDisplay;
import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.installer.InstallerInitializer;
import com.fox2code.mmm.manager.LocalModuleInfo;
import com.fox2code.mmm.manager.ModuleInfo;
import com.fox2code.mmm.manager.ModuleManager;
import com.fox2code.mmm.repo.RepoModule;
import com.fox2code.mmm.utils.ExternalHelper;
import com.fox2code.mmm.utils.IntentHelper;
import com.fox2code.mmm.utils.io.Files;
import com.fox2code.mmm.utils.io.Hashes;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import timber.log.Timber;

@SuppressWarnings({"unused", "SameReturnValue"})
@Keep
public class AndroidacyWebAPI {
    public static final int COMPAT_UNSUPPORTED = 0;
    public static final int COMPAT_DOWNLOAD = 1;
    private static final int MAX_COMPAT_MODE = 1;
    private final AndroidacyActivity activity;
    private final boolean allowInstall;
    boolean consumedAction;
    boolean downloadMode;
    int effectiveCompatMode;
    int notifiedCompatMode;

    public AndroidacyWebAPI(AndroidacyActivity activity, boolean allowInstall) {
        this.activity = activity;
        this.allowInstall = allowInstall;
    }

    void forceQuitRaw(String error) {
        Toast.makeText(this.activity, error, Toast.LENGTH_LONG).show();
        this.activity.runOnUiThread(this.activity::forceBackPressed);
        this.activity.backOnResume = true; // Set backOnResume just in case
        this.downloadMode = false;
    }

    void openNativeModuleDialogRaw(String moduleUrl, String moduleId, String installTitle, String checksum, boolean canInstall) {
        if (BuildConfig.DEBUG)
            Timber.d("ModuleDialog, downloadUrl: " + AndroidacyUtil.hideToken(moduleUrl) + ", moduleId: " + moduleId + ", installTitle: " + installTitle + ", checksum: " + checksum + ", canInstall: " + canInstall);
        this.downloadMode = false;
        RepoModule repoModule = AndroidacyRepoData.getInstance().moduleHashMap.get(installTitle);
        String title, description;
        boolean mmtReborn = false;
        if (repoModule != null) {
            title = repoModule.moduleInfo.name;
            description = repoModule.moduleInfo.description;
            mmtReborn = repoModule.moduleInfo.mmtReborn;
            if (description == null || description.length() == 0) {
                description = this.activity.getString(R.string.no_desc_found);
            }
        } else {
            // URL Decode installTitle
            title = installTitle;
            String checkSumType = Hashes.checkSumName(checksum);
            if (checkSumType == null) {
                description = "Checksum: " + ((checksum == null || checksum.isEmpty()) ? "null" : checksum);
            } else {
                description = checkSumType + ": " + checksum;
            }
        }
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this.activity);
        builder.setTitle(title).setMessage(description).setCancelable(true).setIcon(R.drawable.ic_baseline_extension_24);
        builder.setNegativeButton(R.string.download_module, (x, y) -> {
            this.downloadMode = true;
            IntentHelper.openCustomTab(this.activity, moduleUrl);
        });
        if (canInstall) {
            boolean hasUpdate = false;
            String config = null;
            if (repoModule != null) {
                config = repoModule.moduleInfo.config;
                LocalModuleInfo localModuleInfo = ModuleManager.getINSTANCE().getModules().get(repoModule.id);
                hasUpdate = localModuleInfo != null && repoModule.moduleInfo.versionCode > localModuleInfo.versionCode;
            }
            final String fModuleUrl = moduleUrl, fTitle = title, fConfig = config, fChecksum = checksum;
            final boolean fMMTReborn = mmtReborn;
            builder.setPositiveButton(hasUpdate ? R.string.update_module : R.string.install_module, (x, y) -> IntentHelper.openInstaller(this.activity, fModuleUrl, fTitle, fConfig, fChecksum, fMMTReborn));
        }
        builder.setOnCancelListener(dialogInterface -> {
            if (!this.activity.backOnResume)
                this.consumedAction = false;
        });
        ExternalHelper.INSTANCE.injectButton(builder, () -> {
            this.downloadMode = true;
            try {
                return this.activity.downloadFileAsync(moduleUrl);
            } catch (
                    IOException e) {
                Timber.e(e, "Failed to download module");
                AndroidacyWebAPI.this.activity.runOnUiThread(() -> Toast.makeText(AndroidacyWebAPI.this.activity, R.string.failed_download, Toast.LENGTH_SHORT).show());
                return null;
            }
        }, "androidacy_repo");
        final int dim5dp = FoxDisplay.dpToPixel(5);
        builder.setBackgroundInsetStart(dim5dp).setBackgroundInsetEnd(dim5dp);
        this.activity.runOnUiThread(() -> {
            AlertDialog alertDialog = builder.show();
            for (int i = -3; i < 0; i++) {
                Button alertButton = alertDialog.getButton(i);
                if (alertButton != null && alertButton.getPaddingStart() > dim5dp) {
                    alertButton.setPadding(dim5dp, dim5dp, dim5dp, dim5dp);
                }
            }
        });
    }

    void notifyCompatModeRaw(int value) {
        if (this.consumedAction)
            return;
        if (BuildConfig.DEBUG)
            Timber.d("Androidacy Compat mode: %s", value);
        this.notifiedCompatMode = value;
        if (value < 0) {
            value = 0;
        } else if (value > MAX_COMPAT_MODE) {
            value = MAX_COMPAT_MODE;
        }
        this.effectiveCompatMode = value;
    }

    @JavascriptInterface
    public void forceQuit(String error) {
        // Allow forceQuit and cancel in downloadMode
        if (this.consumedAction && !this.downloadMode)
            return;
        this.consumedAction = true;
        this.forceQuitRaw(error);
    }

    @JavascriptInterface
    public void cancel() {
        // Allow forceQuit and cancel in downloadMode
        if (this.consumedAction && !this.downloadMode)
            return;
        this.consumedAction = true;
        this.activity.runOnUiThread(this.activity::forceBackPressed);
    }

    /**
     * Open an url always in an external page or browser.
     */
    @JavascriptInterface
    public void openUrl(String url) {
        if (this.consumedAction)
            return;
        this.consumedAction = true;
        this.downloadMode = false;
        if (BuildConfig.DEBUG)
            Timber.d("Received openUrl request: %s", url);
        if (Uri.parse(url).getScheme().equals("https")) {
            IntentHelper.openUrl(this.activity, url);
        }
    }

    /**
     * Open an url in a custom tab if possible.
     */
    @JavascriptInterface
    public void openCustomTab(String url) {
        if (this.consumedAction)
            return;
        this.consumedAction = true;
        this.downloadMode = false;
        if (BuildConfig.DEBUG)
            Timber.d("Received openCustomTab request: %s", url);
        if (Uri.parse(url).getScheme().equals("https")) {
            IntentHelper.openCustomTab(this.activity, url);
        }
    }

    /**
     * Return if current theme is a light theme.
     */
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
        return this.allowInstall && this.hasRoot() && !MainApplication.isShowcaseMode();
    }

    /**
     * install a module via url, with the file checked with the md5 checksum value.
     */
    @JavascriptInterface
    public void install(String moduleUrl, String installTitle, String checksum) {
        // If compat mode is 0, this means Androidacy didn't implemented a download mode yet
        if (this.consumedAction || (this.effectiveCompatMode >= 1 && !this.canInstall())) {
            return;
        }
        this.consumedAction = true;
        this.downloadMode = false;
        if (BuildConfig.DEBUG)
            Timber.d("Received install request: " + moduleUrl + " " + installTitle + " " + checksum);
        if (!AndroidacyUtil.isAndroidacyLink(moduleUrl)) {
            this.forceQuitRaw("Non Androidacy module link used on Androidacy");
            return;
        }
        checksum = Hashes.checkSumFormat(checksum);
        if (checksum == null || checksum.isEmpty()) {
            Timber.w("Androidacy WebView didn't provided a checksum!");
        } else if (!Hashes.checkSumValid(checksum)) {
            this.forceQuitRaw("Androidacy didn't provided a valid checksum");
            return;
        }
        // moduleId is the module parameter in the url
        String moduleId = AndroidacyUtil.getModuleId(moduleUrl);
        // Let's handle download mode ourself if not implemented
        if (this.effectiveCompatMode < 1) {
            if (!this.canInstall()) {
                this.downloadMode = true;
                this.activity.runOnUiThread(() -> this.activity.webView.loadUrl(moduleUrl));
            } else {
                this.openNativeModuleDialogRaw(moduleUrl, moduleId, installTitle, checksum, true);
            }
        } else {
            RepoModule repoModule = AndroidacyRepoData.getInstance().moduleHashMap.get(installTitle);
            String config = null;
            boolean mmtReborn = false;
            if (repoModule != null && repoModule.moduleInfo.name.length() >= 3) {
                installTitle = repoModule.moduleInfo.name; // Set title to module name
                config = repoModule.moduleInfo.config;
                mmtReborn = repoModule.moduleInfo.mmtReborn;
            }
            this.activity.backOnResume = true;
            IntentHelper.openInstaller(this.activity, moduleUrl, installTitle, config, checksum, mmtReborn);
        }
    }

    /**
     * install a module via url, with the file checked with the md5 checksum value.
     */
    @JavascriptInterface
    public void openNativeModuleDialog(String moduleUrl, String moduleId, String checksum) {
        if (this.consumedAction)
            return;
        this.consumedAction = true;
        this.downloadMode = false;
        if (!AndroidacyUtil.isAndroidacyLink(moduleUrl)) {
            this.forceQuitRaw("Non Androidacy module link used on Androidacy");
            return;
        }
        checksum = Hashes.checkSumFormat(checksum);
        if (checksum == null || checksum.isEmpty()) {
            Timber.w("Androidacy WebView didn't provided a checksum!");
        } else if (!Hashes.checkSumValid(checksum)) {
            this.forceQuitRaw("Androidacy didn't provided a valid checksum");
            return;
        }
        // Get moduleTitle from url
        String moduleTitle = AndroidacyUtil.getModuleTitle(moduleUrl);
        this.openNativeModuleDialogRaw(moduleUrl, moduleId, moduleTitle, checksum, this.canInstall());
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
        if (this.consumedAction)
            return;
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
        if (this.consumedAction)
            return;
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
     * Return true if the module is an Androidacy module.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    @JavascriptInterface
    public boolean isAndroidacyModule(String moduleId) {
        LocalModuleInfo localModuleInfo = ModuleManager.getINSTANCE().getModules().get(moduleId);
        return localModuleInfo != null && ("Androidacy".equals(localModuleInfo.author) || AndroidacyUtil.isAndroidacyLink(localModuleInfo.config));
    }

    /**
     * get a module file, return an empty string if not
     * an Androidacy module or if file doesn't exists.
     */
    @JavascriptInterface
    public String getAndroidacyModuleFile(String moduleId, String moduleFile) {
        if (moduleFile == null || this.consumedAction || !this.isAndroidacyModule(moduleId))
            return "";
        File moduleFolder = new File("/data/adb/modules/" + moduleId);
        File absModuleFile = new File(moduleFolder, moduleFile).getAbsoluteFile();
        if (!absModuleFile.getPath().startsWith(moduleFolder.getPath()))
            return "";
        try {
            return new String(Files.readSU(absModuleFile.getAbsoluteFile()), StandardCharsets.UTF_8);
        } catch (
                IOException e) {
            return "";
        }
    }

    /**
     * Create an ".androidacy" file with {@param content} as content
     * Return true if action succeeded
     */
    @JavascriptInterface
    public boolean setAndroidacyModuleMeta(String moduleId, String content) {
        if (content == null || this.consumedAction || !this.isAndroidacyModule(moduleId))
            return false;
        File androidacyMetaFile = new File("/data/adb/modules/" + moduleId + "/.androidacy");
        try {
            Files.writeSU(androidacyMetaFile, content.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (
                IOException e) {
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
        return InstallerInitializer.peekMagiskPath() == null ? 0 : InstallerInitializer.peekMagiskVersion();
    }

    /**
     * Return current android sdk-int version code, see:
     * <a href="https://source.android.com/setup/start/build-numbers">right here</a>
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

    /**
     * Allow Androidacy backend to notify compat mode
     * return current effective compat mode
     */
    @JavascriptInterface
    public int getEffectiveCompatMode() {
        return this.effectiveCompatMode;
    }

    /**
     * Return current theme accent color
     */
    @JavascriptInterface
    public int getAccentColor() {
        Resources.Theme theme = this.activity.getTheme();
        TypedValue typedValue = new TypedValue();
        theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true);
        if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return typedValue.data;
        }
        theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true);
        return typedValue.data;
    }

    /**
     * Return current theme foreground color
     */
    @JavascriptInterface
    public int getForegroundColor() {
        return this.activity.isLightTheme() ? Color.BLACK : Color.WHITE;
    }

    /**
     * Return current theme background color
     */
    @JavascriptInterface
    public int getBackgroundColor() {
        Resources.Theme theme = this.activity.getTheme();
        TypedValue typedValue = new TypedValue();
        theme.resolveAttribute(com.google.android.material.R.attr.backgroundColor, typedValue, true);
        if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return typedValue.data;
        }
        theme.resolveAttribute(android.R.attr.background, typedValue, true);
        return typedValue.data;
    }

    /**
     * Return current hex string of monet theme
     */
    @JavascriptInterface
    public String getMonetColor(String id) {
        @SuppressLint("DiscouragedApi") int nameResourceID = this.activity.getResources().getIdentifier("@android:color/" + id, "color", this.activity.getApplicationInfo().packageName);
        if (nameResourceID == 0) {
            throw new IllegalArgumentException("No resource string found with name " + id);
        } else {
            int color = ContextCompat.getColor(this.activity, nameResourceID);
            int red = Color.red(color);
            int blue = Color.blue(color);
            int green = Color.green(color);
            return String.format("#%02x%02x%02x", red, green, blue);
        }
    }

    @JavascriptInterface
    public void setAndroidacyToken(String token) {
        AndroidacyRepoData.getInstance().setToken(token);
    }

    // Androidacy feature level declaration method

    @JavascriptInterface
    public void notifyCompatUnsupported() {
        this.notifyCompatModeRaw(COMPAT_UNSUPPORTED);
    }

    @JavascriptInterface
    public void notifyCompatDownloadButton() {
        this.notifyCompatModeRaw(COMPAT_DOWNLOAD);
    }
}
