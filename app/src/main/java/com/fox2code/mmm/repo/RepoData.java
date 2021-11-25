package com.fox2code.mmm.repo;

import android.content.SharedPreferences;

import com.fox2code.mmm.manager.ModuleInfo;
import com.fox2code.mmm.utils.Files;
import com.fox2code.mmm.utils.PropUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class RepoData {
    private final Object populateLock = new Object();
    public final String url;
    public final File cacheRoot;
    public final SharedPreferences cachedPreferences;
    public final File metaDataCache;
    public final HashMap<String, RepoModule> moduleHashMap;
    public long lastUpdate;
    public String name;

    RepoData(String url, File cacheRoot, SharedPreferences cachedPreferences) {
        this.url = url;
        this.cacheRoot = cacheRoot;
        this.cachedPreferences = cachedPreferences;
        this.metaDataCache = new File(cacheRoot, "modules.json");
        this.moduleHashMap = new HashMap<>();
        this.name = this.url; // Set url as default name
        if (!this.cacheRoot.isDirectory()) {
            this.cacheRoot.mkdirs();
        } else if (this.metaDataCache.exists()) {
            try {
                List<RepoModule> modules = this.populate(new JSONObject(
                        new String(Files.read(this.metaDataCache), StandardCharsets.UTF_8)));
                for (RepoModule repoModule: modules) {
                    if (!this.tryLoadMetadata(repoModule)) {
                        repoModule.moduleInfo.flags &=~ ModuleInfo.FLAG_METADATA_INVALID;
                    }
                }
            } catch (Exception e) {
                this.metaDataCache.delete();
            }
        }
    }

    List<RepoModule> populate(JSONObject jsonObject) throws JSONException {
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
                long moduleLastUpdate = module.getLong("last_update");
                String moduleNotesUrl = module.getString("notes_url");
                String modulePropsUrl = module.getString("prop_url");
                String moduleZipUrl = module.getString("zip_url");
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

    public boolean tryLoadMetadata(RepoModule repoModule) {
        File file = new File(this.cacheRoot, repoModule.id + ".prop");
        if (file.exists()) {
            try {
                PropUtils.readProperties(repoModule.moduleInfo, file.getAbsolutePath());
                repoModule.moduleInfo.flags &= ~ModuleInfo.FLAG_METADATA_INVALID;
                return true;
            } catch (Exception ignored) {
                file.delete();
            }
        }
        repoModule.moduleInfo.flags |= ModuleInfo.FLAG_METADATA_INVALID;
        return false;
    }

    public String getNameOrFallback(String fallback) {
        return this.name == null ||
                this.name.equals(this.url) ?
                fallback : this.name;
    }
}
