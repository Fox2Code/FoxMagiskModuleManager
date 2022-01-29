package com.fox2code.mmm.repo;

import com.fox2code.mmm.manager.ModuleInfo;

public class RepoModule {
    public final ModuleInfo moduleInfo;
    public final String id;
    public String repoName;
    public long lastUpdated;
    public String propUrl;
    public String zipUrl;
    public String notesUrl;
    public String checksum;
    boolean processed;

    public RepoModule(String id) {
        this.moduleInfo = new ModuleInfo(id);
        this.id = id;
        this.moduleInfo.flags |=
                ModuleInfo.FLAG_METADATA_INVALID;
    }
}
