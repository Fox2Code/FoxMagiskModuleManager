package com.fox2code.mmm.installer;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.fox2code.mmm.ActionButtonType;
import com.fox2code.mmm.AppUpdateManager;
import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.Constants;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.XHooks;
import com.fox2code.mmm.compat.CompatActivity;
import com.fox2code.mmm.utils.FastException;
import com.fox2code.mmm.utils.Files;
import com.fox2code.mmm.utils.Hashes;
import com.fox2code.mmm.utils.Http;
import com.fox2code.mmm.utils.IntentHelper;
import com.fox2code.mmm.utils.PropUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.topjohnwu.superuser.CallbackList;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.internal.UiThreadHandler;
import com.topjohnwu.superuser.io.SuFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class InstallerActivity extends CompatActivity {
    private static final String TAG = "InstallerActivity";
    public LinearProgressIndicator progressIndicator;
    public ExtendedFloatingActionButton rebootFloatingButton;
    public InstallerTerminal installerTerminal;
    private File moduleCache;
    private File toDelete;
    private boolean textWrap;
    private boolean canceled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        this.moduleCache = new File(this.getCacheDir(), "installer");
        if (!this.moduleCache.exists() && !this.moduleCache.mkdirs())
            Log.e(TAG, "Failed to mkdir module cache dir!");
        super.onCreate(savedInstanceState);
        this.setDisplayHomeAsUpEnabled(true);
        setActionBarBackground(null);
        this.setOnBackPressedCallback(a -> {
            this.canceled = true;
            return false;
        });
        final Intent intent = this.getIntent();
        final String target;
        final String name;
        final String checksum;
        final boolean noExtensions;
        final boolean rootless;
        // Should we allow 3rd part app to install modules?
        if (Constants.INTENT_INSTALL_INTERNAL.equals(intent.getAction())) {
            if (!MainApplication.checkSecret(intent)) {
                Log.e(TAG, "Security check failed!");
                this.forceBackPressed();
                return;
            }
            target = intent.getStringExtra(Constants.EXTRA_INSTALL_PATH);
            name = intent.getStringExtra(Constants.EXTRA_INSTALL_NAME);
            checksum = intent.getStringExtra(Constants.EXTRA_INSTALL_CHECKSUM);
            noExtensions = intent.getBooleanExtra(// Allow intent to disable extensions
                    Constants.EXTRA_INSTALL_NO_EXTENSIONS, false);
            rootless = intent.getBooleanExtra(// For debug only
                    Constants.EXTRA_INSTALL_TEST_ROOTLESS, false);
        } else {
            Toast.makeText(this, "Unknown intent!", Toast.LENGTH_SHORT).show();
            this.forceBackPressed();
            return;
        }
        Log.i(TAG, "Install link: " + target);
        boolean urlMode = target.startsWith("http://") || target.startsWith("https://");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setTitle(name);
        this.textWrap = MainApplication.isTextWrapEnabled();
        setContentView(this.textWrap ?
                R.layout.installer_wrap : R.layout.installer);
        int background;
        int foreground;
        if (MainApplication.getINSTANCE().isLightTheme() &&
                !MainApplication.isForceDarkTerminal()) {
            background = Color.WHITE;
            foreground = Color.BLACK;
        } else {
            background = Color.BLACK;
            foreground = Color.WHITE;
        }
        View horizontalScroller = findViewById(R.id.install_horizontal_scroller);
        RecyclerView installTerminal;
        this.progressIndicator = findViewById(R.id.progress_bar);
        this.rebootFloatingButton = findViewById(R.id.install_terminal_reboot_fab);
        this.installerTerminal = new InstallerTerminal(
                installTerminal = findViewById(R.id.install_terminal), foreground);
        (horizontalScroller != null ? horizontalScroller : installTerminal)
                .setBackground(new ColorDrawable(background));
        this.progressIndicator.setVisibility(View.GONE);
        this.progressIndicator.setIndeterminate(true);
        this.getWindow().setFlags( // Note: Doesn't require WAKELOCK permission
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        this.progressIndicator.setVisibility(View.VISIBLE);
        if (urlMode) this.installerTerminal.addLine("- Downloading " + name);
        new Thread(() -> {
            File moduleCache = this.toDelete = urlMode ?
                    new File(this.moduleCache, "module.zip") : new File(target);
            if (urlMode && moduleCache.exists() && !moduleCache.delete() &&
                    !new SuFile(moduleCache.getAbsolutePath()).delete())
                Log.e(TAG, "Failed to delete module cache");
            String errMessage = "Failed to download module zip";
            try {
                Log.i(TAG, (urlMode ? "Downloading: " : "Loading: ") + target);
                byte[] rawModule = urlMode ? Http.doHttpGet(target, (progress, max, done) -> {
                    if (max <= 0 && this.progressIndicator.isIndeterminate())
                        return;
                    this.runOnUiThread(() -> {
                        this.progressIndicator.setIndeterminate(false);
                        this.progressIndicator.setMax(max);
                        this.progressIndicator.setProgressCompat(progress, true);
                    });
                }) : Files.readSU(moduleCache);
                this.runOnUiThread(() -> {
                    this.progressIndicator.setVisibility(View.GONE);
                    this.progressIndicator.setIndeterminate(true);
                });
                if (this.canceled) return;
                if (checksum != null && !checksum.isEmpty()) {
                    Log.d(TAG, "Checking for checksum: " + checksum);
                    this.runOnUiThread(() -> {
                        this.installerTerminal.addLine("- Checking file integrity");
                    });
                    if (!Hashes.checkSumMatch(rawModule, checksum)) {
                        this.setInstallStateFinished(false,
                                "! File integrity check failed", "");
                        return;
                    }
                }
                if (this.canceled) return;
                Files.fixJavaZipHax(rawModule);
                boolean noPatch = false;
                boolean isModule = false;
                boolean isAnyKernel = false;
                errMessage = "File is not a valid zip file";
                try (ZipInputStream zipInputStream = new ZipInputStream(
                        new ByteArrayInputStream(rawModule))) {
                    ZipEntry zipEntry;
                    while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                        String entryName = zipEntry.getName();
                        if (entryName.equals("anykernel.sh")) {
                            noPatch = true;
                            isAnyKernel = true;
                            break;
                        } else if (entryName.equals("module.prop")) {
                            noPatch = true;
                            isModule = true;
                            break;
                        } else if (entryName.endsWith("/anykernel.sh")) {
                            isAnyKernel = true;
                        } else if (entryName.endsWith("/module.prop")) {
                            isModule = true;
                        }
                    }
                }
                if (!isModule && !isAnyKernel) {
                    this.setInstallStateFinished(false,
                            "! File is not a valid magisk module", "");
                    return;
                }
                if (noPatch) {
                    if (urlMode) {
                        errMessage = "Failed to save module zip";
                        try (OutputStream outputStream = new FileOutputStream(moduleCache)) {
                            outputStream.write(rawModule);
                            outputStream.flush();
                        }
                    }
                } else {
                    errMessage = "Failed to patch module zip";
                    this.runOnUiThread(() -> {
                        this.installerTerminal.addLine("- Patching " + name);
                    });
                    Log.i(TAG, "Patching: " + moduleCache.getName());
                    try (OutputStream outputStream = new FileOutputStream(moduleCache)) {
                        Files.patchModuleSimple(rawModule, outputStream);
                        outputStream.flush();
                    }
                }
                //noinspection UnusedAssignment (Important to avoid OutOfMemoryError)
                rawModule = null; // Because reference is kept when calling doInstall
                if (this.canceled) return;
                this.runOnUiThread(() -> {
                    this.installerTerminal.addLine("- Installing " + name);
                });
                errMessage = "Failed to install module zip";
                this.doInstall(moduleCache, noExtensions, rootless);
            } catch (IOException e) {
                Log.e(TAG, errMessage, e);
                this.setInstallStateFinished(false,
                        "! " + errMessage, "");
            }
        }, "Module install Thread").start();
    }


    private void doInstall(File file, boolean noExtensions, boolean rootless) {
        if (this.canceled) return;
        UiThreadHandler.runAndWait(() -> {
            this.setOnBackPressedCallback(DISABLE_BACK_BUTTON);
            this.setDisplayHomeAsUpEnabled(false);
        });
        Log.i(TAG, "Installing: " + moduleCache.getName());
        InstallerController installerController = new InstallerController(
                this.progressIndicator, this.installerTerminal,
                file.getAbsoluteFile(), noExtensions);
        InstallerMonitor installerMonitor;
        Shell.Job installJob;
        if (rootless) { // rootless is only used for debugging
            File installScript = this.extractInstallScript("module_installer_test.sh");
            if (installScript == null) {
                this.setInstallStateFinished(false,
                        "! Failed to extract test install script", "");
                return;
            }
            installerMonitor = new InstallerMonitor(installScript);
            installJob = Shell.cmd("export MMM_EXT_SUPPORT=1",
                    "cd \"" + this.moduleCache.getAbsolutePath() + "\"",
                    "sh \"" + installScript.getAbsolutePath() + "\"" +
                            " /dev/null 1 \"" + file.getAbsolutePath() + "\"")
                    .to(installerController, installerMonitor);
        } else {
            String arch32 = "true"; // Do nothing by default
            boolean needs32bit = false;
            String moduleId = null;
            boolean anyKernel = false;
            boolean magiskModule = false;
            boolean anyKernelSystemLess = false;
            File anyKernelInstallScript = new File(this.moduleCache, "update-binary");
            try (ZipFile zipFile = new ZipFile(file)) {
                ZipEntry anyKernelSh = zipFile.getEntry("anykernel.sh");
                if (anyKernelSh != null) { // Check if module is AnyKernel module
                    BufferedReader bufferedReader = new BufferedReader(
                            new InputStreamReader(zipFile.getInputStream(anyKernelSh)));
                    String line;
                    // Check if AnyKernel module support system-less
                    while ((line = bufferedReader.readLine()) != null) {
                        String trimmedLine = line.trim();
                        if (trimmedLine.equals("do.modules=1"))
                            anyKernel = true;
                        if (trimmedLine.equals("do.systemless=1"))
                            anyKernelSystemLess = true;
                    }
                    bufferedReader.close();
                    if (anyKernelSystemLess && anyKernel) {
                        anyKernelSystemLess = false;
                        ZipEntry updateBinary = zipFile.getEntry(
                                "META-INF/com/google/android/update-binary");
                        if (updateBinary != null) {
                            bufferedReader = new BufferedReader(
                                    new InputStreamReader(zipFile.getInputStream(updateBinary)));
                            PrintStream printStream = new PrintStream(
                                    new FileOutputStream(anyKernelInstallScript));
                            while ((line = bufferedReader.readLine()) != null) {
                                String trimmedLine = line.trim();
                                if (trimmedLine.equals("mount_all;") ||
                                        trimmedLine.equals("umount_all;"))
                                    continue; // Do not mount anything
                                line = line.replace("/sbin/sh", "/system/bin/sh");
                                int prePatch = line.length();
                                line = line.replace("/data/adb/modules/ak3-helper",
                                        "/data/adb/modules-update/ak3-helper");
                                if (prePatch != line.length()) anyKernelSystemLess = true;
                                printStream.println(line);
                            }
                            printStream.close();
                            bufferedReader.close();
                            if (!anyKernelSystemLess) anyKernelInstallScript.delete();
                        }
                    }
                    anyKernel = true;
                }
                if (zipFile.getEntry( // Check if module hard require 32bit support
                        "common/addon/Volume-Key-Selector/tools/arm64/keycheck") == null &&
                        zipFile.getEntry(
                                "common/addon/Volume-Key-Selector/install.sh") != null) {
                    needs32bit = true;
                }
                ZipEntry moduleProp = zipFile.getEntry("module.prop");
                magiskModule = moduleProp != null;
                moduleId = PropUtils.readModuleId(zipFile
                        .getInputStream(zipFile.getEntry("module.prop")));
            } catch (IOException ignored) {
            }
                        .getInputStream(moduleProp));
            } catch (IOException ignored) {}
            int compatFlags = AppUpdateManager.getFlagsForModule(moduleId);
            if ((compatFlags & AppUpdateManager.FLAG_COMPAT_NEED_32BIT) != 0)
                needs32bit = true;
            if ((compatFlags & AppUpdateManager.FLAG_COMPAT_NO_EXT) != 0)
                noExtensions = true;
            if (moduleId != null && (moduleId.isEmpty() ||
                    moduleId.contains("/") || moduleId.contains("\0") ||
                    (moduleId.startsWith(".") && moduleId.endsWith(".")))) {
                this.setInstallStateFinished(false,
                        "! This module contain a dangerous moduleId",
                        null);
                return;
            }
            if (magiskModule && moduleId == null && !anyKernel) {
                // Modules without module Ids are module installed by 3rd party software
                this.setInstallStateFinished(false,
                        "! Magisk modules require a moduleId", null);
                return;
            }
            if (Build.SUPPORTED_32_BIT_ABIS.length == 0) {
                if (needs32bit) {
                    this.setInstallStateFinished(false,
                            "! This module can't be installed on a 64bit only system",
                            null);
                    return;
                }
            } else if (needs32bit || (compatFlags & AppUpdateManager.FLAG_COMPAT_NO_EXT) == 0) {
                // Restore Magisk legacy stuff for retro compatibility
                if (Build.SUPPORTED_32_BIT_ABIS[0].contains("arm"))
                    arch32 = "export ARCH32=arm";
                if (Build.SUPPORTED_32_BIT_ABIS[0].contains("x86"))
                    arch32 = "export ARCH32=x86";
            }
            String installCommand;
            File installExecutable;
            if (anyKernel && moduleId == null) { // AnyKernel modules don't have a moduleId
                if (!anyKernelSystemLess) {
                    this.setInstallStateFinished(false,
                            "! This AnyKernel module only support recovery install", null);
                    return;
                }
                installExecutable = anyKernelInstallScript;
                installCommand = "sh \"" + installExecutable.getAbsolutePath() + "\"" +
                        " /dev/null 0 \"" + file.getAbsolutePath() + "\"";
            } else if (InstallerInitializer.peekMagiskVersion() >=
                    Constants.MAGISK_VER_CODE_INSTALL_COMMAND &&
                    ((compatFlags & AppUpdateManager.FLAG_COMPAT_MAGISK_CMD) != 0 ||
                            noExtensions || MainApplication.isUsingMagiskCommand())) {
                installCommand = "magisk --install-module \"" + file.getAbsolutePath() + "\"";
                installExecutable = new File(InstallerInitializer.peekMagiskPath()
                        .equals("/sbin") ? "/sbin/magisk" : "/system/bin/magisk");
            } else if (moduleId != null) {
                installExecutable = this.extractInstallScript("module_installer_compat.sh");
                if (installExecutable == null) {
                    this.setInstallStateFinished(false,
                            "! Failed to extract module install script", null);
                    return;
                }
                installCommand = "sh \"" + installExecutable.getAbsolutePath() + "\"" +
                        " /dev/null 0 \"" + file.getAbsolutePath() + "\"";
            } else {
                this.setInstallStateFinished(false,
                        "! Zip file is not a valid Magisk or a AnyKernel module!", null);
                return;
            }
            installerMonitor = new InstallerMonitor(installExecutable);
            if (moduleId != null) installerMonitor.setForCleanUp(moduleId);
            if (noExtensions) {
                installJob = Shell.cmd(arch32, // No Extensions
                        "cd \"" + this.moduleCache.getAbsolutePath() + "\"",
                        installCommand).to(installerController, installerMonitor);
            } else {
                installJob = Shell.cmd(arch32, "export MMM_EXT_SUPPORT=1",
                        "export MMM_USER_LANGUAGE=" + (MainApplication.isForceEnglish() ?
                                "en-US" : Resources.getSystem()
                                .getConfiguration().locale.toLanguageTag()),
                        "export MMM_APP_VERSION=" + BuildConfig.VERSION_NAME,
                        "export MMM_TEXT_WRAP=" + (this.textWrap ? "1" : "0"),
                        "cd \"" + this.moduleCache.getAbsolutePath() + "\"",
                        installCommand).to(installerController, installerMonitor);
            }
        }
        boolean success = installJob.exec().isSuccess();
        // Wait one UI cycle before disabling controller or processing results
        UiThreadHandler.runAndWait(() -> {
        }); // to avoid race conditions
        installerController.disable();
        String message = "- Install successful";
        if (!success) {
            // Workaround busybox-ndk install recognized as failed when successful
            if (this.installerTerminal.getLastLine().trim().equals("Done!")) {
                success = true;
            } else {
                message = installerMonitor.doCleanUp();
            }
        }
        this.setInstallStateFinished(success, message,
                installerController.getSupportLink());
    }

    public static class InstallerController extends CallbackList<String> {
        private final LinearProgressIndicator progressIndicator;
        private final InstallerTerminal terminal;
        private final File moduleFile;
        private final boolean noExtension;
        private boolean enabled, useExt;
        private String supportLink = "";

        private InstallerController(LinearProgressIndicator progressIndicator,
                                    InstallerTerminal terminal, File moduleFile,
                                    boolean noExtension) {
            this.progressIndicator = progressIndicator;
            this.terminal = terminal;
            this.moduleFile = moduleFile;
            this.noExtension = noExtension;
            this.enabled = true;
            this.useExt = false;
        }

        @Override
        public void onAddElement(String s) {
            if (!this.enabled) return;
            Log.d(TAG, "MSG: " + s);
            if ("#!useExt".equals(s.trim()) && !this.noExtension) {
                this.useExt = true;
                return;
            }
            if (this.useExt && s.startsWith("#!")) {
                this.processCommand(s);
            } else {
                this.terminal.addLine(s.replace(
                        this.moduleFile.getAbsolutePath(),
                        this.moduleFile.getName()));
            }
        }

        private void processCommand(String rawCommand) {
            final String arg;
            final String command;
            int i = rawCommand.indexOf(' ');
            if (i != -1) {
                arg = rawCommand.substring(i + 1).trim();
                command = rawCommand.substring(2, i);
            } else {
                arg = "";
                command = rawCommand.substring(2);
            }
            switch (command) {
                case "addLine":
                    this.terminal.addLine(arg);
                    break;
                case "setLastLine":
                    this.terminal.setLastLine(arg);
                    break;
                case "clearTerminal":
                    this.terminal.clearTerminal();
                    break;
                case "scrollUp":
                    this.terminal.scrollUp();
                    break;
                case "scrollDown":
                    this.terminal.scrollDown();
                    break;
                case "showLoading":
                    if (!arg.isEmpty()) {
                        try {
                            short s = Short.parseShort(arg);
                            if (s <= 0) throw FastException.INSTANCE;
                            this.progressIndicator.setMax(s);
                            this.progressIndicator.setIndeterminate(false);
                        } catch (Exception ignored) {
                            this.progressIndicator.setProgressCompat(0, true);
                            this.progressIndicator.setMax(100);
                            if (this.progressIndicator.getVisibility() == View.VISIBLE) {
                                this.progressIndicator.setVisibility(View.GONE);
                            }
                            this.progressIndicator.setIndeterminate(true);
                        }
                    } else if (!rawCommand.trim().equals("#!showLoading")) {
                        this.progressIndicator.setProgressCompat(0, true);
                        this.progressIndicator.setMax(100);
                        if (this.progressIndicator.getVisibility() == View.VISIBLE) {
                            this.progressIndicator.setVisibility(View.GONE);
                        }
                        this.progressIndicator.setIndeterminate(true);
                    }
                    this.progressIndicator.setVisibility(View.VISIBLE);
                    break;
                case "setLoading":
                    try {
                        this.progressIndicator.setProgressCompat(
                                Short.parseShort(arg), true);
                    } catch (Exception ignored) {
                    }
                    break;
                case "hideLoading":
                    this.progressIndicator.setVisibility(View.GONE);
                    break;
                case "setSupportLink":
                    // Only set link if valid
                    if (arg.isEmpty() || (arg.startsWith("https://") &&
                            arg.indexOf('/', 8) > 8))
                        this.supportLink = arg;
                    break;
            }
        }

        public void disable() {
            this.enabled = false;
        }

        public String getSupportLink() {
            return supportLink;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return true;
        return super.dispatchKeyEvent(event);
    }

    public static class InstallerMonitor extends CallbackList<String> {
        private static final String DEFAULT_ERR = "! Install failed";
        private final String installScriptErr;
        public String lastCommand = "";
        public String forCleanUp;

        public InstallerMonitor(File installScript) {
            super(Runnable::run);
            this.installScriptErr =
                    installScript.getAbsolutePath() +
                            ": /data/adb/modules_update/";
        }

        @Override
        public void onAddElement(String s) {
            Log.d(TAG, "Monitor: " + s);
            this.lastCommand = s;
        }

        public void setForCleanUp(String forCleanUp) {
            this.forCleanUp = forCleanUp;
        }

        private String doCleanUp() {
            String installScriptErr = this.installScriptErr;
            // This block is mainly to help fixing customize.sh syntax errors
            if (this.lastCommand.startsWith(installScriptErr)) {
                installScriptErr = this.lastCommand.substring(installScriptErr.length());
                int i = installScriptErr.indexOf('/');
                if (i == -1) return DEFAULT_ERR;
                String module = installScriptErr.substring(0, i);
                SuFile moduleUpdate = new SuFile("/data/adb/modules_update/" + module);
                if (moduleUpdate.exists()) {
                    if (!moduleUpdate.deleteRecursive())
                        Log.e(TAG, "Failed to delete failed update");
                    return "Error: " + installScriptErr.substring(i + 1);
                }
            } else if (this.forCleanUp != null) {
                SuFile moduleUpdate = new SuFile("/data/adb/modules_update/" + this.forCleanUp);
                if (moduleUpdate.exists() && !moduleUpdate.deleteRecursive())
                    Log.e(TAG, "Failed to delete failed update");
            }
            return DEFAULT_ERR;
        }
    }

    private File extractInstallScript(String script) {
        File compatInstallScript = new File(this.moduleCache, script);
        if (!compatInstallScript.exists() || compatInstallScript.length() == 0) {
            try {
                Files.write(compatInstallScript, Files.readAllBytes(
                        this.getAssets().open(script)));
            } catch (IOException e) {
                compatInstallScript.delete();
                Log.e(TAG, "Failed to extract " + script, e);
                return null;
            }
        }
        return compatInstallScript;
    }

    @SuppressWarnings("SameParameterValue")
    private void setInstallStateFinished(boolean success, String message, String optionalLink) {
        if (success && toDelete != null && !toDelete.delete()) {
            SuFile suFile = new SuFile(toDelete.getAbsolutePath());
            if (suFile.exists() && !suFile.delete())
                Log.w(TAG, "Failed to delete zip file");
            else toDelete = null;
        } else toDelete = null;
        this.runOnUiThread(() -> {
            this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, 0);
            this.setOnBackPressedCallback(null);
            this.setDisplayHomeAsUpEnabled(true);
            this.progressIndicator.setVisibility(View.GONE);

            // This should be improved ?
            String reboot_cmd = "/system/bin/svc power reboot || /system/bin/reboot";
            rebootFloatingButton.setOnClickListener(_view -> {
                if (MainApplication.shouldPreventReboot()) {
                    MaterialAlertDialogBuilder builder =
                            new MaterialAlertDialogBuilder(this);

                    builder
                            .setTitle(R.string.install_terminal_reboot_now)
                            .setCancelable(false)
                            .setIcon(R.drawable.ic_reboot_24)
                            .setPositiveButton(R.string.yes, (x, y) -> {
                                Shell.cmd(reboot_cmd).submit();
                            })
                            .setNegativeButton(R.string.no, (x, y) -> {
                                x.dismiss();
                            }).show();
                } else {
                    Shell.cmd(reboot_cmd).submit();
                }
            });
            this.rebootFloatingButton.setVisibility(View.VISIBLE);

            if (message != null && !message.isEmpty())
                this.installerTerminal.addLine(message);
            if (!optionalLink.isEmpty()) {
                this.setActionBarExtraMenuButton(ActionButtonType.supportIconForUrl(optionalLink),
                        menu -> {
                            IntentHelper.openUrl(this, optionalLink);
                            return true;
                        });
            } else if (success) {
                final Intent intent = this.getIntent();
                final String config = MainApplication.checkSecret(intent) ?
                        intent.getStringExtra(Constants.EXTRA_INSTALL_CONFIG) : null;
                if (config != null && !config.isEmpty()) {
                    String configPkg = IntentHelper.getPackageOfConfig(config);
                    try {
                        XHooks.checkConfigTargetExists(this, configPkg, config);
                        this.setActionBarExtraMenuButton(R.drawable.ic_baseline_app_settings_alt_24, menu -> {
                            IntentHelper.openConfig(this, config);
                            return true;
                        });
                    } catch (PackageManager.NameNotFoundException e) {
                        Log.w(TAG, "Config package \"" +
                                configPkg + "\" missing for installer view");
                    }
                }
            }
        });
    }
}
