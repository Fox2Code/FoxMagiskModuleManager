package com.fox2code.mmm.repo;

import android.content.Context;
import android.content.SharedPreferences;

import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.utils.io.PropUtils;

public class CustomRepoManager {
    private static final boolean AUTO_RECOMPILE = true;
    public static final int MAX_CUSTOM_REPOS = 5;
    private final MainApplication mainApplication;
    private final RepoManager repoManager;
    private final String[] customRepos;
    private int customReposCount;
    boolean dirty;

    CustomRepoManager(MainApplication mainApplication, RepoManager repoManager) {
        this.mainApplication = mainApplication;
        this.repoManager = repoManager;
        this.customRepos = new String[MAX_CUSTOM_REPOS];
        this.customReposCount = 0;
        SharedPreferences sharedPreferences = this.getSharedPreferences();
        int lastFilled = 0;
        for (int i = 0; i < MAX_CUSTOM_REPOS; i++) {
            String repo = sharedPreferences.getString("repo_" + i, "");
            if (!PropUtils.isNullString(repo) && !RepoManager.isBuiltInRepo(repo)) {
                lastFilled = i;
                int index = AUTO_RECOMPILE ?
                        this.customReposCount : i;
                this.customRepos[index] = repo;
                this.customReposCount++;
                ((CustomRepoData) this.repoManager.addOrGet(repo))
                        .override = "custom_repo_" + index;
            }
        }
        if (AUTO_RECOMPILE && (lastFilled + 1) != this.customReposCount) {
            SharedPreferences.Editor editor = sharedPreferences.edit().clear();
            for (int i = 0; i < MAX_CUSTOM_REPOS; i++) {
                if (this.customRepos[i] != null)
                    editor.putString("repo_" + i, this.customRepos[i]);
            }
            editor.apply();
        }
    }

    private SharedPreferences getSharedPreferences() {
        return this.mainApplication.getSharedPreferences(
                "mmm_custom_repos", Context.MODE_PRIVATE);
    }

    public CustomRepoData addRepo(String repo) {
        if (RepoManager.isBuiltInRepo(repo))
            throw new IllegalArgumentException("Can't add built-in repo to custom repos");
        for (String repoEntry : this.customRepos) {
            if (repo.equals(repoEntry))
                return (CustomRepoData) this.repoManager.get(repoEntry);
        }
        int i = 0;
        while (customRepos[i] != null) i++;
        customRepos[i] = repo;
        this.getSharedPreferences().edit()
                .putString("repo_" + i, repo).apply();
        this.dirty = true;
        CustomRepoData customRepoData = (CustomRepoData)
                this.repoManager.addOrGet(repo);
        customRepoData.override = "custom_repo_" + i;
        // Set the enabled state to true
        customRepoData.setEnabled(true);
        customRepoData.updateEnabledState();
        return customRepoData;
    }

    public CustomRepoData getRepo(int index) {
        if (index >= MAX_CUSTOM_REPOS) return null;
        String repo = customRepos[index];
        return repo == null ? null :
                (CustomRepoData) this.repoManager.get(repo);
    }

    public void removeRepo(int index) {
        String oldRepo = customRepos[index];
        if (oldRepo != null) {
            customRepos[index] = null;
            customReposCount--;
            CustomRepoData customRepoData =
                    (CustomRepoData) this.repoManager.get(oldRepo);
            if (customRepoData != null) {
                customRepoData.setEnabled(false);
                customRepoData.override = null;
            }
            this.getSharedPreferences().edit()
                    .remove("repo_" + index).apply();
            this.dirty = true;
        }
    }

    public boolean hasRepo(String repo) {
        for (String repoEntry : this.customRepos) {
            if (repo.equals(repoEntry))
                return true;
        }
        return false;
    }

    public boolean canAddRepo() {
        return this.customReposCount < MAX_CUSTOM_REPOS;
    }

    public boolean canAddRepo(String repo) {
        if (RepoManager.isBuiltInRepo(repo) ||
                this.hasRepo(repo) || !this.canAddRepo())
            return false;
        return repo.startsWith("https://") &&
                repo.indexOf('/', 9) != -1;
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
