package com.fox2code.mmm.module;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.text.Spanned;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.appcompat.app.AlertDialog;

import com.fox2code.foxcompat.FoxActivity;
import com.fox2code.foxcompat.FoxDisplay;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.androidacy.AndroidacyUtil;
import com.fox2code.mmm.installer.InstallerInitializer;
import com.fox2code.mmm.manager.LocalModuleInfo;
import com.fox2code.mmm.manager.ModuleInfo;
import com.fox2code.mmm.manager.ModuleManager;
import com.fox2code.mmm.utils.ExternalHelper;
import com.fox2code.mmm.utils.IntentHelper;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.noties.markwon.Markwon;

@SuppressLint("UseCompatLoadingForDrawables")
public enum ActionButtonType {
    INFO() {
        @Override
        public void update(Chip button, ModuleHolder moduleHolder) {
            button.setChipIcon(button.getContext().getDrawable(R.drawable.ic_baseline_info_24));
            button.setText(R.string.description);
        }

        @Override
        public void doAction(Chip button, ModuleHolder moduleHolder) {
            String notesUrl = moduleHolder.repoModule.notesUrl;
            if (AndroidacyUtil.isAndroidacyLink(notesUrl)) {
                IntentHelper.openUrlAndroidacy(button.getContext(), notesUrl, false,
                        moduleHolder.repoModule.moduleInfo.name,
                        moduleHolder.getMainModuleConfig());
            } else {
                IntentHelper.openMarkdown(button.getContext(), notesUrl,
                        moduleHolder.repoModule.moduleInfo.name,
                        moduleHolder.getMainModuleConfig(),
                        moduleHolder.repoModule.moduleInfo.changeBoot,
                        moduleHolder.repoModule.moduleInfo.needRamdisk,
                        moduleHolder.repoModule.moduleInfo.minMagisk,
                        moduleHolder.repoModule.moduleInfo.minApi,
                        moduleHolder.repoModule.moduleInfo.maxApi
                );
            }
        }

        @Override
        public boolean doActionLong(Chip button, ModuleHolder moduleHolder) {
            Context context = button.getContext();
            Toast.makeText(context, context.getString(R.string.module_id_prefix) +
                    moduleHolder.moduleId, Toast.LENGTH_SHORT).show();
            return true;
        }
    },
    UPDATE_INSTALL() {
        @Override
        public void update(Chip button, ModuleHolder moduleHolder) {
            int icon = moduleHolder.hasUpdate() ?
                    R.drawable.ic_baseline_update_24 :
                    R.drawable.ic_baseline_system_update_24;
            button.setChipIcon(button.getContext().getDrawable(icon));
            if (moduleHolder.hasUpdate()) {
                button.setText(R.string.update);
            } else {
                button.setText(R.string.install);
            }
        }

        @Override
        public void doAction(Chip button, ModuleHolder moduleHolder) {
            ModuleInfo moduleInfo = moduleHolder.getMainModuleInfo();
            if (moduleInfo == null) return;
            String updateZipUrl = moduleHolder.getUpdateZipUrl();
            if (updateZipUrl == null) return;
            // Androidacy manage the selection between download and install
            if (AndroidacyUtil.isAndroidacyLink(updateZipUrl)) {
                IntentHelper.openUrlAndroidacy(
                        button.getContext(), updateZipUrl, true,
                        moduleInfo.name, moduleInfo.config);
                return;
            }
            boolean hasRoot = InstallerInitializer.peekMagiskPath() != null
                    && !MainApplication.isShowcaseMode();
            MaterialAlertDialogBuilder builder =
                    new MaterialAlertDialogBuilder(button.getContext());
            builder.setTitle(moduleInfo.name).setCancelable(true)
                    .setIcon(R.drawable.ic_baseline_extension_24);
            CharSequence desc = moduleInfo.description;
            Markwon markwon = null;
            LocalModuleInfo localModuleInfo = moduleHolder.moduleInfo;
            if (localModuleInfo != null && !localModuleInfo.updateChangeLog.isEmpty()) {
                markwon = MainApplication.getINSTANCE().getMarkwon();
                // Re-render each time in cse of config changes
                desc = markwon.toMarkdown(localModuleInfo.updateChangeLog);
            }

            if (desc == null || desc.length() == 0) {
                builder.setMessage(R.string.no_desc_found);
            } else {
                builder.setMessage(desc);
            }
            Log.i("Test", "URL: " + updateZipUrl);
            builder.setNegativeButton(R.string.download_module, (x, y) -> {
                IntentHelper.openCustomTab(button.getContext(), updateZipUrl);
            });
            if (hasRoot) {
                builder.setPositiveButton(moduleHolder.hasUpdate() ?
                        R.string.update_module : R.string.install_module, (x, y) -> {
                    String updateZipChecksum = moduleHolder.getUpdateZipChecksum();
                    IntentHelper.openInstaller(button.getContext(), updateZipUrl,
                            moduleInfo.name, moduleInfo.config, updateZipChecksum,
                            moduleInfo.mmtReborn);
                });
            }
            ExternalHelper.INSTANCE.injectButton(builder,
                    () -> Uri.parse(updateZipUrl), moduleHolder.getUpdateZipRepo());
            int dim5dp = FoxDisplay.dpToPixel(5);
            builder.setBackgroundInsetStart(dim5dp).setBackgroundInsetEnd(dim5dp);
            AlertDialog alertDialog = builder.show();
            for (int i = -3; i < 0; i++) {
                Button alertButton = alertDialog.getButton(i);
                if (alertButton != null && alertButton.getPaddingStart() > dim5dp) {
                    alertButton.setPadding(dim5dp, dim5dp, dim5dp, dim5dp);
                }
            }
            if (markwon != null) {
                TextView messageView = alertDialog.getWindow()
                        .findViewById(android.R.id.message);
                markwon.setParsedMarkdown(messageView, (Spanned) desc);
            }
        }
    },
    UNINSTALL() {
        @Override
        public void update(Chip button, ModuleHolder moduleHolder) {
            int icon = moduleHolder.hasFlag(ModuleInfo.FLAG_MODULE_UNINSTALLING) ?
                    R.drawable.ic_baseline_delete_outline_24 : (
                    !moduleHolder.hasFlag(ModuleInfo.FLAG_MODULE_UPDATING) ||
                            moduleHolder.hasFlag(ModuleInfo.FLAGS_MODULE_ACTIVE)) ?
                    R.drawable.ic_baseline_delete_24 :
                    R.drawable.ic_baseline_delete_forever_24;
            button.setChipIcon(button.getContext().getDrawable(icon));
            button.setText(R.string.uninstall);
        }

        @Override
        public void doAction(Chip button, ModuleHolder moduleHolder) {
            if (!moduleHolder.hasFlag(ModuleInfo.FLAGS_MODULE_ACTIVE |
                    ModuleInfo.FLAG_MODULE_UNINSTALLING) &&
                    moduleHolder.hasFlag(ModuleInfo.FLAG_MODULE_UPDATING)) {
                doActionLong(button, moduleHolder);
                return;
            }
            Log.i("ActionButtonType", Integer.toHexString(moduleHolder.moduleInfo.flags));
            if (!ModuleManager.getINSTANCE().setUninstallState(moduleHolder.moduleInfo,
                    !moduleHolder.hasFlag(ModuleInfo.FLAG_MODULE_UNINSTALLING))) {
                Log.e("ActionButtonType", "Failed to switch uninstalled state!");
            }
            update(button, moduleHolder);
        }

        @Override
        public boolean doActionLong(Chip button, ModuleHolder moduleHolder) {
            // Actually a module having mount is the only issue when deleting module
            if (moduleHolder.moduleInfo.hasFlag(ModuleInfo.FLAG_MODULE_HAS_ACTIVE_MOUNT))
                return false; // We can't trust active flag on first boot
            new AlertDialog.Builder(button.getContext()).setTitle(R.string.master_delete)
                    .setPositiveButton(R.string.master_delete_yes, (v, i) -> {
                        String moduleId = moduleHolder.moduleInfo.id;
                        if (!ModuleManager.getINSTANCE().masterClear(moduleHolder.moduleInfo)) {
                            Toast.makeText(button.getContext(), R.string.master_delete_fail,
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            moduleHolder.moduleInfo = null;
                            FoxActivity.getFoxActivity(button).refreshUI();
                            Log.e("ActionButtonType", "Cleared: " + moduleId);
                        }
                    }).setNegativeButton(R.string.master_delete_no, (v, i) -> {
                    }).create().show();
            return true;
        }
    },
    CONFIG() {
        @Override
        public void update(Chip button, ModuleHolder moduleHolder) {
            button.setChipIcon(button.getContext().getDrawable(R.drawable.ic_baseline_app_settings_alt_24));
            button.setText(R.string.config);
        }

        @Override
        public void doAction(Chip button, ModuleHolder moduleHolder) {
            String config = moduleHolder.getMainModuleConfig();
            if (config == null) return;
            if (AndroidacyUtil.isAndroidacyLink(config)) {
                IntentHelper.openUrlAndroidacy(button.getContext(), config, true);
            } else {
                IntentHelper.openConfig(button.getContext(), config);
            }
        }
    },
    SUPPORT() {
        @Override
        public void update(Chip button, ModuleHolder moduleHolder) {
            ModuleInfo moduleInfo = moduleHolder.getMainModuleInfo();
            button.setChipIcon(button.getContext().getDrawable(
                    supportIconForUrl(moduleInfo.support)));
            button.setText(R.string.support);
        }

        @Override
        public void doAction(Chip button, ModuleHolder moduleHolder) {
            IntentHelper.openUrl(button.getContext(),
                    moduleHolder.getMainModuleInfo().support);
        }
    },
    DONATE() {
        @Override
        public void update(Chip button, ModuleHolder moduleHolder) {
            ModuleInfo moduleInfo = moduleHolder.getMainModuleInfo();
            button.setChipIcon(button.getContext().getDrawable(
                    donateIconForUrl(moduleInfo.donate)));
            button.setText(R.string.donate);
        }

        @Override
        public void doAction(Chip button, ModuleHolder moduleHolder) {
            IntentHelper.openUrl(button.getContext(), moduleHolder.getMainModuleInfo().donate);
        }
    };

    @DrawableRes
    public static int supportIconForUrl(String url) {
        int icon = R.drawable.ic_baseline_support_24;
        if (url == null) {
            return icon;
        } else if (url.startsWith("https://t.me/")) {
            icon = R.drawable.ic_baseline_telegram_24;
        } else if (url.startsWith("https://discord.gg/") ||
                url.startsWith("https://discord.com/invite/")) {
            icon = R.drawable.ic_baseline_discord_24;
        } else if (url.startsWith("https://github.com/")) {
            icon = R.drawable.ic_github;
        } else if (url.startsWith("https://gitlab.com/")) {
            icon = R.drawable.ic_gitlab;
        } else if (url.startsWith("https://forum.xda-developers.com/")) {
            icon = R.drawable.ic_xda;
        }
        return icon;
    }

    @DrawableRes
    public static int donateIconForUrl(String url) {
        int icon = R.drawable.ic_baseline_monetization_on_24;
        if (url == null) {
            return icon;
        } else if (url.startsWith("https://www.paypal.me/") ||
                url.startsWith("https://www.paypal.com/paypalme/") ||
                url.startsWith("https://www.paypal.com/donate/")) {
            icon = R.drawable.ic_baseline_paypal_24;
        } else if (url.startsWith("https://patreon.com/") ||
                url.startsWith("https://www.patreon.com/")) {
            icon = R.drawable.ic_patreon;
        }
        return icon;
    }

    @DrawableRes
    private final int iconId;

    ActionButtonType() {
        this.iconId = 0;
    }

    ActionButtonType(int iconId) {
        this.iconId = iconId;
    }

    public void update(Chip button, ModuleHolder moduleHolder) {
        button.setChipIcon(button.getContext().getDrawable(this.iconId));
    }

    public abstract void doAction(Chip button, ModuleHolder moduleHolder);

    public boolean doActionLong(Chip button, ModuleHolder moduleHolder) {
        return false;
    }
}
