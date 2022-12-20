package com.fox2code.mmm.repo;

import android.util.Log;

import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.utils.Files;
import com.fox2code.mmm.utils.Http;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class RepoUpdater {
    private static final String TAG = "RepoUpdater";
    public final RepoData repoData;
    public byte[] indexRaw;
    private List<RepoModule> toUpdate;
    private Collection<RepoModule> toApply;

    public RepoUpdater(RepoData repoData) {
        this.repoData = repoData;
    }

    public int fetchIndex() {
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
            // Ensure it's a valid json and response code is 200
            /*if (Arrays.hashCode(this.indexRaw) == 0) {
                this.indexRaw = null;
                this.toUpdate = Collections.emptyList();
                this.toApply = this.repoData.moduleHashMap.values();
                return 0;
            }*/
            this.toUpdate = this.repoData.populate(new JSONObject(
                    new String(this.indexRaw, StandardCharsets.UTF_8)));
            // Since we reuse instances this should work
            this.toApply = new HashSet<>(this.repoData.moduleHashMap.values());
            this.toApply.removeAll(this.toUpdate);
            // Return repo to update
            return this.toUpdate.size();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get manifest of " + this.repoData.id, e);
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
                Files.write(this.repoData.metaDataCache, this.indexRaw);
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "Wrote index of " + this.repoData.id);
                }
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
