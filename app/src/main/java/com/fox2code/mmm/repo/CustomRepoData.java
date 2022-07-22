package com.fox2code.mmm.repo;

import android.content.SharedPreferences;

import com.fox2code.mmm.utils.Http;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CustomRepoData extends RepoData {
    boolean loadedExternal;
    String override;

    CustomRepoData(String url, File cacheRoot, SharedPreferences cachedPreferences) {
        super(url, cacheRoot, cachedPreferences);
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

    @Override
    public boolean isLimited() {
        return true;
    }

    public void quickPrePopulate() throws IOException, JSONException {
        JSONObject jsonObject = new JSONObject(
                new String(Http.doHttpGet(this.getUrl(),
                        false), StandardCharsets.UTF_8));
        this.name = jsonObject.getString("name").trim();
        this.website = jsonObject.optString("website");
        this.support = jsonObject.optString("support");
        this.donate = jsonObject.optString("donate");
        this.submitModule = jsonObject.optString("submitModule");
    }
}
