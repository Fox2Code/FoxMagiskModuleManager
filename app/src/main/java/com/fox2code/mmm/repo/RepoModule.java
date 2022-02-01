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

    public RepoModule(RepoData repoData, String id) {
        this.repoData = repoData;
        this.moduleInfo = new ModuleInfo(id);
        this.id = id;
        this.moduleInfo.flags |=
                ModuleInfo.FLAG_METADATA_INVALID;
    }
}
