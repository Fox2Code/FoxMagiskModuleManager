package com.fox2code.mmm;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.fox2code.mmm.installer.InstallerInitializer;
import com.fox2code.mmm.manager.LocalModuleInfo;
import com.fox2code.mmm.manager.ModuleInfo;
import com.fox2code.mmm.repo.RepoModule;
import com.fox2code.mmm.utils.IntentHelper;
import com.fox2code.mmm.utils.PropUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ModuleHolder implements Comparable<ModuleHolder> {
    private static final String TAG = "ModuleHolder";

    public final String moduleId;
    public final NotificationType notificationType;
    public final Type separator;
    public final int footerPx;
    public LocalModuleInfo moduleInfo;
    public RepoModule repoModule;
    public int filterLevel;

    public ModuleHolder(String moduleId) {
        this.moduleId = Objects.requireNonNull(moduleId);
        this.notificationType = null;
        this.separator = null;
        this.footerPx = 0;
    }

    public ModuleHolder(NotificationType notificationType) {
        this.moduleId = "";
        this.notificationType = Objects.requireNonNull(notificationType);
        this.separator = null;
        this.footerPx = 0;
    }

    public ModuleHolder(Type separator) {
        this.moduleId = "";
        this.notificationType = null;
        this.separator = separator;
        this.footerPx = 0;
    }

    public ModuleHolder(int footerPx) {
        this.moduleId = "";
        this.notificationType = null;
        this.separator = null;
        this.footerPx = footerPx;
    }

    public boolean isModuleHolder() {
        return this.notificationType == null && this.separator == null && this.footerPx == 0;
    }

    public ModuleInfo getMainModuleInfo() {
        return this.repoModule != null && (this.moduleInfo == null ||
                this.moduleInfo.versionCode < this.repoModule.moduleInfo.versionCode)
                ? this.repoModule.moduleInfo : this.moduleInfo;
    }

    public String getUpdateZipUrl() {
        return this.moduleInfo == null || (this.repoModule != null &&
                this.moduleInfo.updateVersionCode < this.repoModule.lastUpdated) ?
                this.repoModule.zipUrl : this.moduleInfo.updateZipUrl;
    }

    public String getMainModuleName() {
        ModuleInfo moduleInfo = this.getMainModuleInfo();
        if (moduleInfo == null || moduleInfo.name == null)
            throw new Error("Error for " + this.getType().name() + " id " + this.moduleId);
        return moduleInfo.name;
    }

    public String getMainModuleConfig() {
        if (this.moduleInfo == null) return null;
        String config = this.moduleInfo.config;
        if (config == null && this.repoModule != null) {
            config = this.repoModule.moduleInfo.config;
        }
        return config;
    }

    public String getUpdateTimeText() {
        if (this.repoModule == null) return "";
        long timeStamp = this.repoModule.lastUpdated;
        return timeStamp <= 0 ? "" :
                MainApplication.formatTime(timeStamp);
    }
    public String getRepoName() {
        if (this.repoModule == null) return "";
        return this.repoModule.repoName;
    }

    public boolean hasFlag(int flag) {
        return this.moduleInfo != null && this.moduleInfo.hasFlag(flag);
    }

    public Type getType() {
        if (this.footerPx != 0) {
            return Type.FOOTER;
        } else if (this.separator != null) {
            return Type.SEPARATOR;
        } else if (this.notificationType != null) {
            return Type.NOTIFICATION;
        } else if (this.moduleInfo == null) {
            return Type.INSTALLABLE;
        } else if (this.moduleInfo.versionCode < this.moduleInfo.updateVersionCode ||
                (this.repoModule != null && this.moduleInfo.versionCode <
                        this.repoModule.moduleInfo.versionCode)) {
            return Type.UPDATABLE;
        } else {
            return Type.INSTALLED;
        }
    }

    public Type getCompareType(Type type) {
        if (this.separator != null) {
            return this.separator;
        } else if (this.notificationType != null &&
                this.notificationType.special) {
            return Type.SPECIAL_NOTIFICATIONS;
        } else {
            return type;
        }
    }

    public boolean shouldRemove() {
        return this.notificationType != null ? this.notificationType.shouldRemove() :
                this.footerPx == 0 && this.moduleInfo == null && (this.repoModule == null ||
                        (PropUtils.isLowQualityModule(this.repoModule.moduleInfo) &&
                                !MainApplication.isDisableLowQualityModuleFilter()));
    }

    public void getButtons(Context context, List<ActionButtonType> buttonTypeList, boolean showcaseMode) {
        if (!this.isModuleHolder()) return;
        if (this.moduleInfo != null && !showcaseMode) {
            buttonTypeList.add(ActionButtonType.UNINSTALL);
        }
        if (this.repoModule != null) {
            buttonTypeList.add(ActionButtonType.INFO);
        }
        if ((this.repoModule != null || (this.moduleInfo != null &&
                this.moduleInfo.updateZipUrl != null)) && !showcaseMode &&
                InstallerInitializer.peekMagiskPath() != null) {
            buttonTypeList.add(ActionButtonType.UPDATE_INSTALL);
        }
        String config = this.getMainModuleConfig();
        if (config != null) {
            String pkg = IntentHelper.getPackageOfConfig(config);
            try {
                context.getPackageManager().getPackageInfo(pkg, 0);
                buttonTypeList.add(ActionButtonType.CONFIG);
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Config package \"" + pkg +
                        "\" missing for module \"" + this.moduleId + "\"");
            }
        }
        ModuleInfo moduleInfo = this.getMainModuleInfo();
        if (moduleInfo.support != null) {
            buttonTypeList.add(ActionButtonType.SUPPORT);
        }
        if (moduleInfo.donate != null) {
            buttonTypeList.add(ActionButtonType.DONATE);
        }
    }

    public boolean hasUpdate() {
        return this.moduleInfo != null && this.repoModule != null &&
                this.moduleInfo.versionCode < this.repoModule.moduleInfo.versionCode;
    }

    @Override
    public int compareTo(ModuleHolder o) {
        // Compare depend on type, also allow type spoofing
        Type selfTypeReal = this.getType();
        Type otherTypeReal = o.getType();
        Type selfType = this.getCompareType(selfTypeReal);
        Type otherType = o.getCompareType(otherTypeReal);
        int compare = selfType.compareTo(otherType);
        return compare != 0 ? compare :
                selfTypeReal == otherTypeReal ?
                        selfTypeReal.compare(this, o) :
                selfTypeReal.compareTo(otherTypeReal);
    }

    public enum Type implements Comparator<ModuleHolder> {
        SEPARATOR(R.string.loading, false) {
            @Override
            @SuppressWarnings("ConstantConditions")
            public int compare(ModuleHolder o1, ModuleHolder o2) {
                return o1.separator.compareTo(o2.separator);
            }
        },
        NOTIFICATION(R.string.loading, true) {
            @Override
            @SuppressWarnings("ConstantConditions")
            public int compare(ModuleHolder o1, ModuleHolder o2) {
                return o1.notificationType.compareTo(o2.notificationType);
            }
        },
        UPDATABLE(R.string.updatable, true) {
            @Override
            public int compare(ModuleHolder o1, ModuleHolder o2) {
                int cmp = Integer.compare(o1.filterLevel, o2.filterLevel);
                if (cmp != 0) return cmp;
                return Long.compare(o2.repoModule.lastUpdated, o1.repoModule.lastUpdated);
            }
        },
        INSTALLED(R.string.installed, true) {
            @Override
            public int compare(ModuleHolder o1, ModuleHolder o2) {
                int cmp = Integer.compare(o1.filterLevel, o2.filterLevel);
                if (cmp != 0) return cmp;
                return o1.getMainModuleName().compareTo(o2.getMainModuleName());
            }
        },
        SPECIAL_NOTIFICATIONS(R.string.loading, true),
        INSTALLABLE(R.string.online_repo, true) {
            @Override
            public int compare(ModuleHolder o1, ModuleHolder o2) {
                int cmp = Integer.compare(o1.filterLevel, o2.filterLevel);
                if (cmp != 0) return cmp;
                return Long.compare(o2.repoModule.lastUpdated, o1.repoModule.lastUpdated);
            }
        },
        FOOTER(R.string.loading, false);

        @StringRes
        public final int title;
        public final boolean hasBackground;

        Type(@StringRes int title, boolean hasBackground) {
            this.title = title;
            this.hasBackground = hasBackground;
        }

        // Note: This method should only be called if both element have the same type
        @Override
        public int compare(ModuleHolder o1, ModuleHolder o2) {
            return 0;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "ModuleHolder{" +
                "moduleId='" + moduleId + '\'' +
                ", notificationType=" + notificationType +
                ", separator=" + separator +
                ", footerPx=" + footerPx +
                '}';
    }
}
