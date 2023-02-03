package com.fox2code.mmm.repo;

import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.fox2code.mmm.AppUpdateManager;
import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.MainActivity;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.XRepo;
import com.fox2code.mmm.manager.ModuleInfo;
import com.fox2code.mmm.utils.io.Files;
import com.fox2code.mmm.utils.io.PropUtils;
import com.fox2code.mmm.utils.realm.ModuleListCache;
import com.fox2code.mmm.utils.realm.ReposList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import timber.log.Timber;

public class RepoData extends XRepo {
    public final String url;
    public final String id;
    public final File cacheRoot;
    public final SharedPreferences cachedPreferences;
    public JSONObject metaDataCache;
    public final HashMap<String, RepoModule> moduleHashMap;
    private final Object populateLock = new Object();
    public long lastUpdate;
    public String name, website, support, donate, submitModule;
    public final JSONObject supportedProperties = new JSONObject();

    protected String defaultName, defaultWebsite, defaultSupport, defaultDonate, defaultSubmitModule;

    // array with module info default values
    // supported properties for a module
    //id=<string>
    //name=<string>
    //version=<string>
    //versionCode=<int>
    //author=<string>
    //description=<string>
    //minApi=<int>
    //maxApi=<int>
    //minMagisk=<int>
    //needRamdisk=<boolean>
    //support=<url>
    //donate=<url>
    //config=<package>
    //changeBoot=<boolean>
    //mmtReborn=<boolean>
    // extra properties only useful for the database
    //repoId=<string>
    //installed=<boolean>
    //installedVersionCode=<int> (only if installed)
    private boolean forceHide, enabled; // Cache for speed

    public RepoData(String url, File cacheRoot, SharedPreferences cachedPreferences) {
        // setup supportedProperties
        try {
            supportedProperties.put("id", "");
            supportedProperties.put("name", "");
            supportedProperties.put("version", "");
            supportedProperties.put("versionCode", "");
            supportedProperties.put("author", "");
            supportedProperties.put("description", "");
            supportedProperties.put("minApi", "");
            supportedProperties.put("maxApi", "");
            supportedProperties.put("minMagisk", "");
            supportedProperties.put("needRamdisk", "");
            supportedProperties.put("support", "");
            supportedProperties.put("donate", "");
            supportedProperties.put("config", "");
            supportedProperties.put("changeBoot", "");
            supportedProperties.put("mmtReborn", "");
            supportedProperties.put("repoId", "");
            supportedProperties.put("installed", "");
            supportedProperties.put("installedVersionCode", "");
        } catch (JSONException e) {
            Timber.e(e, "Error while setting up supportedProperties");
        }
        this.url = url;
        this.id = RepoManager.internalIdOfUrl(url);
        this.cacheRoot = cacheRoot;
        this.cachedPreferences = cachedPreferences;
        // metadata cache is a realm database from ModuleListCache
        this.metaDataCache = null;
        this.moduleHashMap = new HashMap<>();
        this.defaultName = url; // Set url as default name
        this.forceHide = AppUpdateManager.shouldForceHide(this.id);
        // this.enable is set from the database
        RealmConfiguration realmConfiguration = new RealmConfiguration.Builder().name("ReposList.realm").allowQueriesOnUiThread(true).allowWritesOnUiThread(true).directory(MainApplication.getINSTANCE().getDataDirWithPath("realms")).schemaVersion(1).build();
        Realm realm = Realm.getInstance(realmConfiguration);
        ReposList reposList = realm.where(ReposList.class).equalTo("id", this.id).findFirst();
        if (BuildConfig.DEBUG) {
            if (reposList == null) {
                Timber.d("RepoData for %s not found in database", this.id);
            } else {
                Timber.d("RepoData for %s found in database", this.id);
            }
        }
        Timber.d("RepoData: " + this.id + ". record in database: " + (reposList != null ? reposList.toString() : "none"));
        this.enabled = (!this.forceHide && reposList != null && reposList.isEnabled());
        this.defaultWebsite = "https://" + Uri.parse(url).getHost() + "/";
        // open realm database
        // load metadata from realm database
        if (this.enabled) {
            try {
                this.metaDataCache = ModuleListCache.getRepoModulesAsJson(this.id);
                // log count of modules in the database
                Timber.d("RepoData: " + this.id + ". modules in database: " + this.metaDataCache.length());
                // load repo metadata from ReposList unless it's a built-in repo
                if (RepoManager.isBuiltInRepo(this.id)) {
                    this.name = this.defaultName;
                    this.website = this.defaultWebsite;
                    this.support = this.defaultSupport;
                    this.donate = this.defaultDonate;
                    this.submitModule = this.defaultSubmitModule;
                } else {
                    // get everything from ReposList realm database
                    this.name = Objects.requireNonNull(realm.where(ReposList.class).equalTo("id", this.id).findFirst()).getName();
                    this.website = Objects.requireNonNull(realm.where(ReposList.class).equalTo("id", this.id).findFirst()).getWebsite();
                    this.support = Objects.requireNonNull(realm.where(ReposList.class).equalTo("id", this.id).findFirst()).getSupport();
                    this.donate = Objects.requireNonNull(realm.where(ReposList.class).equalTo("id", this.id).findFirst()).getDonate();
                    this.submitModule = Objects.requireNonNull(realm.where(ReposList.class).equalTo("id", this.id).findFirst()).getSubmitModule();
                }
            } catch (Exception e) {
                Timber.w("Failed to load repo metadata from database: " + e.getMessage() + ". If this is a first time run, this is normal.");
            }
        }
        realm.close();
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
                // module IDs must match the regex ^[a-zA-Z][a-zA-Z0-9._-]+$ and cannot be empty or null or equal ak3-helper
                if (moduleId.isEmpty() || moduleId.equals("ak3-helper") || !moduleId.matches("^[a-zA-Z][a-zA-Z0-9._-]+$")) {
                    continue;
                }
                // If module id start with a dot, warn user
                if (moduleId.charAt(0) == '.') {
                    Timber.w("This is not recommended and may indicate an attempt to hide the module");
                }
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
            Timber.d("Repo " + this.id + " enable mismatch: " + this.enabled + " vs " + enabled);
            this.enabled = enabled;
        }
        return this.enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled && !this.forceHide;
        Timber.d("Repo " + this.id + " enabled: " + this.enabled + " (forced: " + this.forceHide + ") with preferenceID: " + this.getPreferenceId());
        MainApplication.getSharedPreferences().edit().putBoolean("pref_" + this.getPreferenceId() + "_enabled", enabled).apply();
    }

    public void updateEnabledState() {
        // Make sure first_launch preference is set to false
        if (MainActivity.doSetupNowRunning) {
            return;
        }
        this.forceHide = AppUpdateManager.shouldForceHide(this.id);
        Timber.d("Repo " + this.id + " update enabled: " + this.enabled + " (forced: " + this.forceHide + ") with preferenceID: " + this.getPreferenceId());
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

    // should update (lastUpdate > 15 minutes)
    public boolean shouldUpdate() {
        RealmConfiguration realmConfiguration2 = new RealmConfiguration.Builder().name("ReposList.realm").allowQueriesOnUiThread(true).allowWritesOnUiThread(true).directory(MainApplication.getINSTANCE().getDataDirWithPath("realms")).schemaVersion(1).build();
        Realm realm2 = Realm.getInstance(realmConfiguration2);
        ReposList repo = realm2.where(ReposList.class).equalTo("id", this.id).findFirst();
        // Make sure ModuleListCache for repoId is not null
        RealmConfiguration realmConfiguration = new RealmConfiguration.Builder().name("ModuleListCache.realm").allowQueriesOnUiThread(true).allowWritesOnUiThread(true).directory(MainApplication.getINSTANCE().getDataDirWithPath("realms/" + this.id)).schemaVersion(1).build();
        Realm realm = Realm.getInstance(realmConfiguration);
        ModuleListCache moduleListCache = realm.where(ModuleListCache.class).equalTo("repoId", this.id).findFirst();
        if (repo != null) {
            if (repo.getLastUpdate() != 0 && moduleListCache != null) {
                long lastUpdate = repo.getLastUpdate();
                long currentTime = System.currentTimeMillis();
                long diff = currentTime - lastUpdate;
                long diffMinutes = diff / (60 * 1000) % 60;
                return diffMinutes > 15;
            } else {
                return true;
            }
        }
        return true;
    }
}
