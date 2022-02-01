package com.fox2code.mmm.repo;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.fox2code.mmm.R;
import com.fox2code.mmm.manager.ModuleInfo;
import com.fox2code.mmm.utils.Files;
import com.fox2code.mmm.utils.Http;
import com.fox2code.mmm.utils.PropUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RepoData {
    private static final String TAG = "RepoData";
    private final Object populateLock = new Object();
    public final String url;
    public final File cacheRoot;
    public final SharedPreferences cachedPreferences;
    public final File metaDataCache;
    public final boolean special;
    public final HashMap<String, RepoModule> moduleHashMap;
    public long lastUpdate;
    public String name;
    private final Map<String, SpecialData> specialData;
    private long specialLastUpdate;

    protected RepoData(String url, File cacheRoot, SharedPreferences cachedPreferences) {
        this(url, cacheRoot, cachedPreferences, false);
    }

    RepoData(String url, File cacheRoot, SharedPreferences cachedPreferences,boolean special) {
        this.url = url;
        this.cacheRoot = cacheRoot;
        this.cachedPreferences = cachedPreferences;
        this.metaDataCache = new File(cacheRoot, "modules.json");
        this.special = special;
        this.moduleHashMap = new HashMap<>();
        this.name = this.url; // Set url as default name
        this.specialData = special ? new HashMap<>() : Collections.emptyMap();
        if (!this.cacheRoot.isDirectory()) {
            this.cacheRoot.mkdirs();
        } else {
            if (special) { // Special times need to be loaded before populate
                File metaDataCacheSpecial = new File(cacheRoot, "modules_times.json");
                if (metaDataCacheSpecial.exists()) {
                    try {
                        JSONArray jsonArray = new JSONArray(new String(
                                Files.read(this.metaDataCache), StandardCharsets.UTF_8));
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject jsonObject = jsonArray.getJSONObject(i);
                            this.specialData.put(
                                    jsonObject.getString("name"), new SpecialData(
                                            Objects.requireNonNull(ISO_OFFSET_DATE_TIME.parse(
                                                    jsonObject.getString(
                                                            "pushed_at"))).getTime(),
                                            jsonObject.optInt("stargazers_count")));
                            Log.d(TAG, "Got " +
                                    jsonObject.getString("name") + " from local storage!");
                        }
                        this.specialLastUpdate = metaDataCacheSpecial.lastModified();
                        if (this.specialLastUpdate > System.currentTimeMillis()) {
                            this.specialLastUpdate = 0; // Don't allow time travel
                        }
                    } catch (Exception e) {
                        metaDataCacheSpecial.delete();
                    }
                }
            }
            if (this.metaDataCache.exists()) {
                this.lastUpdate = metaDataCache.lastModified();
                if (this.lastUpdate > System.currentTimeMillis()) {
                    this.lastUpdate = 0; // Don't allow time travel
                }
                try {
                    List<RepoModule> modules = this.populate(new JSONObject(
                            new String(Files.read(this.metaDataCache), StandardCharsets.UTF_8)));
                    for (RepoModule repoModule : modules) {
                        if (!this.tryLoadMetadata(repoModule)) {
                            repoModule.moduleInfo.flags &= ~ModuleInfo.FLAG_METADATA_INVALID;
                        }
                    }
                } catch (Exception e) {
                    this.metaDataCache.delete();
                }
            }
        }
    }

    protected boolean prepare() {
        return true;
    }

    protected List<RepoModule> populate(JSONObject jsonObject) throws JSONException {
        List<RepoModule> newModules = new ArrayList<>();
        synchronized (this.populateLock) {
            String name = jsonObject.getString("name").trim();
            String nameForModules = name.endsWith(" (Official)") ?
                    name.substring(0, name.length() - 11) : name;
            long lastUpdate = jsonObject.getLong("last_update");
            for (RepoModule repoModule : this.moduleHashMap.values()) {
                repoModule.processed = false;
            }
            JSONArray array = jsonObject.getJSONArray("modules");
            int len = array.length();
            for (int i = 0; i < len; i++) {
                JSONObject module = array.getJSONObject(i);
                String moduleId = module.getString("id");
                // Deny remote modules ids shorter than 3 chars long or that start with a digit
                if (moduleId.length() < 3 || Character.isDigit(moduleId.charAt(0))) continue;
                SpecialData moduleSpecialData = this.specialData.get(moduleId);
                long moduleLastUpdate = module.getLong("last_update");
                String moduleNotesUrl = module.getString("notes_url");
                String modulePropsUrl = module.getString("prop_url");
                String moduleZipUrl = module.getString("zip_url");
                String moduleChecksum = module.optString("checksum");
                String moduleStars = module.optString("stars");
                if (moduleSpecialData != null) { // Fix last update time
                    moduleLastUpdate = Math.max(moduleLastUpdate, moduleSpecialData.time);
                    moduleNotesUrl = Http.updateLink(moduleNotesUrl);
                    modulePropsUrl = Http.updateLink(modulePropsUrl);
                    moduleZipUrl = Http.updateLink(moduleZipUrl);
                } else {
                    moduleNotesUrl = Http.fixUpLink(moduleNotesUrl);
                    modulePropsUrl = Http.fixUpLink(modulePropsUrl);
                    moduleZipUrl = Http.fixUpLink(moduleZipUrl);
                }
                RepoModule repoModule = this.moduleHashMap.get(moduleId);
                if (repoModule == null) {
                    repoModule = new RepoModule(moduleId);
                    this.moduleHashMap.put(moduleId, repoModule);
                    newModules.add(repoModule);
                } else {
                    if (repoModule.lastUpdated < moduleLastUpdate ||
                            repoModule.moduleInfo.hasFlag(ModuleInfo.FLAG_METADATA_INVALID)) {
                        newModules.add(repoModule);
                    }
                }
                repoModule.processed = true;
                repoModule.repoName = nameForModules;
                repoModule.lastUpdated = moduleLastUpdate;
                repoModule.notesUrl = moduleNotesUrl;
                repoModule.propUrl = modulePropsUrl;
                repoModule.zipUrl = moduleZipUrl;
                repoModule.checksum = moduleChecksum;
                if (moduleSpecialData != null) {
                    repoModule.qualityValue = moduleSpecialData.stars;
                    repoModule.qualityText = R.string.module_stars;
                } else if (!moduleStars.isEmpty()) {
                    try {
                        repoModule.qualityValue = Integer.parseInt(moduleStars);
                        repoModule.qualityText = R.string.module_stars;
                    } catch (NumberFormatException ignored) {}
                }
            }
            // Remove no longer existing modules
            Iterator<RepoModule> moduleInfoIterator = this.moduleHashMap.values().iterator();
            while (moduleInfoIterator.hasNext()) {
                RepoModule repoModule = moduleInfoIterator.next();
                if (!repoModule.processed) {
                    new File(this.cacheRoot, repoModule.id + ".prop").delete();
                    moduleInfoIterator.remove();
                }
            }
            // Update final metadata
            this.name = name;
            this.lastUpdate = lastUpdate;
        }
        return newModules;
    }

    public void storeMetadata(RepoModule repoModule,byte[] data) throws IOException {
        Files.write(new File(this.cacheRoot, repoModule.id + ".prop"), data);
    }

    public boolean tryLoadMetadata(RepoModule repoModule) {
        File file = new File(this.cacheRoot, repoModule.id + ".prop");
        if (file.exists()) {
            try {
                ModuleInfo moduleInfo = repoModule.moduleInfo;
                PropUtils.readProperties(moduleInfo, file.getAbsolutePath(),
                        repoModule.repoName + "/" + moduleInfo.name, false);
                moduleInfo.flags &= ~ModuleInfo.FLAG_METADATA_INVALID;
                if (moduleInfo.version == null) {
                    moduleInfo.version = "v" + moduleInfo.versionCode;
                }
                SpecialData moduleSpecialData =
                        this.specialData.get(repoModule.id);
                if (moduleSpecialData != null) {
                    repoModule.qualityValue = moduleSpecialData.stars;
                    repoModule.qualityText = R.string.module_stars;
                }
                return true;
            } catch (Exception ignored) {
                file.delete();
            }
        }
        repoModule.moduleInfo.flags |= ModuleInfo.FLAG_METADATA_INVALID;
        return false;
    }

    @SuppressLint("SimpleDateFormat")
    private static final SimpleDateFormat ISO_OFFSET_DATE_TIME =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    public void updateSpecialTimes(boolean force) throws IOException, JSONException {
        if (!this.special) return;
        synchronized (this.populateLock) {
            if (this.specialLastUpdate == 0L || (force && (this.specialData.isEmpty() ||
                    this.specialLastUpdate < System.currentTimeMillis() - 60000L))) {
                File metaDataCacheSpecial = new File(cacheRoot, "modules_times.json");
                this.specialData.clear();
                try {
                    // Requesting only 32 most recently pushed repos
                    byte[] data = Http.doHttpGet(
                            "https://api.github.com/users/Magisk-Modules-Repo/" +
                                    "repos?sort=pushed&per_page=32", false);
                    JSONArray jsonArray = new JSONArray(new String(data, StandardCharsets.UTF_8));
                    for (int i = 0;i < jsonArray.length();i++) {
                        JSONObject jsonObject = jsonArray.optJSONObject(i);
                        this.specialData.put(
                                jsonObject.getString("name"), new SpecialData(
                                        Objects.requireNonNull(ISO_OFFSET_DATE_TIME.parse(
                                                jsonObject.getString(
                                                        "pushed_at"))).getTime(),
                                        jsonObject.optInt("stargazers_count")));
                    }
                    Files.write(metaDataCacheSpecial, data);
                    this.specialLastUpdate = System.currentTimeMillis();
                } catch (ParseException e) {
                    throw new IOException(e);
                }
            }
        }
    }

    public String getNameOrFallback(String fallback) {
        return this.name == null ||
                this.name.equals(this.url) ?
                fallback : this.name;
    }

    private static class SpecialData {
        SpecialData(long time, int stars) {
            this.time = time; this.stars = stars;
        }

        final long time;
        final int stars;

        @NonNull
        @Override
        public String toString() {
            return "SpecialData{" +
                    "time=" + time +
                    ", stars=" + stars +
                    '}';
        }
    }
}
