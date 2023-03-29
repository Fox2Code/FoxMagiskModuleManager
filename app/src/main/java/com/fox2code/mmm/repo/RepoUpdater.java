package com.fox2code.mmm.repo;

import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.utils.io.net.Http;
import com.fox2code.mmm.utils.realm.ModuleListCache;
import com.fox2code.mmm.utils.realm.ReposList;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmResults;
import timber.log.Timber;

public class RepoUpdater {
    public final RepoData repoData;
    public byte[] indexRaw;
    private List<RepoModule> toUpdate;
    private Collection<RepoModule> toApply;

    public RepoUpdater(RepoData repoData) {
        this.repoData = repoData;
    }

    public int fetchIndex() {
        if (!RepoManager.getINSTANCE().hasConnectivity()) {
            this.indexRaw = null;
            this.toUpdate = Collections.emptyList();
            this.toApply = Collections.emptySet();
            return 0;
        }
        if (!this.repoData.isEnabled()) {
            this.indexRaw = null;
            this.toUpdate = Collections.emptyList();
            this.toApply = Collections.emptySet();
            return 0;
        }
        // if we shouldn't update, get the values from the ModuleListCache realm
        if (!this.repoData.shouldUpdate()) {
            Timber.d("Fetching index from cache for %s", this.repoData.id);
            File cacheRoot = MainApplication.getINSTANCE().getDataDirWithPath("realms/repos/" + this.repoData.id);
            RealmConfiguration realmConfiguration = new RealmConfiguration.Builder().name("ModuleListCache.realm").encryptionKey(MainApplication.getINSTANCE().getExistingKey()).schemaVersion(1).deleteRealmIfMigrationNeeded().allowWritesOnUiThread(true).allowQueriesOnUiThread(true).directory(cacheRoot).build();
            Realm realm = Realm.getInstance(realmConfiguration);
            RealmResults<ModuleListCache> results = realm.where(ModuleListCache.class).equalTo("repoId", this.repoData.id).findAll();
            // reposlist realm
            RealmConfiguration realmConfiguration2 = new RealmConfiguration.Builder().name("ReposList.realm").encryptionKey(MainApplication.getINSTANCE().getExistingKey()).allowQueriesOnUiThread(true).allowWritesOnUiThread(true).directory(MainApplication.getINSTANCE().getDataDirWithPath("realms")).schemaVersion(1).build();
            Realm realm2 = Realm.getInstance(realmConfiguration2);
            this.toUpdate = Collections.emptyList();
            this.toApply = new HashSet<>();
            for (ModuleListCache moduleListCache : results) {
                this.toApply.add(new RepoModule(this.repoData, moduleListCache.getCodename(), moduleListCache.getName(), moduleListCache.getDescription(), moduleListCache.getAuthor(), moduleListCache.getDonate(), moduleListCache.getConfig(), moduleListCache.getSupport(), moduleListCache.getVersion(), moduleListCache.getVersionCode()));
            }
            Timber.d("Fetched %d modules from cache for %s, from %s records", this.toApply.size(), this.repoData.id, results.size());
            // apply the toApply list to the toUpdate list
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("modules", new JSONArray(results.asJSON()));
                this.toUpdate = this.repoData.populate(jsonObject);
            } catch (Exception e) {
                Timber.e(e);
            }
            // close realm
            realm.close();
            realm2.close();
            // Since we reuse instances this should work
            this.toApply = new HashSet<>(this.repoData.moduleHashMap.values());
            this.toApply.removeAll(this.toUpdate);
            // Return repo to update
            return this.toUpdate.size();
        }
        try {
            if (!this.repoData.prepare()) {
                this.indexRaw = null;
                this.toUpdate = Collections.emptyList();
                this.toApply = this.repoData.moduleHashMap.values();
                return 0;
            }
            this.indexRaw = Http.doHttpGet(this.repoData.getUrl(), false);
            this.toUpdate = this.repoData.populate(new JSONObject(new String(this.indexRaw, StandardCharsets.UTF_8)));
            // Since we reuse instances this should work
            this.toApply = new HashSet<>(this.repoData.moduleHashMap.values());
            this.toApply.removeAll(this.toUpdate);
            // Return repo to update
            return this.toUpdate.size();
        } catch (
                Exception e) {
            Timber.e(e);
            this.indexRaw = null;
            this.toUpdate = Collections.emptyList();
            this.toApply = Collections.emptySet();
            return 0;
        }
    }

    public List<RepoModule> toUpdate() {
        return this.toUpdate;
    }

    public Collection<RepoModule> toApply() {
        return this.toApply;
    }

    public boolean finish() {
        var success = new AtomicBoolean(false);
        // If repo is not enabled we don't need to do anything, just return true
        if (!this.repoData.isEnabled()) {
            return true;
        }
        if (this.indexRaw != null) {
            try {
                // iterate over modules, using this.supportedProperties as a template to attempt to get each property from the module. everything that is not null is added to the module
                // use realm to insert to
                // props avail:
                File cacheRoot = MainApplication.getINSTANCE().getDataDirWithPath("realms/repos/" + this.repoData.id);
                RealmConfiguration realmConfiguration = new RealmConfiguration.Builder().name("ModuleListCache.realm").encryptionKey(MainApplication.getINSTANCE().getExistingKey()).schemaVersion(1).deleteRealmIfMigrationNeeded().allowWritesOnUiThread(true).allowQueriesOnUiThread(true).directory(cacheRoot).build();
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
                //
                // all except first six can be null
                // this.indexRaw is the raw index file (json)
                JSONObject modules = new JSONObject(new String(this.indexRaw, StandardCharsets.UTF_8));
                JSONArray modulesArray;
                // androidacy repo uses "data" key, others should use "modules" key. Both are JSONArrays
                if (this.repoData.getName().equals("Androidacy Modules Repo")) {
                    // get modules from "data" key. This is a JSONArray so we need to convert it to a JSONObject
                    modulesArray = modules.getJSONArray("data");
                } else {
                    // get modules from "modules" key. This is a JSONArray so we need to convert it to a JSONObject
                    modulesArray = modules.getJSONArray("modules");
                }
                Realm realm = Realm.getInstance(realmConfiguration);
                // drop old data
                if (realm.isInTransaction()) {
                    realm.commitTransaction();
                }
                realm.beginTransaction();
                realm.where(ModuleListCache.class).equalTo("repoId", this.repoData.id).findAll().deleteAllFromRealm();
                realm.commitTransaction();
                // iterate over modules. pls don't hate me for this, its ugly but it works
                for (int n = 0; n < modulesArray.length(); n++) {
                    // get module
                    JSONObject module = modulesArray.getJSONObject(n);
                    try {
                        // get module id
                        // if codename is present, prefer that over id
                        String id;
                        if (module.has("codename") && !module.getString("codename").equals("")) {
                            id = module.getString("codename");
                        } else {
                            id = module.getString("id");
                        }
                        // get module name
                        String name = module.getString("name");
                        // get module version
                        String version = module.getString("version");
                        // get module version code
                        int versionCode = module.getInt("versionCode");
                        // get module author
                        String author = module.getString("author");
                        // get module description
                        String description = module.getString("description");
                        // get module min api
                        String minApi;
                        if (module.has("minApi") && !module.getString("minApi").equals("")) {
                            minApi = module.getString("minApi");
                        } else {
                            minApi = "0";
                        }
                        // coerce min api to int
                        int minApiInt = Integer.parseInt(minApi);
                        // get module max api and set to 0 if it's "" or null
                        String maxApi;
                        if (module.has("maxApi") && !module.getString("maxApi").equals("")) {
                            maxApi = module.getString("maxApi");
                        } else {
                            maxApi = "0";
                        }
                        // coerce max api to int
                        int maxApiInt = Integer.parseInt(maxApi);
                        // get module min magisk
                        String minMagisk;
                        if (module.has("minMagisk") && !module.getString("minMagisk").equals("")) {
                            minMagisk = module.getString("minMagisk");
                        } else {
                            minMagisk = "0";
                        }
                        // coerce min magisk to int
                        int minMagiskInt = Integer.parseInt(minMagisk);
                        // get module need ramdisk
                        boolean needRamdisk;
                        if (module.has("needRamdisk")) {
                            needRamdisk = module.getBoolean("needRamdisk");
                        } else {
                            needRamdisk = false;
                        }
                        // get module support
                        String support;
                        if (module.has("support")) {
                            support = module.getString("support");
                        } else {
                            support = "";
                        }
                        // get module donate
                        String donate;
                        if (module.has("donate")) {
                            donate = module.getString("donate");
                        } else {
                            donate = "";
                        }
                        // get module config
                        String config;
                        if (module.has("config")) {
                            config = module.getString("config");
                        } else {
                            config = "";
                        }
                        // get module change boot
                        boolean changeBoot;
                        if (module.has("changeBoot")) {
                            changeBoot = module.getBoolean("changeBoot");
                        } else {
                            changeBoot = false;
                        }
                        // get module mmt reborn
                        boolean mmtReborn;
                        if (module.has("mmtReborn")) {
                            mmtReborn = module.getBoolean("mmtReborn");
                        } else {
                            mmtReborn = false;
                        }
                        // try to get updated_at or lastUpdate value for lastUpdate
                        int lastUpdate;
                        if (module.has("updated_at")) {
                            lastUpdate = module.getInt("updated_at");
                        } else if (module.has("lastUpdate")) {
                            lastUpdate = module.getInt("lastUpdate");
                        } else {
                            lastUpdate = 0;
                        }
                        // now downloads or stars
                        int downloads;
                        if (module.has("downloads")) {
                            downloads = module.getInt("downloads");
                        } else if (module.has("stars")) {
                            downloads = module.getInt("stars");
                        } else {
                            downloads = 0;
                        }
                        // get module repo id
                        String repoId = this.repoData.id;
                        // get module installed
                        boolean installed = false;
                        // get module installed version code
                        int installedVersionCode = 0;
                        // get safe property. for now, only supported by androidacy repo and they use "vt_status" key
                        boolean safe = false;
                        if (this.repoData.getName().equals("Androidacy Modules Repo")) {
                            if (module.has("vt_status")) {
                                if (module.getString("vt_status").equals("Clean")) {
                                    safe = true;
                                }
                            }
                        }
                        // insert module to realm
                        // first create a collection of all the properties
                        // then insert to realm
                        // then commit
                        // then close
                        if (realm.isInTransaction()) {
                            realm.cancelTransaction();
                        }
                        // create a realm object and insert or update it
                        // add everything to the realm object
                        if (realm.isInTransaction()) {
                            realm.commitTransaction();
                        }
                        realm.beginTransaction();
                        ModuleListCache moduleListCache = realm.createObject(ModuleListCache.class, id);
                        moduleListCache.setName(name);
                        moduleListCache.setVersion(version);
                        moduleListCache.setVersionCode(versionCode);
                        moduleListCache.setAuthor(author);
                        moduleListCache.setDescription(description);
                        moduleListCache.setMinApi(minApiInt);
                        moduleListCache.setMaxApi(maxApiInt);
                        moduleListCache.setMinMagisk(minMagiskInt);
                        moduleListCache.setNeedRamdisk(needRamdisk);
                        moduleListCache.setSupport(support);
                        moduleListCache.setDonate(donate);
                        moduleListCache.setConfig(config);
                        moduleListCache.setChangeBoot(changeBoot);
                        moduleListCache.setMmtReborn(mmtReborn);
                        moduleListCache.setRepoId(repoId);
                        moduleListCache.setInstalled(installed);
                        moduleListCache.setInstalledVersionCode(installedVersionCode);
                        moduleListCache.setSafe(safe);
                        moduleListCache.setLastUpdate(lastUpdate);
                        moduleListCache.setStats(downloads);
                        realm.copyToRealmOrUpdate(moduleListCache);
                        realm.commitTransaction();
                        Timber.d("Inserted module %s to realm. New record is %s", id, Objects.requireNonNull(realm.where(ModuleListCache.class).equalTo("codename", id).findFirst()).toString());
                    } catch (
                            Exception e) {
                        Timber.w("Failed to get module info from module " + module + " in repo " + this.repoData.id + " with error " + e.getMessage());
                    }
                }
                realm.close();
            } catch (
                    Exception e) {
                Timber.w("Failed to get module info from %s with error %s", this.repoData.id, e.getMessage());
            }
            this.indexRaw = null;
            RealmConfiguration realmConfiguration2 = new RealmConfiguration.Builder().name("ReposList.realm").encryptionKey(MainApplication.getINSTANCE().getExistingKey()).allowQueriesOnUiThread(true).allowWritesOnUiThread(true).directory(MainApplication.getINSTANCE().getDataDirWithPath("realms")).schemaVersion(1).build();
            Realm realm2 = Realm.getInstance(realmConfiguration2);
            if (realm2.isInTransaction()) {
                realm2.cancelTransaction();
            }
            // set lastUpdate
            realm2.executeTransaction(r -> {
                ReposList repoListCache = r.where(ReposList.class).equalTo("id", this.repoData.id).findFirst();
                if (repoListCache != null) {
                    success.set(true);
                    // get unix timestamp of current time
                    int currentTime = (int) (System.currentTimeMillis() / 1000);
                    Timber.d("Updating lastUpdate for repo %s to %s which is %s seconds ago", this.repoData.id, currentTime, (currentTime - repoListCache.getLastUpdate()));
                    repoListCache.setLastUpdate(currentTime);
                } else {
                    Timber.w("Failed to update lastUpdate for repo %s", this.repoData.id);
                }
            });
        } else {
            success.set(true); // assume we're reading from cache. this may be unsafe but it's better than nothing
        }
        return success.get();
    }

}
