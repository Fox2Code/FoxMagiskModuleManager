package com.fox2code.mmm.repo;

import com.fox2code.mmm.utils.io.Http;
import com.fox2code.mmm.utils.realm.ModuleListCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmConfiguration;
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
        boolean success = this.indexRaw != null;
        // If repo is not enabled we don't need to do anything, just return true
        if (!this.repoData.isEnabled()) {
            return true;
        }
        if (this.indexRaw != null) {
            try {
                // iterate over modules, using this.supportedProperties as a template to attempt to get each property from the module. everything that is not null is added to the module
                // use realm to insert to
                // props avail:
                File cacheRoot = this.repoData.cacheRoot;
                RealmConfiguration realmConfiguration = new RealmConfiguration.Builder().name("ModuleListCache.realm").schemaVersion(1).deleteRealmIfMigrationNeeded().allowWritesOnUiThread(true).allowQueriesOnUiThread(true).directory(cacheRoot).build();
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
                // iterate over modules. pls dont hate me for this, its ugly but it works
                for (int n = 0; n < modulesArray.length(); n++) {
                    // get module
                    JSONObject module = modulesArray.getJSONObject(n);
                    try {
                        // get module id
                        String id = module.getString("id");
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
                        // get module repo id
                        String repoId = this.repoData.id;
                        // get module installed
                        boolean installed = false;
                        // get module installed version code
                        int installedVersionCode = 0;
                        // insert module to realm
                        // first create a collection of all the properties
                        // then insert to realm
                        // then commit
                        // then close
                        Realm realm = Realm.getInstance(realmConfiguration);
                        if (realm.isInTransaction()) {
                            realm.cancelTransaction();
                        }
                        realm.executeTransaction(r -> {
                            // create the object
                            // if it already exists, it will be updated
                            // create a new module
                            ModuleListCache moduleListCache = r.createObject(ModuleListCache.class, id);
                            // set module name
                            moduleListCache.setName(name);
                            // set module version
                            moduleListCache.setVersion(version);
                            // set module version code
                            moduleListCache.setVersionCode(versionCode);
                            // set module author
                            moduleListCache.setAuthor(author);
                            // set module description
                            moduleListCache.setDescription(description);
                            // set module min api
                            moduleListCache.setMinApi(minApiInt);
                            // set module max api
                            moduleListCache.setMaxApi(maxApiInt);
                            // set module min magisk
                            moduleListCache.setMinMagisk(minMagiskInt);
                            // set module need ramdisk
                            moduleListCache.setNeedRamdisk(needRamdisk);
                            // set module support
                            moduleListCache.setSupport(support);
                            // set module donate
                            moduleListCache.setDonate(donate);
                            // set module config
                            moduleListCache.setConfig(config);
                            // set module change boot
                            moduleListCache.setChangeBoot(changeBoot);
                            // set module mmt reborn
                            moduleListCache.setMmtReborn(mmtReborn);
                            // set module repo id
                            moduleListCache.setRepoId(repoId);
                            // set module installed
                            moduleListCache.setInstalled(installed);
                            // set module installed version code
                            moduleListCache.setInstalledVersionCode(installedVersionCode);
                        });
                        realm.close();
                    } catch (
                            Exception e) {
                        e.printStackTrace();
                        Timber.w("Failed to get module info from module " + module + " in repo " + this.repoData.id + " with error " + e.getMessage());
                    }
                }
            } catch (
                    Exception e) {
                e.printStackTrace();
            }
            this.indexRaw = null;
        }
        this.toUpdate = null;
        this.toApply = null;
        return success;
    }

}
