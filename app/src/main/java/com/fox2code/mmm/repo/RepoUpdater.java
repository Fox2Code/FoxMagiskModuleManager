package com.fox2code.mmm.repo;

import android.util.Log;

import com.fox2code.mmm.utils.Files;
import com.fox2code.mmm.utils.Http;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RepoUpdater {
    private static final String TAG = "RepoUpdater";
    public final RepoData repoData;
    public byte[] indexRaw;
    private List<RepoModule> toUpdate;
    private Set<RepoModule> toApply;

    public RepoUpdater(RepoData repoData) {
        this.repoData = repoData;
    }

    public int fetchIndex() {
        try {
            this.indexRaw = Http.doHttpGet(this.repoData.url, false);
            this.toUpdate = this.repoData.populate(new JSONObject(
                    new String(this.indexRaw, StandardCharsets.UTF_8)));
            // Since we reuse instances this should work
            this.toApply = new HashSet<>(this.repoData.moduleHashMap.values());
            this.toApply.removeAll(this.toUpdate);
            // Return repo to update
            return this.toUpdate.size();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get manifest", e);
            this.indexRaw = null;
            this.toUpdate = Collections.emptyList();
            this.toApply = Collections.emptySet();
            return 0;
        }
    }

    public List<RepoModule> toUpdate() {
        return this.toUpdate;
    }

    public Set<RepoModule> toApply() {
        return this.toApply;
    }

    public boolean finish() {
        final boolean success = this.indexRaw != null;
        if (this.indexRaw != null) {
            try {
                Files.write(this.repoData.metaDataCache, this.indexRaw);
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.indexRaw = null;
        }
        this.toUpdate = null;
        this.toApply = null;
        return success;
    }
}
