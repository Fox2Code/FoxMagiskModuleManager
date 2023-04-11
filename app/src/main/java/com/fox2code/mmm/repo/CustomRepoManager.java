package com.fox2code.mmm.repo;

import android.content.SharedPreferences;

import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.utils.io.PropUtils;
import com.fox2code.mmm.utils.io.net.Http;
import com.fox2code.mmm.utils.realm.ReposList;

import org.json.JSONObject;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import timber.log.Timber;

public class CustomRepoManager {
    public static final int MAX_CUSTOM_REPOS = 5;
    private static final boolean AUTO_RECOMPILE = true;
    private final RepoManager repoManager;
    private final String[] customRepos;
    boolean dirty;
    private int customReposCount;

    @SuppressWarnings("unused")
    CustomRepoManager(MainApplication mainApplication, RepoManager repoManager) {
        this.repoManager = repoManager;
        this.customRepos = new String[MAX_CUSTOM_REPOS];
        this.customReposCount = 0;
        // refuse to load if setup is not complete
        if (MainApplication.getPreferences("mmm").getString("last_shown_setup", "").equals("")) {
            return;
        }
        RealmConfiguration realmConfiguration = new RealmConfiguration.Builder().name("ReposList.realm").encryptionKey(MainApplication.getINSTANCE().getExistingKey()).allowQueriesOnUiThread(true).allowWritesOnUiThread(true).directory(MainApplication.getINSTANCE().getDataDirWithPath("realms")).schemaVersion(1).build();
        Realm realm = Realm.getInstance(realmConfiguration);
        if (realm.isInTransaction()) {
            realm.commitTransaction();
        }
        int i = 0;
        @SuppressWarnings("MismatchedReadAndWriteOfArray") final int[] lastFilled = {0};
        realm.executeTransaction(realm1 -> {
            // find all repos that are not built-in
            for (ReposList reposList : realm1.where(ReposList.class).notEqualTo("id", "androidacy_repo").and().notEqualTo("id", "magisk_alt_repo").and().notEqualTo("id", "magisk_official_repo").findAll()) {
                String repo = reposList.getUrl();
                if (!PropUtils.isNullString(repo) && !RepoManager.isBuiltInRepo(repo)) {
                    lastFilled[0] = i;
                    int index = AUTO_RECOMPILE ? this.customReposCount : i;
                    this.customRepos[index] = repo;
                    this.customReposCount++;
                    ((CustomRepoData) this.repoManager.addOrGet(repo)).override = "custom_repo_" + index;
                }
            }
        });
    }

    private SharedPreferences getSharedPreferences() {
        return MainApplication.getPreferences("mmm_custom_repos");
    }

    public CustomRepoData addRepo(String repo) {
        if (RepoManager.isBuiltInRepo(repo))
            throw new IllegalArgumentException("Can't add built-in repo to custom repos");
        for (String repoEntry : this.customRepos) {
            if (repo.equals(repoEntry)) return (CustomRepoData) this.repoManager.get(repoEntry);
        }
        int i = 0;
        while (customRepos[i] != null) i++;
        customRepos[i] = repo;
        // fetch that sweet sweet json
        byte[] json;
        try {
            json = Http.doHttpGet(repo, false);
        } catch (Exception e) {
            Timber.e(e, "Failed to fetch json from repo");
            return null;
        }
        // get website, support, donate, submitModule. all optional. name is required.
        // parse json
        JSONObject jsonObject;
        try {
            jsonObject = new JSONObject(new String(json));
        } catch (Exception e) {
            Timber.e(e, "Failed to parse json from repo");
            return null;
        }
        // get name
        String name;
        try {
            name = jsonObject.getString("name");
        } catch (Exception e) {
            Timber.e(e, "Failed to get name from json");
            return null;
        }
        // get website
        String website;
        try {
            website = jsonObject.getString("website");
        } catch (Exception e) {
            website = null;
        }
        // get support
        String support;
        try {
            support = jsonObject.getString("support");
        } catch (Exception e) {
            support = null;
        }
        // get donate
        String donate;
        try {
            donate = jsonObject.getString("donate");
        } catch (Exception e) {
            donate = null;
        }
        // get submitModule
        String submitModule;
        try {
            submitModule = jsonObject.getString("submitModule");
        } catch (Exception e) {
            submitModule = null;
        }
        RealmConfiguration realmConfiguration = new RealmConfiguration.Builder().name("ReposList.realm").encryptionKey(MainApplication.getINSTANCE().getExistingKey()).allowQueriesOnUiThread(true).allowWritesOnUiThread(true).directory(MainApplication.getINSTANCE().getDataDirWithPath("realms")).schemaVersion(1).build();
        Realm realm = Realm.getInstance(realmConfiguration);
        int finalI = i;
        String finalWebsite = website;
        String finalSupport = support;
        String finalDonate = donate;
        String finalSubmitModule = submitModule;
        realm.executeTransaction(realm1 -> {
            // find the matching entry for repo_0, repo_1, etc.
            ReposList reposList = realm1.where(ReposList.class).equalTo("id", "repo_" + finalI).findFirst();
            if (reposList == null) {
                reposList = realm1.createObject(ReposList.class, "repo_" + finalI);
            }
            reposList.setUrl(repo);
            reposList.setName(name);
            reposList.setWebsite(finalWebsite);
            reposList.setSupport(finalSupport);
            reposList.setDonate(finalDonate);
            reposList.setSubmitModule(finalSubmitModule);
            reposList.setEnabled(true);
            // save the object
            realm1.copyToRealmOrUpdate(reposList);
        });
        customReposCount++;
        this.dirty = true;
        CustomRepoData customRepoData = (CustomRepoData) this.repoManager.addOrGet(repo);
        customRepoData.override = "custom_repo_" + i;
        // Set the enabled state to true
        customRepoData.setEnabled(true);
        customRepoData.updateEnabledState();
        return customRepoData;
    }

    public CustomRepoData getRepo(int index) {
        if (index >= MAX_CUSTOM_REPOS) return null;
        String repo = customRepos[index];
        return repo == null ? null : (CustomRepoData) this.repoManager.get(repo);
    }

    public void removeRepo(int index) {
        String oldRepo = customRepos[index];
        if (oldRepo != null) {
            customRepos[index] = null;
            customReposCount--;
            CustomRepoData customRepoData = (CustomRepoData) this.repoManager.get(oldRepo);
            if (customRepoData != null) {
                customRepoData.setEnabled(false);
                customRepoData.override = null;
            }
            this.getSharedPreferences().edit().remove("repo_" + index).apply();
            this.dirty = true;
        }
    }

    public boolean hasRepo(String repo) {
        for (String repoEntry : this.customRepos) {
            if (repo.equals(repoEntry)) return true;
        }
        return false;
    }

    public boolean canAddRepo() {
        return this.customReposCount < MAX_CUSTOM_REPOS;
    }

    public boolean canAddRepo(String repo) {
        if (RepoManager.isBuiltInRepo(repo) || this.hasRepo(repo) || !this.canAddRepo())
            return false;
        return repo.startsWith("https://") && repo.indexOf('/', 9) != -1;
    }

    public int getRepoCount() {
        return this.customReposCount;
    }

    public boolean needUpdate() {
        boolean needUpdate = this.dirty;
        if (needUpdate) this.dirty = false;
        return needUpdate;
    }

}
