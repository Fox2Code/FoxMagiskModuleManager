package com.fox2code.mmm.repo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.XHooks;
import com.fox2code.mmm.XRepo;
import com.fox2code.mmm.androidacy.AndroidacyRepoData;
import com.fox2code.mmm.manager.ModuleInfo;
import com.fox2code.mmm.utils.Files;
import com.fox2code.mmm.utils.Hashes;
import com.fox2code.mmm.utils.Http;
import com.fox2code.mmm.utils.PropUtils;
import com.fox2code.mmm.utils.SyncManager;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public final class RepoManager extends SyncManager {
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
    public static final String DG_MAGISK_REPO_GITHUB_RAW =
            "https://raw.githubusercontent.com/Googlers-Repo/googlers-repo.github.io/master/modules.json";
    private static final String TAG = "RepoManager";
    private static final String MAGISK_REPO_MANAGER =
            "https://magisk-modules-repo.github.io/submission/modules.json";
    private static final Object lock = new Object();
    private static final double STEP1 = 0.1D;
    private static final double STEP2 = 0.8D;
    private static final double STEP3 = 0.1D;
    private static volatile RepoManager INSTANCE;
    private final MainApplication mainApplication;
    private final LinkedHashMap<String, RepoData> repoData;
    private final HashMap<String, RepoModule> modules;
    private final AndroidacyRepoData androidacyRepoData;
    private final CustomRepoManager customRepoManager;
    public String repoLastErrorName = null;
    private boolean hasInternet;
    private boolean initialized;
    private boolean repoLastSuccess;

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
                DG_MAGISK_REPO_GITHUB_RAW, "Googlers Magisk Repo");
        dgRepo.defaultWebsite = "https://dergoogler.com/repo";
        this.androidacyRepoData = this.addAndroidacyRepoData();
        this.customRepoManager = new CustomRepoManager(mainApplication, this);
        XHooks.onRepoManagerInitialize();
        // Populate default cache
        boolean x = false;
        for (RepoData repoData : this.repoData.values()) {
            if (repoData == this.androidacyRepoData) {
                if (x) return;
                x = true;
            }
            this.populateDefaultCache(repoData);
        }
        this.initialized = true;
    }

    public static RepoManager getINSTANCE() {
        if (INSTANCE == null || !INSTANCE.initialized) {
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

    public static RepoManager getINSTANCE_UNSAFE() {
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
            case DG_MAGISK_REPO_GITHUB_RAW:
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
            case RepoManager.DG_MAGISK_REPO_GITHUB_RAW:
                return true;
        }
        return false;
    }

    /**
     * Safe way to do {@code RepoManager.getInstance().androidacyRepoData.isEnabled()}
     * without initializing RepoManager
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isAndroidacyRepoEnabled() {
        return INSTANCE != null && INSTANCE.androidacyRepoData != null &&
                INSTANCE.androidacyRepoData.isEnabled();
    }

    private void populateDefaultCache(RepoData repoData) {
        for (RepoModule repoModule : repoData.moduleHashMap.values()) {
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
        if (DG_MAGISK_REPO.equals(url) ||
                DG_MAGISK_REPO_GITHUB.equals(url))
            url = DG_MAGISK_REPO_GITHUB_RAW;
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

    @SuppressLint("StringFormatInvalid")
    protected void scanInternal(@NonNull UpdateListener updateListener) {
        this.modules.clear();
        updateListener.update(0D);
        // Using LinkedHashSet to deduplicate Androidacy entry.
        RepoData[] repoDatas = new LinkedHashSet<>(
                this.repoData.values()).toArray(new RepoData[0]);
        RepoUpdater[] repoUpdaters = new RepoUpdater[repoDatas.length];
        int moduleToUpdate = 0;
        for (int i = 0; i < repoDatas.length; i++) {
            if (BuildConfig.DEBUG) Log.d("NoodleDebug", repoDatas[i].getName());
            moduleToUpdate += (repoUpdaters[i] =
                    new RepoUpdater(repoDatas[i])).fetchIndex();
            updateListener.update(STEP1 / repoDatas.length * (i + 1));
        }
        if (BuildConfig.DEBUG) Log.d("NoodleDebug", "Updating meta-data");
        int updatedModules = 0;
        boolean allowLowQualityModules = MainApplication.isDisableLowQualityModuleFilter();
        for (int i = 0; i < repoUpdaters.length; i++) {
            // Check if the repo is enabled
            if (!repoUpdaters[i].repoData.isEnabled()) {
                if (BuildConfig.DEBUG) Log.d("NoodleDebug", "Skipping disabled repo: " + repoUpdaters[i].repoData.getName());
                continue;
            }
            List<RepoModule> repoModules = repoUpdaters[i].toUpdate();
            RepoData repoData = repoDatas[i];
            if (BuildConfig.DEBUG) Log.d("NoodleDebug", repoData.getName());
            if (BuildConfig.DEBUG) Log.d(TAG, "Registering " + repoData.getName());
            for (RepoModule repoModule : repoModules) {
                if (BuildConfig.DEBUG) Log.d("NoodleDebug", repoModule.id);
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
            for (RepoModule repoModule : repoUpdaters[i].toApply()) {
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
        if (BuildConfig.DEBUG) Log.d("NoodleDebug", "Finishing update");
        this.hasInternet = false;
        // Check if we have internet connection
        // Attempt to contact connectivitycheck.gstatic.com/generate_204
        // If we can't, we don't have internet connection
        try {
            HttpURLConnection urlConnection = (HttpURLConnection) new URL(
                    "https://connectivitycheck.gstatic.com/generate_204").openConnection();
            urlConnection.setInstanceFollowRedirects(false);
            urlConnection.setConnectTimeout(1000);
            urlConnection.setReadTimeout(1000);
            urlConnection.setUseCaches(false);
            urlConnection.getInputStream().close();
            if (urlConnection.getResponseCode() == 204 &&
                    urlConnection.getContentLength() == 0) {
                this.hasInternet = true;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to check internet connection", e);
        }
        if (hasInternet) {
            for (int i = 0; i < repoDatas.length; i++) {
                // If repo is not enabled, skip
                if (!repoDatas[i].isEnabled()) {
                    if (BuildConfig.DEBUG) Log.d("NoodleDebug",
                            "Skipping " + repoDatas[i].getName() + " because it's disabled");
                    continue;
                }
                if (BuildConfig.DEBUG) Log.d("NoodleDebug", repoUpdaters[i].repoData.getName());
                this.repoLastSuccess = repoUpdaters[i].finish();
                if (!this.repoLastSuccess) {
                    Log.e(TAG, "Failed to update " + repoUpdaters[i].repoData.getName());
                    // Show snackbar on main looper and add some bottom padding
                    int finalI = i;
                    Activity context = MainApplication.getINSTANCE().getLastCompatActivity();
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (context != null) {
                            Snackbar.make(context.findViewById(android.R.id.content),
                                    context.getString(R.string.repo_update_failed_extended,
                                            repoUpdaters[finalI].repoData.getName()),
                                    Snackbar.LENGTH_LONG).show();
                        }
                    });
                    this.repoLastErrorName = repoUpdaters[i].repoData.getName();
                }
                updateListener.update(STEP1 + STEP2 + (STEP3 / repoDatas.length * (i + 1)));
            }
        }
        Log.i(TAG, "Got " + this.modules.size() + " modules!");
        updateListener.update(1D);
    }

    public void updateEnabledStates() {
        for (RepoData repoData : this.repoData.values()) {
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
        return this.hasInternet;
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

    public Collection<XRepo> getXRepos() {
        return new LinkedHashSet<>(this.repoData.values());
    }

    public boolean isLastUpdateSuccess() {
        return this.repoLastSuccess;
    }
}
