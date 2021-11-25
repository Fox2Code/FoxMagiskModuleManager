package com.fox2code.mmm;

import android.util.Log;

import com.fox2code.mmm.utils.Http;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

// See https://docs.github.com/en/rest/reference/repos#releases
public class AppUpdateManager {
    private static final String TAG = "AppUpdateManager";
    private static final AppUpdateManager INSTANCE = new AppUpdateManager();
    private static final String RELEASES_API_URL =
            "https://api.github.com/repos/Fox2Code/FoxMagiskModuleManager/releases";

    public static AppUpdateManager getAppUpdateManager() {
        return INSTANCE;
    }

    private final Object updateLock = new Object();
    private String latestRelease;
    private String latestPreRelease;
    private long lastChecked;
    private boolean preReleaseNewer;

    private AppUpdateManager() {
        this.latestRelease = MainApplication.getBootSharedPreferences()
                .getString("updater_latest_release", BuildConfig.VERSION_NAME);
        this.latestPreRelease = MainApplication.getBootSharedPreferences()
                .getString("updater_latest_pre_release", BuildConfig.VERSION_NAME);
        this.lastChecked = 0;
        this.preReleaseNewer = true;
    }

    // Return true if should show a notification
    public boolean checkUpdate(boolean force) {
        if (this.peekShouldUpdate())
            return true;
        long lastChecked = this.lastChecked;
        if (!force && lastChecked != 0 &&
                // Avoid spam calls by putting a 10 seconds timer
                lastChecked < System.currentTimeMillis() - 10000L)
            return false;
        synchronized (this.updateLock) {
            if (lastChecked != this.lastChecked)
                return this.peekShouldUpdate();
            boolean preReleaseNewer = true;
            try {
                JSONArray releases = new JSONArray(new String(Http.doHttpGet(
                        RELEASES_API_URL, false), StandardCharsets.UTF_8));
                String latestRelease = null, latestPreRelease = null;
                for (int i = 0; i < releases.length(); i++) {
                    JSONObject release = releases.getJSONObject(i);
                    // Skip invalid entries
                    if (release.getBoolean("draft")) continue;
                    boolean preRelease = release.getBoolean("prerelease");
                    String version = release.getString("tag_name");
                    if (version.startsWith("v"))
                        version = version.substring(1);
                    if (preRelease) {
                        if (latestPreRelease == null)
                            latestPreRelease = version;
                    } else if (latestRelease == null) {
                        latestRelease = version;
                        if (latestPreRelease == null)
                            preReleaseNewer = false;
                    }
                    if (latestRelease != null && latestPreRelease != null) {
                        break; // We read everything we needed to read.
                    }
                }
                if (latestRelease != null)
                    this.latestRelease = latestRelease;
                if (latestPreRelease != null) {
                    this.latestPreRelease = latestPreRelease;
                    this.preReleaseNewer = preReleaseNewer;
                } else if (!preReleaseNewer) {
                    this.latestPreRelease = "";
                    this.preReleaseNewer = false;
                }
                Log.d(TAG, "Latest release: " + latestRelease);
                Log.d(TAG, "Latest pre-release: " + latestPreRelease);
                Log.d(TAG, "Latest pre-release newer: " + preReleaseNewer);
                this.lastChecked = System.currentTimeMillis();
            } catch (Exception ioe) {
                Log.e("AppUpdateManager", "Failed to check releases", ioe);
            }
        }
        return this.peekShouldUpdate();
    }

    public boolean peekShouldUpdate() {
        return !(BuildConfig.VERSION_NAME.equals(this.latestRelease) ||
                (this.preReleaseNewer &&
                        BuildConfig.VERSION_NAME.equals(this.latestPreRelease)));
    }

    public boolean peekHasUpdate() {
        return !BuildConfig.VERSION_NAME.equals(this.preReleaseNewer ?
                this.latestPreRelease : this.latestRelease);
    }
}
