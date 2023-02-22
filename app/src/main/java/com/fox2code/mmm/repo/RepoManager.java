package com.fox2code.mmm.repo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.MainActivity;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.XHooks;
import com.fox2code.mmm.XRepo;
import com.fox2code.mmm.androidacy.AndroidacyRepoData;
import com.fox2code.mmm.manager.ModuleInfo;
import com.fox2code.mmm.utils.SyncManager;
import com.fox2code.mmm.utils.io.Files;
import com.fox2code.mmm.utils.io.Hashes;
import com.fox2code.mmm.utils.io.Http;
import com.fox2code.mmm.utils.io.PropUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import timber.log.Timber;

public final class RepoManager extends SyncManager {
    public static final String MAGISK_REPO = "https://raw.githubusercontent.com/Magisk-Modules-Repo/submission/modules/modules.json";
    public static final String MAGISK_REPO_HOMEPAGE = "https://github.com/Magisk-Modules-Repo";
    public static final String MAGISK_ALT_REPO = "https://raw.githubusercontent.com/Magisk-Modules-Alt-Repo/json/main/modules.json";
    public static final String MAGISK_ALT_REPO_HOMEPAGE = "https://github.com/Magisk-Modules-Alt-Repo";
    public static final String MAGISK_ALT_REPO_JSDELIVR = "https://cdn.jsdelivr.net/gh/Magisk-Modules-Alt-Repo/json@main/modules.json";
    public static final String ANDROIDACY_MAGISK_REPO_ENDPOINT = "https://production-api.androidacy.com/magisk/repo";
    public static final String ANDROIDACY_TEST_MAGISK_REPO_ENDPOINT = "https://staging-api.androidacy.com/magisk/repo";
    public static final String ANDROIDACY_MAGISK_REPO_HOMEPAGE = "https://www.androidacy.com/modules-repo";
    private static final String MAGISK_REPO_MANAGER = "https://magisk-modules-repo.github.io/submission/modules.json";
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
        this.androidacyRepoData = this.addAndroidacyRepoData();
        RepoData altRepo = this.addRepoData(MAGISK_ALT_REPO, "Magisk Modules Alt Repo");
        altRepo.defaultWebsite = RepoManager.MAGISK_ALT_REPO_HOMEPAGE;
        altRepo.defaultSubmitModule = "https://github.com/Magisk-Modules-Alt-Repo/submission/issues";
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
        return switch (url) {
            case MAGISK_ALT_REPO, MAGISK_ALT_REPO_JSDELIVR -> "magisk_alt_repo";
            case ANDROIDACY_MAGISK_REPO_ENDPOINT, ANDROIDACY_TEST_MAGISK_REPO_ENDPOINT ->
                    "androidacy_repo";
            default -> "repo_" + Hashes.hashSha256(url.getBytes(StandardCharsets.UTF_8));
        };
    }

    static boolean isBuiltInRepo(String repo) {
        return switch (repo) {
            case RepoManager.ANDROIDACY_MAGISK_REPO_ENDPOINT, RepoManager.ANDROIDACY_TEST_MAGISK_REPO_ENDPOINT, RepoManager.MAGISK_ALT_REPO, RepoManager.MAGISK_ALT_REPO_JSDELIVR ->
                    true;
            default -> false;
        };
    }

    /**
     * Safe way to do {@code RepoManager.getInstance().androidacyRepoData.isEnabled()}
     * without initializing RepoManager
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isAndroidacyRepoEnabled() {
        return INSTANCE != null && INSTANCE.androidacyRepoData != null && INSTANCE.androidacyRepoData.isEnabled();
    }

    @SuppressWarnings("StatementWithEmptyBody")
    private void populateDefaultCache(RepoData repoData) {
        for (RepoModule repoModule : repoData.moduleHashMap.values()) {
            if (!repoModule.moduleInfo.hasFlag(ModuleInfo.FLAG_METADATA_INVALID)) {
                RepoModule registeredRepoModule = this.modules.get(repoModule.id);
                if (registeredRepoModule == null) {
                    this.modules.put(repoModule.id, repoModule);
                } else if (AndroidacyRepoData.getInstance().isEnabled() && registeredRepoModule.repoData == this.androidacyRepoData) {
                    // empty
                } else if (AndroidacyRepoData.getInstance().isEnabled() && repoModule.repoData == this.androidacyRepoData) {
                    this.modules.put(repoModule.id, repoModule);
                } else if (repoModule.moduleInfo.versionCode > registeredRepoModule.moduleInfo.versionCode) {
                    this.modules.put(repoModule.id, repoModule);
                }
            } else {
                Timber.e("Detected module with invalid metadata: " + repoModule.repoName + "/" + repoModule.id);
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
        if (MAGISK_ALT_REPO_JSDELIVR.equals(url)) url = MAGISK_ALT_REPO;
        RepoData repoData;
        synchronized (this.syncLock) {
            repoData = this.repoData.get(url);
            if (repoData == null) {
                if (ANDROIDACY_TEST_MAGISK_REPO_ENDPOINT.equals(url) || ANDROIDACY_MAGISK_REPO_ENDPOINT.equals(url)) {
                    //noinspection ReplaceNullCheck
                    if (this.androidacyRepoData != null) {
                        return this.androidacyRepoData;
                    } else {
                        return this.addAndroidacyRepoData();
                    }
                } else {
                    return this.addRepoData(url, fallBackName);
                }
            }
        }
        return repoData;
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @SuppressLint("StringFormatInvalid")
    protected void scanInternal(@NonNull UpdateListener updateListener) {
        // Refuse to start if first_launch is not false in shared preferences
        if (MainActivity.doSetupNowRunning) {
            return;
        }
        this.modules.clear();
        updateListener.update(0D);
        // Using LinkedHashSet to deduplicate Androidacy entry.
        RepoData[] repoDatas = new LinkedHashSet<>(this.repoData.values()).toArray(new RepoData[0]);
        RepoUpdater[] repoUpdaters = new RepoUpdater[repoDatas.length];
        int moduleToUpdate = 0;
        this.checkConnection();
        if (!this.hasConnectivity()) {
            updateListener.update(STEP3);
            return;
        }
        for (int i = 0; i < repoDatas.length; i++) {
            if (BuildConfig.DEBUG) Timber.d("Preparing to fetch: %s", repoDatas[i].getName());
            moduleToUpdate += (repoUpdaters[i] = new RepoUpdater(repoDatas[i])).fetchIndex();
            updateListener.update(STEP1 / repoDatas.length * (i + 1));
        }
        if (BuildConfig.DEBUG) Timber.d("Updating meta-data");
        int updatedModules = 0;
        boolean allowLowQualityModules = MainApplication.isDisableLowQualityModuleFilter();
        for (int i = 0; i < repoUpdaters.length; i++) {
            // Check if the repo is enabled
            if (!repoUpdaters[i].repoData.isEnabled()) {
                if (BuildConfig.DEBUG)
                    Timber.d("Skipping disabled repo: %s", repoUpdaters[i].repoData.getName());
                continue;
            }
            List<RepoModule> repoModules = repoUpdaters[i].toUpdate();
            RepoData repoData = repoDatas[i];
            if (BuildConfig.DEBUG) Timber.d("Registering %s", repoData.getName());
            for (RepoModule repoModule : repoModules) {
                try {
                    if (repoModule.propUrl != null && !repoModule.propUrl.isEmpty()) {
                        repoData.storeMetadata(repoModule, Http.doHttpGet(repoModule.propUrl, false));
                        Files.write(new File(repoData.cacheRoot, repoModule.id + ".prop"), Http.doHttpGet(repoModule.propUrl, false));
                    }
                    if (repoData.tryLoadMetadata(repoModule) && (allowLowQualityModules || !PropUtils.isLowQualityModule(repoModule.moduleInfo))) {
                        // Note: registeredRepoModule may not be null if registered by multiple repos
                        RepoModule registeredRepoModule = this.modules.get(repoModule.id);
                        if (registeredRepoModule == null) {
                            this.modules.put(repoModule.id, repoModule);
                        } else if (AndroidacyRepoData.getInstance().isEnabled() && registeredRepoModule.repoData == this.androidacyRepoData) {
                            // empty
                        } else if (AndroidacyRepoData.getInstance().isEnabled() && repoModule.repoData == this.androidacyRepoData) {
                            this.modules.put(repoModule.id, repoModule);
                        } else if (repoModule.moduleInfo.versionCode > registeredRepoModule.moduleInfo.versionCode) {
                            this.modules.put(repoModule.id, repoModule);
                        }
                    } else {
                        repoModule.moduleInfo.flags |= ModuleInfo.FLAG_METADATA_INVALID;
                    }
                } catch (Exception e) {
                    Timber.e(e);
                }
                updatedModules++;
                updateListener.update(STEP1 + (STEP2 / (moduleToUpdate != 0 ? moduleToUpdate : 1) * updatedModules));
            }
            for (RepoModule repoModule : repoUpdaters[i].toApply()) {
                if ((repoModule.moduleInfo.flags & ModuleInfo.FLAG_METADATA_INVALID) == 0) {
                    RepoModule registeredRepoModule = this.modules.get(repoModule.id);
                    if (registeredRepoModule == null) {
                        this.modules.put(repoModule.id, repoModule);
                    } else if (AndroidacyRepoData.getInstance().isEnabled() && registeredRepoModule.repoData == this.androidacyRepoData) {
                        // empty
                    } else if (AndroidacyRepoData.getInstance().isEnabled() && repoModule.repoData == this.androidacyRepoData) {
                        this.modules.put(repoModule.id, repoModule);
                    } else if (repoModule.moduleInfo.versionCode > registeredRepoModule.moduleInfo.versionCode) {
                        this.modules.put(repoModule.id, repoModule);
                    }
                }
            }
        }
        if (BuildConfig.DEBUG) Timber.d("Finishing update");
        if (hasConnectivity()) {
            for (int i = 0; i < repoDatas.length; i++) {
                // If repo is not enabled, skip
                if (!repoDatas[i].isEnabled()) {
                    if (BuildConfig.DEBUG)
                        Timber.d("Skipping " + repoDatas[i].getName() + " because it's disabled");
                    continue;
                }
                if (BuildConfig.DEBUG)
                    Timber.d("Finishing: %s", repoUpdaters[i].repoData.getName());
                this.repoLastSuccess = repoUpdaters[i].finish();
                if (!this.repoLastSuccess) {
                    Timber.e("Failed to update %s", repoUpdaters[i].repoData.getName());
                    // Show snackbar on main looper and add some bottom padding
                    int finalI = i;
                    Activity context = MainApplication.getINSTANCE().getLastCompatActivity();
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (context != null) {
                            // Show material dialogue with the repo name. for androidacy repo, show an option to reset the api key. show a message then a list of errors
                            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
                            builder.setTitle(R.string.repo_update_failed);
                            builder.setMessage(context.getString(R.string.repo_update_failed_message, "- " + repoUpdaters[finalI].repoData.getName()));
                            builder.setPositiveButton(android.R.string.ok, null);
                            if (repoUpdaters[finalI].repoData instanceof AndroidacyRepoData) {
                                builder.setNeutralButton(R.string.reset_api_key, (dialog, which) -> {
                                    SharedPreferences.Editor editor = MainApplication.getINSTANCE().getSharedPreferences("androidacy", 0).edit();
                                    editor.putString("androidacy_api_key", "");
                                    editor.apply();
                                    Toast.makeText(context, R.string.api_key_removed, Toast.LENGTH_SHORT).show();
                                });
                            }
                            builder.show();
                        }
                    });
                    this.repoLastErrorName = repoUpdaters[i].repoData.getName();
                }
                updateListener.update(STEP1 + STEP2 + (STEP3 / repoDatas.length * (i + 1)));
            }
        }
        Timber.i("Got " + this.modules.size() + " modules!");
        updateListener.update(1D);
    }

    private void checkConnection() {
        this.hasInternet = false;
        // Check if we have internet connection
        // Attempt to contact connectivitycheck.gstatic.com/generate_204
        // If we can't, we don't have internet connection
        Timber.d("Checking internet connection...");
        // this url is actually hosted by Cloudflare and is not dependent on Androidacy servers being up
        byte[] resp = new byte[0];
        try {
            resp = Http.doHttpGet("https://production-api.androidacy.com/cdn-cgi/trace", false);
        } catch (Exception e) {
            Timber.e("Failed to check internet connection. Assuming no internet connection.");
        }
        // get the response body
        String response = new String(resp, StandardCharsets.UTF_8);
        // check if the response body contains "visit_scheme=https" and "http/<some number>"
        // if it does, we have internet connection
        this.hasInternet = response.contains("visit_scheme=https") && response.contains("http/");
        Timber.d("Internet connection: %s", this.hasInternet);
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
        Timber.d("Has connectivity: %s", this.hasInternet);
        return this.hasInternet;
    }

    private RepoData addRepoData(String url, String fallBackName) {
        String id = internalIdOfUrl(url);
        File cacheRoot = new File(this.mainApplication.getDataDir(), "repos/" + id);
        SharedPreferences sharedPreferences = this.mainApplication.getSharedPreferences("mmm_" + id, Context.MODE_PRIVATE);
        RepoData repoData = id.startsWith("repo_") ? new CustomRepoData(url, cacheRoot, sharedPreferences) : new RepoData(url, cacheRoot, sharedPreferences);
        if (fallBackName != null && !fallBackName.isEmpty()) {
            repoData.defaultName = fallBackName;
            if (repoData instanceof CustomRepoData) {
                ((CustomRepoData) repoData).loadedExternal = true;
                this.customRepoManager.dirty = true;
                repoData.updateEnabledState();
            }
        }
        switch (url) {
            case MAGISK_REPO, MAGISK_REPO_MANAGER -> repoData.defaultWebsite = MAGISK_REPO_HOMEPAGE;
        }
        this.repoData.put(url, repoData);
        if (this.initialized) {
            this.populateDefaultCache(repoData);
        }
        return repoData;
    }

    private AndroidacyRepoData addAndroidacyRepoData() {
        // cache dir is actually under app data
        File cacheRoot = this.mainApplication.getDataDirWithPath("realms/repos/androidacy_repo");
        SharedPreferences sharedPreferences = this.mainApplication.getSharedPreferences("mmm_androidacy_repo", Context.MODE_PRIVATE);
        AndroidacyRepoData repoData = new AndroidacyRepoData(cacheRoot, sharedPreferences, MainApplication.isAndroidacyTestMode());
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
