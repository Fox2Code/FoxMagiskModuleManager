package com.fox2code.mmm.installer;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.fox2code.mmm.ActionButtonType;
import com.fox2code.mmm.Constants;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.compat.CompatActivity;
import com.fox2code.mmm.utils.Files;
import com.fox2code.mmm.utils.Http;
import com.fox2code.mmm.utils.IntentHelper;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.topjohnwu.superuser.CallbackList;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.io.SuFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class InstallerActivity extends CompatActivity {
    private static final String TAG = "InstallerActivity";
    public LinearProgressIndicator progressIndicator;
    public InstallerTerminal installerTerminal;
    private File moduleCache;
    private File toDelete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        this.moduleCache = new File(this.getCacheDir(), "installer");
        if (!this.moduleCache.exists() && !this.moduleCache.mkdirs())
            Log.e(TAG, "Failed to mkdir module cache dir!");
        this.setDisplayHomeAsUpEnabled(false);
        this.setOnBackPressedCallback(DISABLE_BACK_BUTTON);
        super.onCreate(savedInstanceState);
        final Intent intent = this.getIntent();
        final String target;
        final String name;
        // Should we allow 3rd part app to install modules?
        if (Constants.INTENT_INSTALL_INTERNAL.equals(intent.getAction())) {
            if (!MainApplication.checkSecret(intent)) {
                Log.e(TAG, "Security check failed!");
                this.forceBackPressed();
                return;
            }
            target = intent.getExtras().getString(Constants.EXTRA_INSTALL_PATH);
            name = intent.getExtras().getString(Constants.EXTRA_INSTALL_NAME);
        } else {
            Toast.makeText(this, "Unknown intent!", Toast.LENGTH_SHORT).show();
            this.forceBackPressed();
            return;
        }
        boolean urlMode = target.startsWith("http://") || target.startsWith("https://");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setTitle(name);
        setContentView(R.layout.installer);
        this.progressIndicator = findViewById(R.id.progress_bar);
        this.installerTerminal = new InstallerTerminal(findViewById(R.id.install_terminal));
        this.progressIndicator.setVisibility(View.GONE);
        this.progressIndicator.setIndeterminate(true);
        if (urlMode) {
            this.progressIndicator.setVisibility(View.VISIBLE);
            this.installerTerminal.addLine("- Downloading " + name);
            new Thread(() -> {
                File moduleCache = this.toDelete =
                        new File(this.moduleCache, "module.zip");
                if (moduleCache.exists() && !moduleCache.delete() &&
                        !new SuFile(moduleCache.getAbsolutePath()).delete())
                    Log.e(TAG, "Failed to delete module cache");
                try {
                    Log.i(TAG, "Downloading: " + target);
                    byte[] rawModule = Http.doHttpGet(target,(progress, max, done) -> {
                        if (max <= 0 && this.progressIndicator.isIndeterminate())
                            return;
                        this.runOnUiThread(() -> {
                            this.progressIndicator.setIndeterminate(false);
                            this.progressIndicator.setMax(max);
                            this.progressIndicator.setProgressCompat(progress, true);
                        });
                    });
                    this.runOnUiThread(() -> {
                                this.installerTerminal.addLine("- Patching " + name);
                                this.progressIndicator.setVisibility(View.GONE);
                                this.progressIndicator.setIndeterminate(true);
                    });
                    Log.i(TAG, "Patching: " + moduleCache.getName());
                    try (OutputStream outputStream = new FileOutputStream(moduleCache)) {
                        Files.patchModuleSimple(rawModule, outputStream);
                        outputStream.flush();
                    } finally {
                        //noinspection UnusedAssignment (Important for GC)
                        rawModule = null;
                    }
                    this.runOnUiThread(() -> {
                        this.installerTerminal.addLine("- Installing " + name);
                    });
                    this.doInstall(moduleCache);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to download module zip", e);
                    this.setInstallStateFinished(false,
                            "! Failed to download module zip", "");
                }
            }, "Module download Thread").start();
        } else {
            this.installerTerminal.addLine("- Installing " + name);
            new Thread(() -> this.doInstall(
                    this.toDelete = new File(target)),
                    "Install Thread").start();
        }
    }

    private void doInstall(File file) {
        Log.i(TAG, "Installing: " + moduleCache.getName());
        File installScript = this.extractCompatScript();
        if (installScript == null) {
            this.setInstallStateFinished(false,
                    "! Failed to extract module install script", "");
            return;
        }
        InstallerController installerController = new InstallerController(
                this.progressIndicator, this.installerTerminal);
        InstallerMonitor installerMonitor = new InstallerMonitor(installScript);
        boolean success = Shell.su("export MMM_EXT_SUPPORT=1",
                "cd \"" + this.moduleCache.getAbsolutePath() + "\"",
                "sh \"" + installScript.getAbsolutePath() + "\"" +
                        " /dev/null 1 \"" + file.getAbsolutePath() + "\"")
                .to(installerController, installerMonitor).exec().isSuccess();
        installerController.disable();
        String message = "- Install successful";
        if (!success) {
            message = installerMonitor.doCleanUp();
        }
        this.setInstallStateFinished(success, message,
                installerController.getSupportLink());
    }

    public static class InstallerController extends CallbackList<String> {
        private final LinearProgressIndicator progressIndicator;
        private final InstallerTerminal terminal;
        private boolean enabled, useExt;
        private String supportLink = "";

        private InstallerController(LinearProgressIndicator progressIndicator, InstallerTerminal terminal) {
            this.progressIndicator = progressIndicator;
            this.terminal = terminal;
            this.enabled = true;
            this.useExt = false;
        }

        @Override
        public void onAddElement(String s) {
            if (!this.enabled) return;
            Log.d(TAG, "MSG: " + s);
            if ("#!useExt".equals(s)) {
                this.useExt = true;
                return;
            }
            if (this.useExt && s.startsWith("#!")) {
                this.processCommand(s);
            } else {
                this.terminal.addLine(s);
            }
        }

        private void processCommand(String rawCommand) {
            final String arg;
            final String command;
            int i = rawCommand.indexOf(' ');
            if (i != -1) {
                arg = rawCommand.substring(i + 1);
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
                    this.progressIndicator.setVisibility(View.VISIBLE);
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
        private final String installScriptPath;
        public String lastCommand;

        public InstallerMonitor(File installScript) {
            super(Runnable::run);
            this.installScriptPath = installScript.getAbsolutePath();
        }

        @Override
        public void onAddElement(String s) {
            Log.d(TAG, "Monitor: " + s);
            this.lastCommand = s;
        }

        private String doCleanUp() {
            String installScriptErr =
                    this.installScriptPath + ": /data/adb/modules_update/";
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
            }
            return DEFAULT_ERR;
        }
    }

    private static boolean didExtract = false;

    private File extractCompatScript() {
        File compatInstallScript = new File(this.moduleCache, "module_installer_compat.sh");
        if (!compatInstallScript.exists() || compatInstallScript.length() == 0 || !didExtract) {
            try {
                Files.write(compatInstallScript, Files.readAllBytes(
                        this.getAssets().open("module_installer_compat.sh")));
                didExtract = true;
            } catch (IOException e) {
                compatInstallScript.delete();
                Log.e(TAG, "Failed to extract module_installer_compat.sh", e);
                return null;
            }
        }
        return compatInstallScript;
    }

    @SuppressWarnings("SameParameterValue")
    private void setInstallStateFinished(boolean success, String message,String optionalLink) {
        if (success && toDelete != null && !toDelete.delete()) {
            SuFile suFile = new SuFile(toDelete.getAbsolutePath());
            if (suFile.exists() && !suFile.delete())
                Log.w(TAG, "Failed to delete zip file");
            else toDelete = null;
        } else toDelete = null;
        this.runOnUiThread(() -> {
            this.setOnBackPressedCallback(null);
            this.setDisplayHomeAsUpEnabled(true);
            this.progressIndicator.setVisibility(View.GONE);
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
                        this.getPackageManager().getPackageInfo(configPkg, 0);
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
