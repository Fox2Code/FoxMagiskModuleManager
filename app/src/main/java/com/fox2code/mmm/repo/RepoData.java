package com.fox2code.mmm.repo;

import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.fox2code.mmm.AppUpdateManager;
import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.MainActivity;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.XRepo;
import com.fox2code.mmm.manager.ModuleInfo;
import com.fox2code.mmm.utils.Files;
import com.fox2code.mmm.utils.PropUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class RepoData extends XRepo {
    public final String url;
    public final String id;
    public final File cacheRoot;
    public final SharedPreferences cachedPreferences;
    public final File metaDataCache;
    public final HashMap<String, RepoModule> moduleHashMap;
    private final Object populateLock = new Object();
    public long lastUpdate;
    public String name, website, support, donate, submitModule;
    protected String defaultName, defaultWebsite, defaultSupport, defaultDonate, defaultSubmitModule;
    private boolean forceHide, enabled; // Cache for speed

    public RepoData(String url, File cacheRoot, SharedPreferences cachedPreferences) {
        this.url = url;
        this.id = RepoManager.internalIdOfUrl(url);
        this.cacheRoot = cacheRoot;
        this.cachedPreferences = cachedPreferences;
        this.metaDataCache = new File(cacheRoot, "modules.json");
        this.moduleHashMap = new HashMap<>();
        this.defaultName = url; // Set url as default name
        this.forceHide = AppUpdateManager.shouldForceHide(this.id);
        this.enabled = (!this.forceHide) && MainApplication.getSharedPreferences().getBoolean("pref_" + this.getPreferenceId() + "_enabled", true);
        this.defaultWebsite = "https://" + Uri.parse(url).getHost() + "/";
        if (!this.cacheRoot.isDirectory()) {
            boolean mkdirs = this.cacheRoot.mkdirs();
            if (!mkdirs) {
                throw new RuntimeException("Failed to create cache directory");
            }
        } else {
            if (this.metaDataCache.exists()) {
                this.lastUpdate = metaDataCache.lastModified();
                if (this.lastUpdate > System.currentTimeMillis()) {
                    this.lastUpdate = 0; // Don't allow time travel
                }
                try {
                    List<RepoModule> modules = this.populate(new JSONObject(new String(Files.read(this.metaDataCache), StandardCharsets.UTF_8)));
                    for (RepoModule repoModule : modules) {
                        if (!this.tryLoadMetadata(repoModule)) {
                            repoModule.moduleInfo.flags &= ~ModuleInfo.FLAG_METADATA_INVALID;
                        }
                    }
                } catch (
                        Exception e) {
                    boolean delete = this.metaDataCache.delete();
                    if (!delete) {
                        throw new RuntimeException("Failed to delete invalid cache file");
                    }
                }
            }
        }
    }

    private static boolean isNonNull(String str) {
        return str != null && !str.isEmpty() && !"null".equals(str);
    }

    protected boolean prepare() throws NoSuchAlgorithmException {
        return true;
    }

    protected List<RepoModule> populate(JSONObject jsonObject) throws JSONException, NoSuchAlgorithmException {
        List<RepoModule> newModules = new ArrayList<>();
        synchronized (this.populateLock) {
            String name = jsonObject.getString("name").trim();
            String nameForModules = name.endsWith(" (Official)") ? name.substring(0, name.length() - 11) : name;
            long lastUpdate = jsonObject.getLong("last_update");
            for (RepoModule repoModule : this.moduleHashMap.values()) {
                repoModule.processed = false;
            }
            JSONArray array = jsonObject.getJSONArray("modules");
            int len = array.length();
            for (int i = 0; i < len; i++) {
                JSONObject module = array.getJSONObject(i);
                String moduleId = module.getString("id");
                // Deny remote modules ids shorter than 3 chars or containing null char or space
                if (moduleId.length() < 3 || moduleId.indexOf('\0') != -1 || moduleId.indexOf(' ') != -1 || "ak3-helper".equals(moduleId))
                    continue;
                long moduleLastUpdate = module.getLong("last_update");
                String moduleNotesUrl = module.getString("notes_url");
                String modulePropsUrl = module.getString("prop_url");
                String moduleZipUrl = module.getString("zip_url");
                String moduleChecksum = module.optString("checksum");
                String moduleStars = module.optString("stars");
                String moduleDownloads = module.optString("downloads");
                RepoModule repoModule = this.moduleHashMap.get(moduleId);
                if (repoModule == null) {
                    repoModule = new RepoModule(this, moduleId);
                    this.moduleHashMap.put(moduleId, repoModule);
                    newModules.add(repoModule);
                } else {
                    if (repoModule.lastUpdated < moduleLastUpdate || repoModule.moduleInfo.hasFlag(ModuleInfo.FLAG_METADATA_INVALID)) {
                        newModules.add(repoModule);
                    }
                }
                repoModule.processed = true;
                repoModule.repoName = nameForModules;
                repoModule.lastUpdated = moduleLastUpdate;
                repoModule.notesUrl = moduleNotesUrl;
                repoModule.propUrl = modulePropsUrl;
                repoModule.zipUrl = moduleZipUrl;
                repoModule.checksum = moduleChecksum;
                if (!moduleStars.isEmpty()) {
                    try {
                        repoModule.qualityValue = Integer.parseInt(moduleStars);
                        repoModule.qualityText = R.string.module_stars;
                    } catch (
                            NumberFormatException ignored) {
                    }
                } else if (!moduleDownloads.isEmpty()) {
                    try {
                        repoModule.qualityValue = Integer.parseInt(moduleDownloads);
                        repoModule.qualityText = R.string.module_downloads;
                    } catch (
                            NumberFormatException ignored) {
                    }
                }
            }
            // Remove no longer existing modules
            Iterator<RepoModule> moduleInfoIterator = this.moduleHashMap.values().iterator();
            while (moduleInfoIterator.hasNext()) {
                RepoModule repoModule = moduleInfoIterator.next();
                if (!repoModule.processed) {
                    boolean delete = new File(this.cacheRoot, repoModule.id + ".prop").delete();
                    if (!delete) {
                        throw new RuntimeException("Failed to delete module metadata");
                    }
                    moduleInfoIterator.remove();
                } else {
                    repoModule.moduleInfo.verify();
                }
            }
            // Update final metadata
            this.name = name;
            this.lastUpdate = lastUpdate;
            this.website = jsonObject.optString("website");
            this.support = jsonObject.optString("support");
            this.donate = jsonObject.optString("donate");
            this.submitModule = jsonObject.optString("submitModule");
        }
        return newModules;
    }

    @Override
    public boolean isEnabledByDefault() {
        return BuildConfig.ENABLED_REPOS.contains(this.id);
    }

    public void storeMetadata(RepoModule repoModule, byte[] data) throws IOException {
        Files.write(new File(this.cacheRoot, repoModule.id + ".prop"), data);
    }

    public boolean tryLoadMetadata(RepoModule repoModule) {
        File file = new File(this.cacheRoot, repoModule.id + ".prop");
        if (file.exists()) {
            try {
                ModuleInfo moduleInfo = repoModule.moduleInfo;
                PropUtils.readProperties(moduleInfo, file.getAbsolutePath(), repoModule.repoName + "/" + moduleInfo.name, false);
                moduleInfo.flags &= ~ModuleInfo.FLAG_METADATA_INVALID;
                if (moduleInfo.version == null) {
                    moduleInfo.version = "v" + moduleInfo.versionCode;
                }
                return true;
            } catch (
                    Exception ignored) {
                boolean delete = file.delete();
                if (!delete) {
                    throw new RuntimeException("Failed to delete invalid metadata file");
                }
            }
        }
        repoModule.moduleInfo.flags |= ModuleInfo.FLAG_METADATA_INVALID;
        return false;
    }

    @Override
    public boolean isEnabled() {
        SharedPreferences preferenceManager = MainApplication.getSharedPreferences();
        boolean enabled = preferenceManager.getBoolean("pref_" + this.id + "_enabled", this.isEnabledByDefault());
        if (this.enabled != enabled) {
            if (BuildConfig.DEBUG) {
                Log.d("NoodleDebug", "Repo " + this.id + " enable mismatch: " + this.enabled + " vs " + enabled);
            }
            this.enabled = enabled;
        }
        return this.enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled && !this.forceHide;
        if (BuildConfig.DEBUG) {    Log.d("RepoData", "Repo " + this.id + " enabled: " + this.enabled + " (forced: " + this.forceHide + ") with preferenceID: " + this.getPreferenceId());
        }
        MainApplication.getSharedPreferences().edit().putBoolean("pref_" + this.getPreferenceId() + "_enabled", enabled).apply();
    }

    public void updateEnabledState() {
        // Make sure first_launch preference is set to false
        if (MainActivity.doSetupNowRunning) {
            return;
        }
        this.forceHide = AppUpdateManager.shouldForceHide(this.id);
        if (BuildConfig.DEBUG) {    Log.d("RepoData", "Repo " + this.id + " update enabled: " + this.enabled + " (forced: " + this.forceHide + ") with preferenceID: " + this.getPreferenceId());
        }
        this.enabled = (!this.forceHide) && MainApplication.getSharedPreferences().getBoolean("pref_" + this.getPreferenceId() + "_enabled", true);
    }

    public String getUrl() throws NoSuchAlgorithmException {
        return this.url;
    }

    public String getPreferenceId() {
        return this.id;
    }

    // Repo data info getters
    @NonNull
    @Override
    public String getName() {
        if (isNonNull(this.name))
            return this.name;
        if (this.defaultName != null)
            return this.defaultName;
        return this.url;
    }

    @NonNull
    public String getWebsite() {
        if (isNonNull(this.website))
            return this.website;
        if (this.defaultWebsite != null)
            return this.defaultWebsite;
        return this.url;
    }

    public String getSupport() {
        if (isNonNull(this.support))
            return this.support;
        return this.defaultSupport;
    }

    public String getDonate() {
        if (isNonNull(this.donate))
            return this.donate;
        return this.defaultDonate;
    }

    public String getSubmitModule() {
        if (isNonNull(this.submitModule))
            return this.submitModule;
        return this.defaultSubmitModule;
    }

    public final boolean isForceHide() {
        return this.forceHide;
    }
}
