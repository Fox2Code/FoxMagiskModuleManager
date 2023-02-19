package com.fox2code.mmm.utils.realm;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.Required;
import timber.log.Timber;

@SuppressWarnings("unused")
public class ModuleListCache extends RealmObject {
    // for compatibility, only id is required
    @PrimaryKey
    @Required
    private String codename;
    private String name;
    private String version;
    private int versionCode;
    private String author;
    private String description;
    private int minApi;
    private int maxApi;
    private int minMagisk;
    private boolean needRamdisk;
    private String support;
    private String donate;
    private String config;
    private boolean changeBoot;
    private boolean mmtReborn;
    private String repoId;
    private boolean installed;
    private int installedVersionCode;
    private int lastUpdate;
    // androidacy specific, may be added by other repos
    private boolean safe;

    public ModuleListCache(String codename, String name, String version, int versionCode, String author, String description, int minApi, int maxApi, int minMagisk, boolean needRamdisk, String support, String donate, String config, boolean changeBoot, boolean mmtReborn, String repoId, boolean installed, int installedVersionCode, int lastUpdate) {
        this.codename = codename;
        this.name = name;
        this.version = version;
        this.versionCode = versionCode;
        this.author = author;
        this.description = description;
        this.minApi = minApi;
        this.maxApi = maxApi;
        this.minMagisk = minMagisk;
        this.needRamdisk = needRamdisk;
        this.support = support;
        this.donate = donate;
        this.config = config;
        this.changeBoot = changeBoot;
        this.mmtReborn = mmtReborn;
        this.repoId = repoId;
        this.installed = installed;
        this.installedVersionCode = installedVersionCode;
        this.lastUpdate = lastUpdate;
        this.safe = false;
    }

    public ModuleListCache() {
    }

    // get all modules from a repo as a json object
    public static JSONObject getRepoModulesAsJson(String repoId) {
        Realm realm = Realm.getDefaultInstance();
        RealmResults<ModuleListCache> modules = realm.where(ModuleListCache.class).equalTo("repoId", repoId).findAll();
        JSONObject jsonObject = new JSONObject();
        for (ModuleListCache module : modules) {
            try {
                jsonObject.put(module.getCodename(), module.toJson());
            } catch (
                    JSONException e) {
                Timber.e(e);
            }
        }
        realm.close();
        return jsonObject;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public int getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(int versionCode) {
        this.versionCode = versionCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSupport() {
        return support;
    }

    public void setSupport(String support) {
        this.support = support;
    }

    public String getDonate() {
        return donate;
    }

    public void setDonate(String donate) {
        this.donate = donate;
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public boolean isChangeBoot() {
        return changeBoot;
    }

    public void setChangeBoot(boolean changeBoot) {
        this.changeBoot = changeBoot;
    }

    public boolean isMmtReborn() {
        return mmtReborn;
    }

    public void setMmtReborn(boolean mmtReborn) {
        this.mmtReborn = mmtReborn;
    }

    public String getRepoId() {
        return repoId;
    }

    public void setRepoId(String repoId) {
        this.repoId = repoId;
    }

    public boolean isInstalled() {
        return installed;
    }

    public void setInstalled(boolean installed) {
        this.installed = installed;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getMinApi() {
        return minApi;
    }

    public void setMinApi(int minApi) {
        this.minApi = minApi;
    }

    public int getMaxApi() {
        return maxApi;
    }

    public void setMaxApi(int maxApi) {
        this.maxApi = maxApi;
    }

    public int getMinMagisk() {
        return minMagisk;
    }

    public void setMinMagisk(int minMagisk) {
        this.minMagisk = minMagisk;
    }

    public boolean isNeedRamdisk() {
        return needRamdisk;
    }

    public void setNeedRamdisk(boolean needRamdisk) {
        this.needRamdisk = needRamdisk;
    }

    public int getInstalledVersionCode() {
        return installedVersionCode;
    }

    public void setInstalledVersionCode(int installedVersionCode) {
        this.installedVersionCode = installedVersionCode;
    }

    public String getCodename() {
        return codename;
    }

    public void setCodename(String codename) {
        this.codename = codename;
    }

    public int getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(int lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public boolean isSafe() {
        return safe;
    }

    public void setSafe(boolean safe) {
        this.safe = safe;
    }

    private JSONObject toJson() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("name", name);
            jsonObject.put("version", version);
            jsonObject.put("versionCode", versionCode);
            jsonObject.put("author", author);
            jsonObject.put("description", description);
            jsonObject.put("minApi", minApi);
            jsonObject.put("maxApi", maxApi);
            jsonObject.put("minMagisk", minMagisk);
            jsonObject.put("needRamdisk", needRamdisk);
            jsonObject.put("support", support);
            jsonObject.put("donate", donate);
            jsonObject.put("config", config);
            jsonObject.put("changeBoot", changeBoot);
            jsonObject.put("mmtReborn", mmtReborn);
            jsonObject.put("repoId", repoId);
            jsonObject.put("installed", installed);
            jsonObject.put("installedVersionCode", installedVersionCode);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    public RealmResults<ModuleListCache> getModules() {
        // return all modules matching the repo id
        Realm realm = Realm.getDefaultInstance();
        RealmResults<ModuleListCache> modules = realm.where(ModuleListCache.class).equalTo("repoId", repoId).findAll();
        realm.close();
        return modules;
    }

    // same as above but returns a json object
    public JSONObject getModulesAsJson(String repoId) {
        Realm realm = Realm.getDefaultInstance();
        RealmResults<ModuleListCache> modules = realm.where(ModuleListCache.class).equalTo("repoId", repoId).findAll();
        JSONObject jsonObject = new JSONObject();
        // everything goes under top level "modules" key
        try {
            jsonObject.put("modules", new JSONArray());
        } catch (JSONException ignored) {
            // we should never get here
        }
        for (ModuleListCache module : modules) {
            try {
                jsonObject.getJSONArray("modules").put(module.toJson());
            } catch (
                    JSONException e) {
                Timber.e(e);
            }
        }
        realm.close();
        return jsonObject;
    }
}
