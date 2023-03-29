package com.fox2code.mmm.repo;

import android.content.SharedPreferences;

import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.utils.io.PropUtils;
import com.fox2code.mmm.utils.realm.ReposList;

import io.realm.Realm;
import io.realm.RealmConfiguration;

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
        SharedPreferences sharedPreferences = this.getSharedPreferences();
        int lastFilled = 0;
        for (int i = 0; i < MAX_CUSTOM_REPOS; i++) {
            RealmConfiguration realmConfiguration = new RealmConfiguration.Builder().name("ReposList.realm").encryptionKey(MainApplication.getINSTANCE().getExistingKey()).allowQueriesOnUiThread(true).allowWritesOnUiThread(true).directory(MainApplication.getINSTANCE().getDataDirWithPath("realms")).schemaVersion(1).build();
            Realm realm = Realm.getInstance(realmConfiguration);
            if (realm.isInTransaction()) {
                realm.commitTransaction();
            }
            realm.beginTransaction();
            // find the matching entry for repo_0, repo_1, etc.
            ReposList reposList = realm.where(ReposList.class).equalTo("id", "repo_" + i).findFirst();
            if (reposList == null) {
                continue;
            }
            String repo = reposList.getUrl();
            if (!PropUtils.isNullString(repo) && !RepoManager.isBuiltInRepo(repo)) {
                lastFilled = i;
                int index = AUTO_RECOMPILE ? this.customReposCount : i;
                this.customRepos[index] = repo;
                this.customReposCount++;
                ((CustomRepoData) this.repoManager.addOrGet(repo)).override = "custom_repo_" + index;
            }
        }
        if (AUTO_RECOMPILE && (lastFilled + 1) != this.customReposCount) {
            RealmConfiguration realmConfiguration = new RealmConfiguration.Builder().name("ReposList.realm").encryptionKey(MainApplication.getINSTANCE().getExistingKey()).allowQueriesOnUiThread(true).allowWritesOnUiThread(true).directory(MainApplication.getINSTANCE().getDataDirWithPath("realms")).schemaVersion(1).build();
            Realm realm = Realm.getInstance(realmConfiguration);
            if (realm.isInTransaction()) {
                realm.commitTransaction();
            }
            realm.beginTransaction();
            for (int i = 0; i < MAX_CUSTOM_REPOS; i++) {
                if (this.customRepos[i] != null) {
                    // find the matching entry for repo_0, repo_1, etc.
                    ReposList reposList = realm.where(ReposList.class).equalTo("id", "repo_" + i).findFirst();
                    if (reposList == null) {
                        continue;
                    }
                    reposList.setUrl(this.customRepos[i]);
                }
            }
        }
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
        RealmConfiguration realmConfiguration = new RealmConfiguration.Builder().name("ReposList.realm").encryptionKey(MainApplication.getINSTANCE().getExistingKey()).allowQueriesOnUiThread(true).allowWritesOnUiThread(true).directory(MainApplication.getINSTANCE().getDataDirWithPath("realms")).schemaVersion(1).build();
        Realm realm = Realm.getInstance(realmConfiguration);
        if (realm.isInTransaction()) {
            realm.commitTransaction();
        }
        realm.beginTransaction();
        // find the matching entry for repo_0, repo_1, etc.
        ReposList reposList = realm.where(ReposList.class).equalTo("id", "repo_" + i).findFirst();
        if (reposList == null) {
            reposList = realm.createObject(ReposList.class, "repo_" + i);
        }
        reposList.setUrl(repo);
        realm.commitTransaction();
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
