package com.fox2code.mmm.androidacy;

import android.content.SharedPreferences;
import android.util.Log;
import android.webkit.CookieManager;

import com.fox2code.mmm.R;
import com.fox2code.mmm.manager.ModuleInfo;
import com.fox2code.mmm.repo.RepoData;
import com.fox2code.mmm.repo.RepoModule;
import com.fox2code.mmm.utils.Http;
import com.fox2code.mmm.utils.PropUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AndroidacyRepoData extends RepoData {
    private static final String TAG = "AndroidacyRepoData";
    private long androidacyBlockade = 0;

    public AndroidacyRepoData(String url, File cacheRoot,
                                 SharedPreferences cachedPreferences) {
        super(url, cacheRoot, cachedPreferences);
        if (this.metaDataCache.exists()) {
            this.androidacyBlockade = this.metaDataCache.lastModified() + 5_000L;
            if (this.androidacyBlockade - 10_000L > System.currentTimeMillis()) {
                this.androidacyBlockade = 0; // Don't allow time travel
            }
        }
    }

    @Override
    protected boolean prepare() {
        // Implementation details discussed on telegram
        long time = System.currentTimeMillis();
        if (this.androidacyBlockade > time) return false;
        this.androidacyBlockade = time + 5_000L;
        String cookies = CookieManager.getInstance().getCookie("https://.androidacy.com/");
        int start = cookies.indexOf("USER=");
        String token = null;
        if (start != -1) {
            int end = cookies.indexOf(";", start);
            if (end != -1)
                token = cookies.substring(start, end);
        }
        if (token != null) {
            try {
                Http.doHttpGet("https://api.androidacy.com/auth/me",true);
            } catch (Exception e) {
                if ("Received error code: 419".equals(e.getMessage()) ||
                        "Received error code: 429".equals(e.getMessage())) {
                    Log.e(TAG, "We are being rate limited!", e);
                    this.androidacyBlockade = time + 3_600_000L;
                    return false;
                }
                Log.w(TAG, "Invalid token, resetting...");
                CookieManager.getInstance().setCookie("https://.androidacy.com/",
                        "USER=; expires=Thu, 01 Jan 1970 00:00:00 GMT;" +
                                " path=/; secure; domain=.androidacy.com");
                token = null;
            }
        }
        if (token == null) {
            try {
                Log.i(TAG, "Refreshing token...");
                token = new String(Http.doHttpPost(
                        "https://api.androidacy.com/auth/register",
                        "",true), StandardCharsets.UTF_8);
                CookieManager.getInstance().setCookie(".androidacy.com",
                        "USER="+ token + "; expires=Fri, 31 Dec 9999 23:59:59 GMT;" +
                                " path=/; secure; domain=.androidacy.com");
            } catch (Exception e) {
                if ("Received error code: 419".equals(e.getMessage()) ||
                        "Received error code: 429".equals(e.getMessage())) {
                    Log.e(TAG, "We are being rate limited!", e);
                    this.androidacyBlockade = time + 3_600_000L;
                }
                Log.e(TAG, "Failed to get a new token", e);
                return false;
            }
        }
        return true;
    }

    @Override
    protected List<RepoModule> populate(JSONObject jsonObject) throws JSONException {
        if (!jsonObject.getString("status").equals("success"))
            throw new JSONException("Response is not a success!");
        String name = jsonObject.optString(
                "name", "Androidacy Modules Repo");
        String nameForModules = name.endsWith(" (Official)") ?
                name.substring(0, name.length() - 11) : name;
        JSONArray jsonArray = jsonObject.getJSONArray("data");
        for (RepoModule repoModule : this.moduleHashMap.values()) {
            repoModule.processed = false;
        }
        ArrayList<RepoModule> newModules = new ArrayList<>();
        int len = jsonArray.length();
        long lastLastUpdate = 0;
        for (int i = 0; i < len; i++) {
            jsonObject = jsonArray.getJSONObject(i);
            String moduleId = jsonObject.getString("codename");
            long lastUpdate = jsonObject.getLong("updated_at") * 1000;
            lastLastUpdate = Math.max(lastLastUpdate, lastUpdate);
            RepoModule repoModule = this.moduleHashMap.get(moduleId);
            if (repoModule == null) {
                repoModule = new RepoModule(this, moduleId);
                repoModule.moduleInfo.flags = 0;
                this.moduleHashMap.put(moduleId, repoModule);
                newModules.add(repoModule);
            } else {
                if (repoModule.lastUpdated < lastUpdate) {
                    newModules.add(repoModule);
                }
            }
            repoModule.processed = true;
            repoModule.lastUpdated = lastUpdate;
            repoModule.repoName = nameForModules;
            repoModule.zipUrl = filterURL(
                    jsonObject.optString("zipUrl", ""));
            repoModule.notesUrl = filterURL(
                    jsonObject.optString("notesUrl", ""));
            if (repoModule.zipUrl == null)
                repoModule.zipUrl = jsonObject.getString("url");
            if (repoModule.notesUrl == null) {
                repoModule.notesUrl = // Fallback url in case the API doesn't return one
                        "https://api.androidacy.com/magisk/readme/?module=" + moduleId;
            }
            repoModule.qualityText = R.string.module_downloads;
            repoModule.qualityValue = jsonObject.optInt("downloads", 0);
            String checksum = jsonObject.optString("checksum", "");
            repoModule.checksum = checksum.isEmpty() ? null : checksum;
            ModuleInfo moduleInfo = repoModule.moduleInfo;
            moduleInfo.name = jsonObject.getString("name");
            moduleInfo.versionCode = jsonObject.getLong("versionCode");
            moduleInfo.version = jsonObject.optString(
                    "version", "v" + moduleInfo.versionCode);
            moduleInfo.author = jsonObject.optString("author", "Unknown");
            moduleInfo.description = jsonObject.optString("description", "");
            moduleInfo.minApi = jsonObject.getInt("minApi");
            moduleInfo.maxApi = jsonObject.getInt("maxApi");
            String minMagisk = jsonObject.getString("minMagisk");
            try {
                int c = minMagisk.indexOf('.');
                if (c == -1) {
                    moduleInfo.minMagisk = Integer.parseInt(minMagisk);
                } else {
                    moduleInfo.minMagisk = // Allow 24.1 to mean 24100
                            (Integer.parseInt(minMagisk.substring(0, c)) * 1000) +
                                    (Integer.parseInt(minMagisk.substring(c + 1)) * 100);
                }
            } catch (Exception e) {
                moduleInfo.minMagisk = 0;
            }
            moduleInfo.support = filterURL(jsonObject.optString("support"));
            moduleInfo.donate = filterURL(jsonObject.optString("donate"));
            String config = jsonObject.optString("config", "");
            moduleInfo.config = config.isEmpty() ? null : config;
            PropUtils.applyFallbacks(moduleInfo); // Apply fallbacks
            Log.d(TAG, "Module " + moduleInfo.name + " " + moduleInfo.id + " " +
                    moduleInfo.version + " " + moduleInfo.versionCode);
        }
        Iterator<RepoModule> moduleInfoIterator = this.moduleHashMap.values().iterator();
        while (moduleInfoIterator.hasNext()) {
            RepoModule repoModule = moduleInfoIterator.next();
            if (!repoModule.processed) {
                moduleInfoIterator.remove();
            }
        }
        this.lastUpdate = lastLastUpdate;
        this.name = name;
        return newModules;
    }

    private static String filterURL(String url) {
        if (url == null || url.isEmpty() || PropUtils.isInvalidURL(url)) {
            return null;
        }
        return url;
    }

    @Override
    public void storeMetadata(RepoModule repoModule, byte[] data) {}

    @Override
    public boolean tryLoadMetadata(RepoModule repoModule) {
        if (this.moduleHashMap.containsKey(repoModule.id)) {
            repoModule.moduleInfo.flags &=
                    ~ModuleInfo.FLAG_METADATA_INVALID;
            return true;
        }
        repoModule.moduleInfo.flags |=
                ModuleInfo.FLAG_METADATA_INVALID;
        return false;
    }
}
