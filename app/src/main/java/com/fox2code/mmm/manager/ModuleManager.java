package com.fox2code.mmm.manager;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.installer.InstallerInitializer;
import com.fox2code.mmm.utils.SyncManager;
import com.fox2code.mmm.utils.io.PropUtils;
import com.fox2code.mmm.utils.realm.ModuleListCache;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.io.SuFile;
import com.topjohnwu.superuser.io.SuFileInputStream;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Objects;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import timber.log.Timber;

public final class ModuleManager extends SyncManager {
    // New method is not really effective, this flag force app to use old method
    public static final boolean FORCE_NEED_FALLBACK = true;
    private static final int FLAG_MM_INVALID = ModuleInfo.FLAG_METADATA_INVALID;
    private static final int FLAG_MM_UNPROCESSED = ModuleInfo.FLAG_CUSTOM_INTERNAL;
    private static final int FLAGS_KEEP_INIT = FLAG_MM_UNPROCESSED | ModuleInfo.FLAGS_MODULE_ACTIVE | ModuleInfo.FLAG_MODULE_UPDATING_ONLY;
    private static final int FLAGS_RESET_UPDATE = FLAG_MM_INVALID | FLAG_MM_UNPROCESSED;
    private static final ModuleManager INSTANCE = new ModuleManager();
    private final HashMap<String, LocalModuleInfo> moduleInfos;
    private final SharedPreferences bootPrefs;
    private int updatableModuleCount = 0;

    private ModuleManager() {
        this.moduleInfos = new HashMap<>();
        this.bootPrefs = MainApplication.getBootSharedPreferences();
    }

    public static ModuleManager getINSTANCE() {
        return INSTANCE;
    }

    public static boolean isModuleActive(String moduleId) {
        ModuleInfo moduleInfo = ModuleManager.getINSTANCE().getModules().get(moduleId);
        return moduleInfo != null && (moduleInfo.flags & ModuleInfo.FLAGS_MODULE_ACTIVE) != 0;
    }

    protected void scanInternal(@NonNull UpdateListener updateListener) {
        boolean firstScan = this.bootPrefs.getBoolean("mm_first_scan", true);
        SharedPreferences.Editor editor = firstScan ? this.bootPrefs.edit() : null;
        for (ModuleInfo v : this.moduleInfos.values()) {
            v.flags |= FLAG_MM_UNPROCESSED;
            v.flags &= FLAGS_KEEP_INIT;
            v.name = v.id;
            v.version = null;
            v.versionCode = 0;
            v.author = null;
            v.description = "";
            v.support = null;
            v.config = null;
        }
        String modulesPath = InstallerInitializer.peekModulesPath();
        String[] modules = new SuFile("/data/adb/modules").list();
        boolean needFallback = FORCE_NEED_FALLBACK || modulesPath == null || !new SuFile(modulesPath).exists();
        if (!FORCE_NEED_FALLBACK && needFallback) {
            Timber.e("using fallback instead.");
        }
        if (BuildConfig.DEBUG) Timber.d("Scan");
        if (modules != null) {
            for (String module : modules) {
                if (!new SuFile("/data/adb/modules/" + module).isDirectory())
                    continue; // Ignore non directory files inside modules folder
                LocalModuleInfo moduleInfo = moduleInfos.get(module);
                // next, merge the module info with a record from ModuleListCache if it exists
                RealmConfiguration realmConfiguration;
                // get all dirs under the realms/repos/ dir under app's data dir
                File cacheRoot = new File(MainApplication.getINSTANCE().getDataDirWithPath("realms/repos/").toURI());
                ModuleListCache moduleListCache;
                boolean foundCache = false;
                for (File dir : Objects.requireNonNull(cacheRoot.listFiles())) {
                    if (dir.isDirectory()) {
                        // if the dir name matches the module name, use it as the cache dir
                        File tempCacheRoot = new File(dir.toString());
                        Timber.d("Looking for cache in %s", tempCacheRoot);
                        realmConfiguration = new RealmConfiguration.Builder().name("ModuleListCache.realm").schemaVersion(1).deleteRealmIfMigrationNeeded().allowWritesOnUiThread(true).allowQueriesOnUiThread(true).directory(tempCacheRoot).build();
                        Realm realm = Realm.getInstance(realmConfiguration);
                        Timber.d("Looking for cache for %s out of %d", module, realm.where(ModuleListCache.class).count());
                        moduleListCache = realm.where(ModuleListCache.class).equalTo("codename", module).findFirst();
                        if (moduleListCache != null) {
                            foundCache = true;
                            Timber.d("Found cache for %s", module);
                            // get module info from cache
                            if (moduleInfo == null) {
                                moduleInfo = new LocalModuleInfo(module);
                            }
                            moduleInfo.name = moduleListCache.getName();
                            moduleInfo.description = moduleListCache.getDescription() + " (cached)";
                            moduleInfo.author = moduleListCache.getAuthor();
                            moduleInfo.safe = moduleListCache.isSafe();
                            moduleInfo.support = moduleListCache.getSupport();
                            moduleInfo.donate = moduleListCache.getDonate();
                            moduleInfos.put(module, moduleInfo);
                            realm.close();
                            break;
                        }
                    }
                }

                if (moduleInfo == null) {
                    moduleInfo = new LocalModuleInfo(module);
                    moduleInfos.put(module, moduleInfo);
                    // This should not really happen, but let's handles theses cases anyway
                    moduleInfo.flags |= ModuleInfo.FLAG_MODULE_UPDATING_ONLY;
                }
                moduleInfo.flags &= ~FLAGS_RESET_UPDATE;
                if (new SuFile("/data/adb/modules/" + module + "/disable").exists()) {
                    moduleInfo.flags |= ModuleInfo.FLAG_MODULE_DISABLED;
                } else if (firstScan && needFallback) {
                    moduleInfo.flags |= ModuleInfo.FLAG_MODULE_ACTIVE;
                    editor.putBoolean("module_" + moduleInfo.id + "_active", true);
                }
                if (new SuFile("/data/adb/modules/" + module + "/remove").exists()) {
                    moduleInfo.flags |= ModuleInfo.FLAG_MODULE_UNINSTALLING;
                }
                if ((firstScan && !needFallback && new SuFile(modulesPath, module).exists()) || bootPrefs.getBoolean("module_" + moduleInfo.id + "_active", false)) {
                    moduleInfo.flags |= ModuleInfo.FLAG_MODULE_ACTIVE;
                    if (firstScan) {
                        editor.putBoolean("module_" + moduleInfo.id + "_active", true);
                    }
                } else if (!needFallback) {
                    moduleInfo.flags &= ~ModuleInfo.FLAG_MODULE_ACTIVE;
                }
                if ((moduleInfo.flags & ModuleInfo.FLAGS_MODULE_ACTIVE) != 0 && (new SuFile("/data/adb/modules/" + module + "/system").exists() || new SuFile("/data/adb/modules/" + module + "/vendor").exists() || new SuFile("/data/adb/modules/" + module + "/zygisk").exists() || new SuFile("/data/adb/modules/" + module + "/riru").exists())) {
                    moduleInfo.flags |= ModuleInfo.FLAG_MODULE_HAS_ACTIVE_MOUNT;
                }
                try {
                    PropUtils.readProperties(moduleInfo, "/data/adb/modules/" + module + "/module.prop", true);
                } catch (Exception e) {
                    if (BuildConfig.DEBUG) Timber.d(e);
                    moduleInfo.flags |= FLAG_MM_INVALID;
                }
            }
        }
        if (BuildConfig.DEBUG) Timber.d("Scan update");
        String[] modules_update = new SuFile("/data/adb/modules_update").list();
        if (modules_update != null) {
            for (String module : modules_update) {
                if (!new SuFile("/data/adb/modules_update/" + module).isDirectory())
                    continue; // Ignore non directory files inside modules folder
                if (BuildConfig.DEBUG) Timber.d(module);
                LocalModuleInfo moduleInfo = moduleInfos.get(module);
                if (moduleInfo == null) {
                    moduleInfo = new LocalModuleInfo(module);
                    moduleInfos.put(module, moduleInfo);
                }
                moduleInfo.flags &= ~FLAGS_RESET_UPDATE;
                moduleInfo.flags |= ModuleInfo.FLAG_MODULE_UPDATING;
                try {
                    PropUtils.readProperties(moduleInfo, "/data/adb/modules_update/" + module + "/module.prop", true);
                } catch (Exception e) {
                    if (BuildConfig.DEBUG) Timber.d(e);
                    moduleInfo.flags |= FLAG_MM_INVALID;
                }
            }
        }
        if (BuildConfig.DEBUG) Timber.d("Finalize scan");
        this.updatableModuleCount = 0;
        Iterator<LocalModuleInfo> moduleInfoIterator = this.moduleInfos.values().iterator();
        while (moduleInfoIterator.hasNext()) {
            LocalModuleInfo moduleInfo = moduleInfoIterator.next();
            if (BuildConfig.DEBUG) Timber.d(moduleInfo.id);
            if ((moduleInfo.flags & FLAG_MM_UNPROCESSED) != 0) {
                moduleInfoIterator.remove();
                continue; // Don't process fallbacks if unreferenced
            }
            if (moduleInfo.updateJson != null) {
                this.updatableModuleCount++;
            } else {
                moduleInfo.updateVersion = null;
                moduleInfo.updateVersionCode = Long.MIN_VALUE;
                moduleInfo.updateZipUrl = null;
                moduleInfo.updateChangeLog = "";
            }
            if (moduleInfo.name == null || (moduleInfo.name.equals(moduleInfo.id))) {
                moduleInfo.name = Character.toUpperCase(moduleInfo.id.charAt(0)) + moduleInfo.id.substring(1).replace('_', ' ');
            }
            if (moduleInfo.version == null || moduleInfo.version.trim().isEmpty()) {
                moduleInfo.version = "v" + moduleInfo.versionCode;
            }
            moduleInfo.verify();
        }
        if (firstScan) {
            editor.putBoolean("mm_first_scan", false);
            editor.apply();
        }
    }

    public HashMap<String, LocalModuleInfo> getModules() {
        this.afterScan();
        return this.moduleInfos;
    }

    public int getUpdatableModuleCount() {
        this.afterScan();
        return this.updatableModuleCount;
    }

    public boolean setEnabledState(ModuleInfo moduleInfo, boolean checked) {
        if (moduleInfo.hasFlag(ModuleInfo.FLAG_MODULE_UPDATING) && !checked) return false;
        SuFile disable = new SuFile("/data/adb/modules/" + moduleInfo.id + "/disable");
        if (checked) {
            if (disable.exists() && !disable.delete()) {
                moduleInfo.flags |= ModuleInfo.FLAG_MODULE_DISABLED;
                return false;
            }
            moduleInfo.flags &= ~ModuleInfo.FLAG_MODULE_DISABLED;
        } else {
            if (!disable.exists() && !disable.createNewFile()) {
                return false;
            }
            moduleInfo.flags |= ModuleInfo.FLAG_MODULE_DISABLED;
        }
        return true;
    }

    public boolean setUninstallState(ModuleInfo moduleInfo, boolean checked) {
        if (checked && moduleInfo.hasFlag(ModuleInfo.FLAG_MODULE_UPDATING)) return false;
        SuFile disable = new SuFile("/data/adb/modules/" + moduleInfo.id + "/remove");
        if (checked) {
            if (!disable.exists() && !disable.createNewFile()) {
                return false;
            }
            moduleInfo.flags |= ModuleInfo.FLAG_MODULE_UNINSTALLING;
        } else {
            if (disable.exists() && !disable.delete()) {
                moduleInfo.flags |= ModuleInfo.FLAG_MODULE_UNINSTALLING;
                return false;
            }
            moduleInfo.flags &= ~ModuleInfo.FLAG_MODULE_UNINSTALLING;
        }
        return true;
    }

    public boolean masterClear(ModuleInfo moduleInfo) {
        if (moduleInfo.hasFlag(ModuleInfo.FLAG_MODULE_HAS_ACTIVE_MOUNT)) return false;
        String escapedId = moduleInfo.id.replace("\\", "\\\\").replace("\"", "\\\"").replace(" ", "\\ ");
        try { // Check for module that declare having file outside their own folder.
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(SuFileInputStream.open("/data/adb/modules/." + moduleInfo.id + "-files"), StandardCharsets.UTF_8))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    line = line.trim().replace(' ', '.');
                    if (!line.startsWith("/data/adb/") || line.contains("*") || line.contains("/../") || line.endsWith("/..") || line.startsWith("/data/adb/modules") || line.equals("/data/adb/magisk.db"))
                        continue;
                    line = line.replace("\\", "\\\\").replace("\"", "\\\"");
                    Shell.cmd("rm -rf \"" + line + "\"").exec();
                }
            }
        } catch (IOException ignored) {
        }
        Shell.cmd("rm -rf /data/adb/modules/" + escapedId + "/").exec();
        Shell.cmd("rm -f /data/adb/modules/." + escapedId + "-files").exec();
        Shell.cmd("rm -rf /data/adb/modules_update/" + escapedId + "/").exec();
        moduleInfo.flags = ModuleInfo.FLAG_METADATA_INVALID;
        return true;
    }
}
