package com.fox2code.mmm.repo;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.XHooks;
import com.fox2code.mmm.androidacy.AndroidacyRepoData;
import com.fox2code.mmm.manager.ModuleInfo;
import com.fox2code.mmm.utils.Files;
import com.fox2code.mmm.utils.Hashes;
import com.fox2code.mmm.utils.Http;
import com.fox2code.mmm.utils.PropUtils;
import com.fox2code.mmm.utils.SyncManager;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

public final class RepoManager extends SyncManager {
    private static final String TAG = "RepoManager";

    private static final String MAGISK_REPO_MANAGER =
            "https://magisk-modules-repo.github.io/submission/modules.json";
    public static final String MAGISK_REPO =
            "https://raw.githubusercontent.com/Magisk-Modules-Repo/submission/modules/modules.json";
    public static final String MAGISK_REPO_HOMEPAGE = "https://github.com/Magisk-Modules-Repo";

    public static final String MAGISK_ALT_REPO =
            "https://raw.githubusercontent.com/Magisk-Modules-Alt-Repo/json/main/modules.json";
    public static final String MAGISK_ALT_REPO_HOMEPAGE =
            "https://github.com/Magisk-Modules-Alt-Repo";
    public static final String MAGISK_ALT_REPO_JSDELIVR =
            "https://cdn.jsdelivr.net/gh/Magisk-Modules-Alt-Repo/json@main/modules.json";

    public static final String ANDROIDACY_MAGISK_REPO_ENDPOINT =
            "https://production-api.androidacy.com/magisk/repo";
    public static final String ANDROIDACY_TEST_MAGISK_REPO_ENDPOINT =
            "https://staging-api.androidacy.com/magisk/repo";
    public static final String ANDROIDACY_MAGISK_REPO_HOMEPAGE =
            "https://www.androidacy.com/modules-repo";

    public static final String DG_MAGISK_REPO =
            "https://repo.dergoogler.com/modules.json";
    public static final String DG_MAGISK_REPO_GITHUB =
            "https://googlers-magisk-repo.github.io/modules.json";

    private static final Object lock = new Object();
    private static volatile RepoManager INSTANCE;

    public static RepoManager getINSTANCE() {
        if (INSTANCE == null) {
            synchronized (lock) {
                if (INSTANCE == null) {
                    MainApplication mainApplication = MainApplication.getINSTANCE();
                    if (mainApplication != null) {
                        INSTANCE = new RepoManager(mainApplication);
                        XHooks.onRepoManagerInitialized();
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
    private final AndroidacyRepoData androidacyRepoData;
    private final CustomRepoManager customRepoManager;
    private boolean initialized;

    private RepoManager(MainApplication mainApplication) {
        INSTANCE = this; // Set early fox XHooks
        this.initialized = false;
        this.mainApplication = mainApplication;
        this.repoData = new LinkedHashMap<>();
        this.modules = new HashMap<>();
        // We do not have repo list config yet.
        RepoData altRepo = this.addRepoData(
                MAGISK_ALT_REPO, "Magisk Modules Alt Repo");
        altRepo.defaultWebsite = RepoManager.MAGISK_ALT_REPO_HOMEPAGE;
        altRepo.defaultSubmitModule =
                "https://github.com/Magisk-Modules-Alt-Repo/submission/issues";
        RepoData dgRepo = this.addRepoData(
                DG_MAGISK_REPO_GITHUB, "Googlers Magisk Repo");
        dgRepo.defaultWebsite = "https://dergoogler.com/repo";
        this.androidacyRepoData = this.addAndroidacyRepoData();
        this.customRepoManager = new CustomRepoManager(mainApplication, this);
        XHooks.onRepoManagerInitialize();
        // Populate default cache
        boolean x = false;
        for (RepoData repoData:this.repoData.values()) {
            if (repoData == this.androidacyRepoData) {
                if (x) return; x = true;
            }
            this.populateDefaultCache(repoData);
        }
        this.initialized = true;
    }

    private void populateDefaultCache(RepoData repoData) {
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
                Log.e(TAG, "Detected module with invalid metadata: " +
                        repoModule.repoName + "/" + repoModule.id);
            }
        }
    }

    public RepoData get(String url) {
        if (url == null) return null;
        if (MAGISK_ALT_REPO_JSDELIVR.equals(url)) {
            url = MAGISK_ALT_REPO;
        }
        return this.repoData.get(url);
    }

    public RepoData addOrGet(String url) {
        return this.addOrGet(url, null);
    }

    public RepoData addOrGet(String url, String fallBackName) {
        if (MAGISK_ALT_REPO_JSDELIVR.equals(url))
            url = MAGISK_ALT_REPO;
        if (DG_MAGISK_REPO.equals(url))
            url = DG_MAGISK_REPO_GITHUB;
        RepoData repoData;
        synchronized (this.syncLock) {
            repoData = this.repoData.get(url);
            if (repoData == null) {
                if (ANDROIDACY_TEST_MAGISK_REPO_ENDPOINT.equals(url) ||
                        ANDROIDACY_MAGISK_REPO_ENDPOINT.equals(url)) {
                    if (this.androidacyRepoData != null)
                        return this.androidacyRepoData;
                    return this.addAndroidacyRepoData();
                } else {
                    return this.addRepoData(url, fallBackName);
                }
            }
        }
        return repoData;
    }

    private boolean repoLastResult = true;

    private static final double STEP1 = 0.1D;
    private static final double STEP2 = 0.8D;
    private static final double STEP3 = 0.1D;

    protected void scanInternal(@NonNull UpdateListener updateListener) {
        this.modules.clear();
        updateListener.update(0D);
        // Using LinkedHashSet to deduplicate Androidacy entry.
        RepoData[] repoDatas = new LinkedHashSet<>(
                this.repoData.values()).toArray(new RepoData[0]);
        RepoUpdater[] repoUpdaters = new RepoUpdater[repoDatas.length];
        int moduleToUpdate = 0;
        for (int i = 0; i < repoDatas.length; i++) {
            moduleToUpdate += (repoUpdaters[i] =
                    new RepoUpdater(repoDatas[i])).fetchIndex();
            updateListener.update(STEP1 / repoDatas.length * (i + 1));
        }
        int updatedModules = 0;
        boolean allowLowQualityModules = MainApplication.isDisableLowQualityModuleFilter();
        for (int i = 0; i < repoUpdaters.length; i++) {
            List<RepoModule> repoModules = repoUpdaters[i].toUpdate();
            RepoData repoData = repoDatas[i];
            Log.d(TAG, "Registering " + repoData.getName());
            for (RepoModule repoModule:repoModules) {
                try {
                    if (repoModule.propUrl != null &&
                            !repoModule.propUrl.isEmpty()) {
                        repoData.storeMetadata(repoModule,
                                Http.doHttpGet(repoModule.propUrl, false));
                        Files.write(new File(repoData.cacheRoot, repoModule.id + ".prop"),
                                Http.doHttpGet(repoModule.propUrl, false));
                    }
                    if (repoData.tryLoadMetadata(repoModule) && (allowLowQualityModules ||
                            !PropUtils.isLowQualityModule(repoModule.moduleInfo))) {
                        // Note: registeredRepoModule may not be null if registered by multiple repos
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
        this.repoLastResult = hasInternet;
    }

    public void updateEnabledStates() {
        for (RepoData repoData:this.repoData.values()) {
            boolean wasEnabled = repoData.isEnabled();
            repoData.updateEnabledState();
            if (!wasEnabled && repoData.isEnabled()) {
                this.customRepoManager.dirty = true;
            }
        }
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
            case MAGISK_ALT_REPO:
            case MAGISK_ALT_REPO_JSDELIVR:
                return "magisk_alt_repo";
            case ANDROIDACY_MAGISK_REPO_ENDPOINT:
            case ANDROIDACY_TEST_MAGISK_REPO_ENDPOINT:
                return "androidacy_repo";
            case DG_MAGISK_REPO:
            case DG_MAGISK_REPO_GITHUB:
                return "dg_magisk_repo";
            default:
                return "repo_" + Hashes.hashSha1(
                        url.getBytes(StandardCharsets.UTF_8));
        }
    }

    static boolean isBuiltInRepo(String repo) {
        switch (repo) {
            case RepoManager.MAGISK_ALT_REPO:
            case RepoManager.MAGISK_ALT_REPO_JSDELIVR:
            case RepoManager.ANDROIDACY_MAGISK_REPO_ENDPOINT:
            case RepoManager.ANDROIDACY_TEST_MAGISK_REPO_ENDPOINT:
            case RepoManager.DG_MAGISK_REPO:
            case RepoManager.DG_MAGISK_REPO_GITHUB:
                return true;
        }
        return false;
    }

    private RepoData addRepoData(String url, String fallBackName) {
        String id = internalIdOfUrl(url);
        File cacheRoot = new File(this.mainApplication.getCacheDir(), id);
        SharedPreferences sharedPreferences = this.mainApplication
                .getSharedPreferences("mmm_" + id, Context.MODE_PRIVATE);
        RepoData repoData = id.startsWith("repo_") ?
                new CustomRepoData(url, cacheRoot, sharedPreferences) :
                new RepoData(url, cacheRoot, sharedPreferences);
        if (fallBackName != null && !fallBackName.isEmpty()) {
            repoData.defaultName = fallBackName;
            if (repoData instanceof CustomRepoData) {
                ((CustomRepoData) repoData).loadedExternal = true;
                this.customRepoManager.dirty = true;
                repoData.updateEnabledState();
            }
        }
        switch (url) {
            case MAGISK_REPO:
            case MAGISK_REPO_MANAGER: {
                repoData.defaultWebsite = MAGISK_REPO_HOMEPAGE;
            }
        }
        this.repoData.put(url, repoData);
        if (this.initialized) {
            this.populateDefaultCache(repoData);
        }
        return repoData;
    }

    private AndroidacyRepoData addAndroidacyRepoData() {
        File cacheRoot = new File(this.mainApplication.getCacheDir(), "androidacy_repo");
        SharedPreferences sharedPreferences = this.mainApplication
                .getSharedPreferences("mmm_androidacy_repo", Context.MODE_PRIVATE);
        AndroidacyRepoData repoData = new AndroidacyRepoData(cacheRoot,
                sharedPreferences, MainApplication.isAndroidacyTestMode());
        this.repoData.put(ANDROIDACY_MAGISK_REPO_ENDPOINT, repoData);
        this.repoData.put(ANDROIDACY_TEST_MAGISK_REPO_ENDPOINT, repoData);
        return repoData;
    }

    public AndroidacyRepoData getAndroidacyRepoData() {
        return this.androidacyRepoData;
    }

    public CustomRepoManager getCustomRepoManager() {
        return customRepoManager;
    }
}
