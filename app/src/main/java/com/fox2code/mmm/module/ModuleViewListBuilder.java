package com.fox2code.mmm.module;

import android.app.Activity;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fox2code.mmm.AppUpdateManager;
import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.NotificationType;
import com.fox2code.mmm.installer.InstallerInitializer;
import com.fox2code.mmm.manager.LocalModuleInfo;
import com.fox2code.mmm.manager.ModuleInfo;
import com.fox2code.mmm.manager.ModuleManager;
import com.fox2code.mmm.repo.RepoManager;
import com.fox2code.mmm.repo.RepoModule;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

import timber.log.Timber;

public class ModuleViewListBuilder {
    private static final Runnable RUNNABLE = () -> {};
    private final EnumSet<NotificationType> notifications = EnumSet.noneOf(NotificationType.class);
    private final HashMap<String, ModuleHolder> mappedModuleHolders = new HashMap<>();
    private final Object updateLock = new Object();
    private final Object queryLock = new Object();
    private final Activity activity;
    @NonNull
    private String query = "";
    private boolean updating;
    private int headerPx;
    private int footerPx;
    private ModuleSorter moduleSorter = ModuleSorter.UPDATE;
    private Runnable updateInsets = RUNNABLE;

    public ModuleViewListBuilder(Activity activity) {
        this.activity = activity;
    }

    private static void notifySizeChanged(ModuleViewAdapter moduleViewAdapter,
                                          int index, int oldLen, int newLen) {
        // Timber.i("A: " + index + " " + oldLen + " " + newLen);
        if (oldLen == newLen) {
            if (newLen != 0)
                moduleViewAdapter.notifyItemRangeChanged(index, newLen);
        } else if (oldLen < newLen) {
            if (oldLen != 0)
                moduleViewAdapter.notifyItemRangeChanged(index, oldLen);
            moduleViewAdapter.notifyItemRangeInserted(
                    index + oldLen, newLen - oldLen);
        } else {
            if (newLen != 0)
                moduleViewAdapter.notifyItemRangeChanged(index, newLen);
            moduleViewAdapter.notifyItemRangeRemoved(
                    index + newLen, oldLen - newLen);
        }
    }

    public void addNotification(NotificationType notificationType) {
        if (notificationType == null) {
            Timber.w("addNotification(null) called!");
            return;
        } else {
            Timber.i("addNotification(%s) called", notificationType);
        }
        synchronized (this.updateLock) {
            this.notifications.add(notificationType);
        }
    }

    public void appendInstalledModules() {
        synchronized (this.updateLock) {
            for (ModuleHolder moduleHolder : this.mappedModuleHolders.values()) {
                moduleHolder.moduleInfo = null;
            }
            ModuleManager moduleManager = ModuleManager.getINSTANCE();
            moduleManager.runAfterScan(() -> {
                Timber.i("A1: %s", moduleManager.getModules().size());
                for (LocalModuleInfo moduleInfo : moduleManager.getModules().values()) {
                    ModuleHolder moduleHolder = this.mappedModuleHolders.get(moduleInfo.id);
                    if (moduleHolder == null) {
                        this.mappedModuleHolders.put(moduleInfo.id,
                                moduleHolder = new ModuleHolder(moduleInfo.id));
                    }
                    moduleHolder.moduleInfo = moduleInfo;
                }
            });
        }
    }

    public void appendRemoteModules() {
        if (BuildConfig.DEBUG) {
            Timber.i("appendRemoteModules() called");
        }
        synchronized (this.updateLock) {
            boolean showIncompatible = MainApplication.isShowIncompatibleModules();
            for (ModuleHolder moduleHolder : this.mappedModuleHolders.values()) {
                moduleHolder.repoModule = null;
            }
            RepoManager repoManager = RepoManager.getINSTANCE();
            repoManager.runAfterUpdate(() -> {
                Timber.i("A2: %s", repoManager.getModules().size());
                boolean no32bitSupport = Build.SUPPORTED_32_BIT_ABIS.length == 0;
                for (RepoModule repoModule : repoManager.getModules().values()) {
                    // if repoData is null, something is wrong
                    if (repoModule.repoData == null) {
                        Timber.w("RepoData is null for module %s", repoModule.id);
                        continue;
                    }
                    if (!repoModule.repoData.isEnabled()) {
                        Timber.i("Repo %s is disabled, skipping module %s", repoModule.repoData.id, repoModule.id);
                        continue;
                    }
                    ModuleInfo moduleInfo = repoModule.moduleInfo;
                    if (!showIncompatible && (moduleInfo.minApi > Build.VERSION.SDK_INT ||
                            (moduleInfo.maxApi != 0 && moduleInfo.maxApi < Build.VERSION.SDK_INT) ||
                            // Only check Magisk compatibility if root is present
                            (InstallerInitializer.peekMagiskPath() != null &&
                                    repoModule.moduleInfo.minMagisk >
                                            InstallerInitializer.peekMagiskVersion())) ||
                            // If 64bit only system, skip 32bit only modules
                            (no32bitSupport && (AppUpdateManager.getFlagsForModule(repoModule.id)
                                    & AppUpdateManager.FLAG_COMPAT_NEED_32BIT) != 0) ||
                            // If module need ramdisk but boot doesn't have one
                            (repoModule.moduleInfo.needRamdisk &&
                                    !InstallerInitializer.peekHasRamdisk())
                    ) continue; // Skip adding incompatible modules
                    ModuleHolder moduleHolder = this.mappedModuleHolders.get(repoModule.id);
                    if (moduleHolder == null) {
                        this.mappedModuleHolders.put(repoModule.id,
                                moduleHolder = new ModuleHolder(repoModule.id));
                    }
                    moduleHolder.repoModule = repoModule;
                }
            });
        }
    }

    public void refreshNotificationsUI(ModuleViewAdapter moduleViewAdapter) {
        final int notificationCount = this.notifications.size();
        notifySizeChanged(moduleViewAdapter, 0,
                notificationCount, notificationCount);
    }

    private boolean matchFilter(ModuleHolder moduleHolder) {
        ModuleInfo moduleInfo = moduleHolder.getMainModuleInfo();
        String query = this.query;
        String idLw = moduleInfo.id.toLowerCase(Locale.ROOT);
        String nameLw = moduleInfo.name.toLowerCase(Locale.ROOT);
        String authorLw = moduleInfo.author == null ? "" :
                moduleInfo.author.toLowerCase(Locale.ROOT);
        if (query.isEmpty() || query.equals(idLw) ||
                query.equals(nameLw) || query.equals(authorLw)) {
            moduleHolder.filterLevel = 0; // Lower = better
            return true;
        }
        if (idLw.contains(query) || nameLw.contains(query)) {
            moduleHolder.filterLevel = 1;
            return true;
        }
        if (authorLw.contains(query) || (moduleInfo.description != null &&
                moduleInfo.description.toLowerCase(Locale.ROOT).contains(query))) {
            moduleHolder.filterLevel = 2;
            return true;
        }
        moduleHolder.filterLevel = 3;
        return false;
    }

    public void applyTo(final RecyclerView moduleList,final ModuleViewAdapter moduleViewAdapter) {
        if (this.updating) return;
        this.updating = true;
        ModuleManager.getINSTANCE().afterScan();
        RepoManager.getINSTANCE().afterUpdate();
        final ArrayList<ModuleHolder> moduleHolders;
        final int newNotificationsLen;
        final boolean first;
        try {
            synchronized (this.updateLock) {
                // Build start
                moduleHolders = new ArrayList<>(Math.min(64,
                        this.mappedModuleHolders.size() + 5));
                int special = 0;
                Iterator<NotificationType> notificationTypeIterator = this.notifications.iterator();
                while (notificationTypeIterator.hasNext()) {
                    NotificationType notificationType = notificationTypeIterator.next();
                    if (notificationType.shouldRemove()) {
                        notificationTypeIterator.remove();
                    } else {
                        if (notificationType.special) special++;
                        moduleHolders.add(new ModuleHolder(notificationType));
                    }
                }
                first = moduleViewAdapter.moduleHolders.isEmpty();
                newNotificationsLen = this.notifications.size() + 1 - special;
                EnumSet<ModuleHolder.Type> headerTypes = EnumSet.of(ModuleHolder.Type.SEPARATOR,
                        ModuleHolder.Type.NOTIFICATION, ModuleHolder.Type.FOOTER);
                Iterator<ModuleHolder> moduleHolderIterator = this.mappedModuleHolders.values().iterator();
                synchronized (this.queryLock) {
                    while (moduleHolderIterator.hasNext()) {
                        ModuleHolder moduleHolder = moduleHolderIterator.next();
                        if (moduleHolder.shouldRemove()) {
                            moduleHolderIterator.remove();
                        } else {
                            ModuleHolder.Type type = moduleHolder.getType();
                            if (matchFilter(moduleHolder)) {
                                if (headerTypes.add(type)) {
                                    ModuleHolder separator = new ModuleHolder(type);
                                    if (type == ModuleHolder.Type.INSTALLABLE) {
                                        ModuleSorter moduleSorter = this.moduleSorter;
                                        separator.filterLevel = this.moduleSorter.icon;
                                        separator.onClickListener = v -> {
                                            if (this.updating || this.moduleSorter != moduleSorter)
                                                return; // Do not allow spams calls
                                            this.moduleSorter = this.moduleSorter.next();
                                            new Thread(() -> // Apply async
                                                    this.applyTo(moduleList, moduleViewAdapter),
                                                    "Sorter apply Thread").start();
                                        };
                                    }
                                    moduleHolders.add(separator);
                                }
                                moduleHolders.add(moduleHolder);
                            }
                        }
                    }
                }
                moduleHolders.sort(this.moduleSorter);
                // Header is always first
                //moduleHolders.add(0, headerFooter[0] =
                //        new ModuleHolder(this.headerPx / 2, true));
                // Footer is always last
                //moduleHolders.add(headerFooter[1] =
                //        new ModuleHolder(this.footerPx * 2, false));
                Timber.i("Got " + moduleHolders.size() + " entries!");
                // Build end
            }
        } finally {
            this.updating = false;
        }
        this.activity.runOnUiThread(() -> {
            this.updateInsets = RUNNABLE;
            final EnumSet<NotificationType> oldNotifications =
                    EnumSet.noneOf(NotificationType.class);
            boolean isTop = first || !moduleList.canScrollVertically(-1);
            boolean isBottom = !isTop && !moduleList.canScrollVertically(1);
            int oldNotificationsLen = 0;
            int oldOfflineModulesLen = 0;
            for (ModuleHolder moduleHolder : moduleViewAdapter.moduleHolders) {
                NotificationType notificationType = moduleHolder.notificationType;
                if (notificationType != null) {
                    oldNotifications.add(notificationType);
                    if (!notificationType.special)
                        oldNotificationsLen++;
                } else if (moduleHolder.footerPx != -1 &&
                        moduleHolder.filterLevel == 1)
                    oldNotificationsLen++; // Fix header
                if (moduleHolder.separator == ModuleHolder.Type.INSTALLABLE)
                    break;
                oldOfflineModulesLen++;
            }
            oldOfflineModulesLen -= oldNotificationsLen;
            int newOfflineModulesLen = 0;
            for (ModuleHolder moduleHolder : moduleHolders) {
                if (moduleHolder.separator == ModuleHolder.Type.INSTALLABLE)
                    break;
                newOfflineModulesLen++;
            }
            newOfflineModulesLen -= newNotificationsLen;
            moduleViewAdapter.moduleHolders.size();
            int newLen = moduleHolders.size();
            int oldLen = moduleViewAdapter.moduleHolders.size();
            moduleViewAdapter.moduleHolders.clear();
            moduleViewAdapter.moduleHolders.addAll(moduleHolders);
            if (oldNotificationsLen != newNotificationsLen ||
                    !oldNotifications.equals(this.notifications)) {
                notifySizeChanged(moduleViewAdapter, 0,
                        oldNotificationsLen, newNotificationsLen);
            } else {
                notifySizeChanged(moduleViewAdapter, 0, 1, 1);
            }
            if (newLen - newNotificationsLen == 0) {
                notifySizeChanged(moduleViewAdapter, newNotificationsLen,
                        oldLen - oldNotificationsLen, 0);
            } else {
                notifySizeChanged(moduleViewAdapter, newNotificationsLen,
                        oldOfflineModulesLen, newOfflineModulesLen);
                notifySizeChanged(moduleViewAdapter,
                        newNotificationsLen + newOfflineModulesLen,
                        oldLen - oldNotificationsLen - oldOfflineModulesLen,
                        newLen - newNotificationsLen - newOfflineModulesLen);
            }
            if (isTop) moduleList.scrollToPosition(0);
            if (isBottom) moduleList.scrollToPosition(newLen);
            this.updateInsets = () -> {
                notifySizeChanged(moduleViewAdapter, 0, 1, 1);
                notifySizeChanged(moduleViewAdapter,
                        moduleHolders.size(), 1, 1);
            };
        });
    }

    public void setQuery(String query) {
        synchronized (this.queryLock) {
            Timber.i("Query " + this.query + " -> " + query);
            this.query = query == null ? "" :
                    query.trim().toLowerCase(Locale.ROOT);
        }
    }

    public boolean setQueryChange(String query) {
        synchronized (this.queryLock) {
            String newQuery = query == null ? "" :
                    query.trim().toLowerCase(Locale.ROOT);
            Timber.i("Query change " + this.query + " -> " + newQuery);
            if (this.query.equals(newQuery))
                return false;
            this.query = newQuery;
        }
        return true;
    }

    public void setHeaderPx(int headerPx) {
        if (this.headerPx != headerPx) {
            synchronized (this.updateLock) {
                this.headerPx = headerPx;
            }
        }
    }

    public void setFooterPx(int footerPx) {
        if (this.footerPx != footerPx) {
            synchronized (this.updateLock) {
                this.footerPx = footerPx;
            }
        }
    }

    public void updateInsets() {
        this.updateInsets.run();
    }
}
