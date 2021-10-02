package com.fox2code.mmm.repo;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.manager.ModuleInfo;
import com.fox2code.mmm.utils.Files;
import com.fox2code.mmm.utils.Hashes;
import com.fox2code.mmm.utils.Http;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public final class RepoManager {
    private static final String TAG = "RepoManager";
    private static final String MAGISK_REPO_MANAGER =
            "https://magisk-modules-repo.github.io/submission/modules.json";
    public static final String MAGISK_REPO =
            "https://raw.githubusercontent.com/Magisk-Modules-Repo/submission/modules/modules.json";
    public static final String MAGISK_ALT_REPO =
            "https://raw.githubusercontent.com/Magisk-Modules-Alt-Repo/json/main/modules.json";

    public static final String MAGISK_REPO_HOMEPAGE = "https://github.com/Magisk-Modules-Repo";
    public static final String MAGISK_ALT_REPO_HOMEPAGE = "https://github.com/Magisk-Modules-Alt-Repo";

    private static final Object lock = new Object();
    private static RepoManager INSTANCE;

    public static RepoManager getINSTANCE() {
        if (INSTANCE == null) {
            synchronized (lock) {
                if (INSTANCE == null) {
                    MainApplication mainApplication = MainApplication.getINSTANCE();
                    if (mainApplication != null) {
                        INSTANCE = new RepoManager(mainApplication);
                    } else {
                        throw new RuntimeException("Getting RepoManager too soon!");
                    }
                }
            }
        }
        return INSTANCE;
    }

    private final MainApplication mainApplication;
    private final LinkedHashMap<String, RepoData> repoData;
    private final HashMap<String, RepoModule> modules;

    private RepoManager(MainApplication mainApplication) {
        this.mainApplication = mainApplication;
        this.repoData = new LinkedHashMap<>();
        this.modules = new HashMap<>();
        // We do not have repo list config yet.
        this.addRepoData(MAGISK_REPO);
        this.addRepoData(MAGISK_ALT_REPO);
        // Populate default cache
        for (RepoData repoData:this.repoData.values()) {
            for (RepoModule repoModule:repoData.moduleHashMap.values()) {
                if (!repoModule.moduleInfo.hasFlag(ModuleInfo.FLAG_METADATA_INVALID)) {
                    RepoModule registeredRepoModule = this.modules.get(repoModule.id);
                    if (registeredRepoModule == null) {
                        this.modules.put(repoModule.id, repoModule);
                    } else if (repoModule.moduleInfo.versionCode >
                            registeredRepoModule.moduleInfo.versionCode) {
                        this.modules.put(repoModule.id, repoModule);
                    }
                } else {
                    Log.e(TAG, "Detected module with invalid metadata: " + repoModule.id);
                }
            }
        }
    }

    public RepoData get(String url) {
        return this.repoData.get(url);
    }

    public RepoData addOrGet(String url) {
        RepoData repoData;
        synchronized (this.repoUpdateLock) {
            repoData = this.repoData.get(url);
            if (repoData == null) {
                return this.addRepoData(url);
            }
        }
        return repoData;
    }

    public interface UpdateListener {
        void update(double value);
    }

    private final Object repoUpdateLock = new Object();
    private boolean repoUpdating;
    private boolean repoLastResult = true;

    public boolean isRepoUpdating() {
        return this.repoUpdating;
    }

    public final void afterUpdate() {
        if (this.repoUpdating) synchronized (this.repoUpdateLock) {}
    }

    public final void runAfterUpdate(Runnable runnable) {
        synchronized (this.repoUpdateLock) {
            runnable.run();
        }
    }

    // MultiThread friendly method
    public final void update(UpdateListener updateListener) {
        if (!this.repoUpdating) {
            // Do scan
            synchronized (this.repoUpdateLock) {
                this.repoUpdating = true;
                try {
                    this.repoLastResult =
                            this.scanInternal(updateListener);
                } finally {
                    this.repoUpdating = false;
                }
            }
        } else {
            // Wait for current scan
            synchronized (this.repoUpdateLock) {}
        }
    }

    private static final double STEP1 = 0.1D;
    private static final double STEP2 = 0.8D;
    private static final double STEP3 = 0.1D;

    private boolean scanInternal(UpdateListener updateListener) {
        this.modules.clear();
        updateListener.update(0D);
        RepoData[] repoDatas = this.repoData.values().toArray(new RepoData[0]);
        RepoUpdater[] repoUpdaters = new RepoUpdater[repoDatas.length];
        int moduleToUpdate = 0;
        for (int i = 0; i < repoDatas.length; i++) {
            moduleToUpdate += (repoUpdaters[i] =
                    new RepoUpdater(repoDatas[i])).fetchIndex();
            updateListener.update(STEP1 / repoDatas.length * (i + 1));
        }
        int updatedModules = 0;
        for (int i = 0; i < repoUpdaters.length; i++) {
            List<RepoModule> repoModules = repoUpdaters[i].toUpdate();
            RepoData repoData = repoDatas[i];
            for (RepoModule repoModule:repoModules) {
                try {
                    Files.write(new File(repoData.cacheRoot, repoModule.id + ".prop"),
                            Http.doHttpGet(repoModule.propUrl, false));
                    if (repoDatas[i].tryLoadMetadata(repoModule)) {
                        // Note: registeredRepoModule may not null if registered by multiple repos
                        RepoModule registeredRepoModule = this.modules.get(repoModule.id);
                        if (registeredRepoModule == null) {
                            this.modules.put(repoModule.id, repoModule);
                        } else if (repoModule.moduleInfo.versionCode >
                                registeredRepoModule.moduleInfo.versionCode) {
                            this.modules.put(repoModule.id, repoModule);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to get \"" + repoModule.id + "\" metadata", e);
                }
                updatedModules++;
                updateListener.update(STEP1 + (STEP2 / moduleToUpdate * updatedModules));
            }
            for (RepoModule repoModule:repoUpdaters[i].toApply()) {
                if ((repoModule.moduleInfo.flags & ModuleInfo.FLAG_METADATA_INVALID) == 0) {
                    RepoModule registeredRepoModule = this.modules.get(repoModule.id);
                    if (registeredRepoModule == null) {
                        this.modules.put(repoModule.id, repoModule);
                    } else if (repoModule.moduleInfo.versionCode >
                            registeredRepoModule.moduleInfo.versionCode) {
                        this.modules.put(repoModule.id, repoModule);
                    }
                }
            }
        }
        boolean hasInternet = false;
        for (int i = 0; i < repoDatas.length; i++) {
            hasInternet |= repoUpdaters[i].finish();
            updateListener.update(STEP1 + STEP2 + (STEP3 / repoDatas.length * (i + 1)));
        }
        Log.i(TAG, "Got " + this.modules.size() + " modules!");
        updateListener.update(1D);
        return hasInternet;
    }

    public HashMap<String, RepoModule> getModules() {
        this.afterUpdate();
        return this.modules;
    }

    public boolean hasConnectivity() {
        return this.repoLastResult;
    }

    public static String internalIdOfUrl(String url) {
        switch (url) {
            case MAGISK_REPO_MANAGER:
            case MAGISK_REPO:
                return "magisk_repo";
            case MAGISK_ALT_REPO:
                return "magisk_alt_repo";
            default:
                return "repo_" + Hashes.hashSha1(url);
        }
    }

    private RepoData addRepoData(String url) {
        String id = internalIdOfUrl(url);
        File cacheRoot = new File(this.mainApplication.getCacheDir(), id);
        SharedPreferences sharedPreferences = this.mainApplication
                .getSharedPreferences("mmm_" + id, Context.MODE_PRIVATE);
        RepoData repoData = new RepoData(url, cacheRoot, sharedPreferences);
        this.repoData.put(url, repoData);
        return repoData;
    }
}
