package com.fox2code.mmm;

import android.app.AlertDialog;
import android.content.Context;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.DrawableRes;

import com.fox2code.mmm.compat.CompatActivity;
import com.fox2code.mmm.manager.ModuleInfo;
import com.fox2code.mmm.manager.ModuleManager;
import com.fox2code.mmm.repo.RepoModule;
import com.fox2code.mmm.utils.IntentHelper;

public enum ActionButtonType {
    INFO(R.drawable.ic_baseline_info_24) {
        @Override
        public void doAction(ImageButton button, ModuleHolder moduleHolder) {
            IntentHelper.openMarkdown(button.getContext(),
                    moduleHolder.repoModule.notesUrl,
                    moduleHolder.repoModule.moduleInfo.name,
                    moduleHolder.getMainModuleConfig());
        }

        @Override
        public boolean doActionLong(ImageButton button, ModuleHolder moduleHolder) {
            Context context = button.getContext();
            Toast.makeText(context, context.getString(R.string.module_id_prefix) +
                            moduleHolder.moduleId, Toast.LENGTH_SHORT).show();
            return true;
        }
    },
    UPDATE_INSTALL() {
        @Override
        public void update(ImageButton button, ModuleHolder moduleHolder) {
            int icon = moduleHolder.hasUpdate() ?
                    R.drawable.ic_baseline_update_24 :
                    R.drawable.ic_baseline_system_update_24;
            button.setImageResource(icon);
        }

        @Override
        public void doAction(ImageButton button, ModuleHolder moduleHolder) {
            ModuleInfo moduleInfo = moduleHolder.getMainModuleInfo();
            if (moduleInfo == null) return;
            String updateZipUrl = moduleHolder.getUpdateZipUrl();
            if (updateZipUrl == null) return;
            String updateZipChecksum = moduleHolder.getUpdateZipChecksum();
            IntentHelper.openInstaller(button.getContext(), updateZipUrl,
                    moduleInfo.name, moduleInfo.config, updateZipChecksum);
        }
    },
    UNINSTALL() {
        @Override
        public void update(ImageButton button, ModuleHolder moduleHolder) {
            int icon = moduleHolder.hasFlag(ModuleInfo.FLAG_MODULE_UNINSTALLING) ?
                    R.drawable.ic_baseline_delete_outline_24 : (
                            // We can't trust active flag on first boot
                            MainApplication.isFirstBoot() ||
                    moduleHolder.hasFlag(ModuleInfo.FLAG_MODULE_ACTIVE)) ?
                            R.drawable.ic_baseline_delete_24 :
                            R.drawable.ic_baseline_delete_forever_24;
            button.setImageResource(icon);
        }

        @Override
        public void doAction(ImageButton button, ModuleHolder moduleHolder) {
            if (!ModuleManager.getINSTANCE().setUninstallState(moduleHolder.moduleInfo,
                    !moduleHolder.moduleInfo.hasFlag(ModuleInfo.FLAG_MODULE_UNINSTALLING))) {
                Log.e("ActionButtonType", "Failed to switch uninstalled state!");
            }
            update(button, moduleHolder);
        }

        @Override
        public boolean doActionLong(ImageButton button, ModuleHolder moduleHolder) {
            // We can't trust active flag on first boot
            if (moduleHolder.moduleInfo.hasFlag(ModuleInfo.FLAG_MODULE_ACTIVE)
                    || MainApplication.isFirstBoot()) return false;
            new AlertDialog.Builder(button.getContext()).setTitle(R.string.master_delete)
                    .setPositiveButton(R.string.master_delete_yes, (v, i) -> {
                        if (!ModuleManager.getINSTANCE().masterClear(moduleHolder.moduleInfo)) {
                            Toast.makeText(button.getContext(), R.string.master_delete_fail,
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            moduleHolder.moduleInfo = null;
                            CompatActivity.getCompatActivity(button).refreshUI();
                        }
                    }).setNegativeButton(R.string.master_delete_no, (v, i) -> {}).create().show();
            return true;
        }
    },
    CONFIG(R.drawable.ic_baseline_app_settings_alt_24) {
        @Override
        public void doAction(ImageButton button, ModuleHolder moduleHolder) {
            String config = moduleHolder.getMainModuleConfig();
            if (config == null) return;
            if (config.startsWith("https://www.androidacy.com/")) {
                IntentHelper.openUrlAndroidacy(button.getContext(), config, false);
            } else {
                IntentHelper.openConfig(button.getContext(), config);
            }
        }
    },
    SUPPORT() {
        @Override
        public void update(ImageButton button, ModuleHolder moduleHolder) {
            ModuleInfo moduleInfo = moduleHolder.getMainModuleInfo();
            button.setImageResource(supportIconForUrl(moduleInfo.support));
        }

        @Override
        public void doAction(ImageButton button, ModuleHolder moduleHolder) {
            IntentHelper.openUrl(button.getContext(), moduleHolder.getMainModuleInfo().support);
        }
    },
    DONATE() {
        @Override
        public void update(ImageButton button, ModuleHolder moduleHolder) {
            ModuleInfo moduleInfo = moduleHolder.getMainModuleInfo();
            int icon = R.drawable.ic_baseline_monetization_on_24;
            if (moduleInfo.donate.startsWith("https://www.paypal.me/")) {
                icon = R.drawable.ic_baseline_paypal_24;
            } else if (moduleInfo.donate.startsWith("https://www.patreon.com/")) {
                icon = R.drawable.ic_patreon;
            }
            button.setImageResource(icon);
        }

        @Override
        public void doAction(ImageButton button, ModuleHolder moduleHolder) {
            IntentHelper.openUrl(button.getContext(), moduleHolder.getMainModuleInfo().donate);
        }
    };

    @DrawableRes
    public static int supportIconForUrl(String url) {
        int icon = R.drawable.ic_baseline_support_24;
        if (url.startsWith("https://t.me/")) {
            icon = R.drawable.ic_baseline_telegram_24;
        } else if (url.startsWith("https://discord.gg/") ||
                url.startsWith("https://discord.com/invite/")) {
            icon = R.drawable.ic_baseline_discord_24;
        } else if (url.startsWith("https://github.com/")) {
            icon = R.drawable.ic_github;
        } else if (url.startsWith("https://forum.xda-developers.com/")) {
            icon = R.drawable.ic_xda;
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

    public void update(ImageButton button, ModuleHolder moduleHolder) {
        button.setImageResource(this.iconId);
    }

    public abstract void doAction(ImageButton button, ModuleHolder moduleHolder);

    public boolean doActionLong(ImageButton button, ModuleHolder moduleHolder) {
        return false;
    }
}
