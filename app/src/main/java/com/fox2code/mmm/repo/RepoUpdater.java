package com.fox2code.mmm.repo;

import androidx.annotation.NonNull;

import com.fox2code.mmm.utils.io.Http;
import com.fox2code.mmm.utils.realm.ModuleListCache;

import org.json.JSONException;
import org.json.JSONObject;

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
                //
                RealmConfiguration realmConfiguration = new RealmConfiguration.Builder().name("ModuleListCache.realm").schemaVersion(1).deleteRealmIfMigrationNeeded().build();
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
                // this.indexRaw is the raw index file (json) and the modules can be either under the "modules" key or the "data" key
                // both are arrays of modules
                // try to get modules from "modules" key
                JSONObject modules = new JSONObject(new String(this.indexRaw, StandardCharsets.UTF_8));
                // check if modules has key "modules" or "data"
                try {
                    if (modules.has("modules")) {
                        // get modules from "modules" key
                        modules = modules.getJSONObject("modules");
                    } else if (modules.has("data")) {
                        // get modules from "data" key
                        modules = modules.getJSONObject("data");
                    }
                } catch (JSONException e) {
                    // there's a possibility that the modules key is an array, so we need to convert it to a json object
                    // get modules array
                    JSONObject[] modulesArray = new JSONObject[]{modules};
                    // create new json object
                    modules = new JSONObject();
                    // iterate over modules array
                    for (int i = 0; i < modulesArray.length; i++) {
                        // put module in json object
                        modules.put(String.valueOf(i), modulesArray[i]);
                    }
                }
                for (JSONObject module : new JSONObject[]{modules}) {
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
                        int minApi = module.getInt("minApi");
                        // get module max api
                        int maxApi = module.getInt("maxApi");
                        // get module min magisk
                        int minMagisk = module.getInt("minMagisk");
                        // get module need ramdisk
                        boolean needRamdisk = module.getBoolean("needRamdisk");
                        // get module support
                        String support = module.getString("support");
                        // get module donate
                        String donate = module.getString("donate");
                        // get module config
                        String config = module.getString("config");
                        // get module change boot
                        boolean changeBoot = module.getBoolean("changeBoot");
                        // get module mmt reborn
                        boolean mmtReborn = module.getBoolean("mmtReborn");
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
                        Realm.getInstanceAsync(realmConfiguration, new Realm.Callback() {
                            @Override
                            public void onSuccess(@NonNull Realm realm) {
                                realm.executeTransactionAsync(r -> {
                                    // create a new module
                                    ModuleListCache moduleListCache = r.createObject(ModuleListCache.class);
                                    // set module id
                                    moduleListCache.setId(id);
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
                                    moduleListCache.setMinApi(minApi);
                                    // set module max api
                                    moduleListCache.setMaxApi(maxApi);
                                    // set module min magisk
                                    moduleListCache.setMinMagisk(minMagisk);
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
                                }, () -> {
                                    // Transaction was a success.
                                    Timber.d("onSuccess: Transaction was a success.");
                                    // close realm
                                    realm.close();
                                }, error -> {
                                    // Transaction failed and was automatically canceled.
                                    Timber.e(error);
                                    // close realm
                                    realm.close();
                                });
                            }
                        });
                    } catch (
                            JSONException e) {
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
