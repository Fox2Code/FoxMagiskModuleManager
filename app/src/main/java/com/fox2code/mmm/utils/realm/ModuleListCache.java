package com.fox2code.mmm.utils.realm;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.Required;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class ModuleListCache extends RealmObject {
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

    // for compatibility, only id is required
    @PrimaryKey
    @Required
    private String id;
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

    public ModuleListCache(String id, String name, String version, int versionCode, String author, String description, int minApi, int maxApi, int minMagisk, boolean needRamdisk, String support, String donate, String config, boolean changeBoot, boolean mmtReborn, String repoId, boolean installed, int installedVersionCode) {
        this.id = id;
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
    }

    public ModuleListCache() {
    }
}
