package com.fox2code.mmm.repo;

import androidx.annotation.StringRes;

import com.fox2code.mmm.manager.ModuleInfo;

public class RepoModule {
    public final RepoData repoData;
    public final ModuleInfo moduleInfo;
    public final String id;
    public String repoName;
    public long lastUpdated;
    public String propUrl;
    public String zipUrl;
    public String notesUrl;
    public String checksum;
    public boolean processed;
    @StringRes
    public int qualityText;
    public int qualityValue;
    public boolean safe;

    public RepoModule(RepoData repoData, String id) {
        this.repoData = repoData;
        this.moduleInfo = new ModuleInfo(id);
        this.id = id;
        this.moduleInfo.flags |=
                ModuleInfo.FLAG_METADATA_INVALID;
        this.safe = this.moduleInfo.safe;
    }

    // allows all fields to be set-
    public RepoModule(RepoData repoData, String id, String name, String description, String author, String donate, String config, String support, String version, int versionCode) {
        this.repoData = repoData;
        this.moduleInfo = new ModuleInfo(id);
        this.id = id;
        this.moduleInfo.name = name;
        this.moduleInfo.description = description;
        this.moduleInfo.author = author;
        this.moduleInfo.donate = donate;
        this.moduleInfo.config = config;
        this.moduleInfo.support = support;
        this.moduleInfo.version = version;
        this.moduleInfo.versionCode = versionCode;
        this.moduleInfo.flags |=
                ModuleInfo.FLAG_METADATA_INVALID;
        this.safe = this.moduleInfo.safe;
    }
}
