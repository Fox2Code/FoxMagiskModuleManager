package com.fox2code.mmm.manager;

/**
 * Representation of the module.prop
 * Optionally flags represent module status
 * It's value is 0 if not applicable
 */
public class ModuleInfo {
    public static final int FLAG_MODULE_DISABLED = 0x01;
    public static final int FLAG_MODULE_UPDATING = 0x02;
    public static final int FLAG_MODULE_ACTIVE = 0x04;
    public static final int FLAG_MODULE_UNINSTALLING = 0x08;
    public static final int FLAG_MODULE_UPDATING_ONLY = 0x10;

    public static final int FLAG_METADATA_INVALID = 0x80000000;

    // Magisk standard
    public final String id;
    public String name;
    public String version;
    public long versionCode;
    public String author;
    public String description;
    public String updateJson;
    // Community meta
    public String support;
    public String donate;
    public String config;
    // Community restrictions
    public int minMagisk;
    public int minApi;
    public int maxApi;
    // Module status (0 if not from Module Manager)
    public int flags;

    public ModuleInfo(String id) {
        this.id = id;
        this.name = id;
    }

    public ModuleInfo(ModuleInfo moduleInfo) {
        this.id = moduleInfo.id;
        this.name = moduleInfo.name;
        this.version = moduleInfo.version;
        this.versionCode = moduleInfo.versionCode;
        this.author = moduleInfo.author;
        this.description = moduleInfo.description;
        this.updateJson = moduleInfo.updateJson;
        this.support = moduleInfo.support;
        this.donate = moduleInfo.donate;
        this.config = moduleInfo.config;
        this.minMagisk = moduleInfo.minMagisk;
        this.minApi = moduleInfo.minApi;
        this.maxApi = moduleInfo.maxApi;
        this.flags = moduleInfo.flags;
    }

    public boolean hasFlag(int flag) {
        return (this.flags & flag) != 0;
    }
}
