package com.fox2code.mmm.repo;

import android.content.SharedPreferences;

import java.io.File;

public class LimitedRepoData extends RepoData {
    LimitedRepoData(String url, File cacheRoot, SharedPreferences cachedPreferences) {
        super(url, cacheRoot, cachedPreferences);
    }

    @Override
    public final boolean isEnabledByDefault() {
        return false;
    }

    @Override
    public final boolean isLimited() {
        return true;
    }
}
