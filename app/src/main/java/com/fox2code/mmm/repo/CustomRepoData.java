package com.fox2code.mmm.repo;

import com.fox2code.mmm.utils.io.net.Http;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class CustomRepoData extends RepoData {
    boolean loadedExternal;
    String override;

    CustomRepoData(String url, File cacheRoot) {
        super(url, cacheRoot);
    }

    @Override
    public boolean isEnabledByDefault() {
        return this.override != null || this.loadedExternal;
    }

    @Override
    public String getPreferenceId() {
        return this.override == null ?
                this.id : this.override;
    }

    public void quickPrePopulate() throws IOException, JSONException {
        JSONObject jsonObject = new JSONObject(
                new String(Http.doHttpGet(this.getUrl(),
                        false), StandardCharsets.UTF_8));
        // make sure there's at least a name and a modules or data object
        if (!jsonObject.has("name") || (!jsonObject.has("modules") && !jsonObject.has("data"))) {
            throw new IllegalArgumentException("Invalid repo: " + this.getUrl());
        }
        this.name = jsonObject.getString("name").trim();
        this.website = jsonObject.optString("website");
        this.support = jsonObject.optString("support");
        this.donate = jsonObject.optString("donate");
        this.submitModule = jsonObject.optString("submitModule");
    }
}
