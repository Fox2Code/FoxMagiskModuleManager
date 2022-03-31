package com.fox2code.mmm;

import android.util.Log;

import com.fox2code.mmm.utils.Files;
import com.fox2code.mmm.utils.Http;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

// See https://docs.github.com/en/rest/reference/repos#releases
public class AppUpdateManager {
    public static int FLAG_COMPAT_LOW_QUALITY = 0x01;
    public static int FLAG_COMPAT_NO_EXT = 0x02;
    public static int FLAG_COMPAT_MAGISK_CMD = 0x04;
    public static int FLAG_COMPAT_NEED_32BIT = 0x08;
    private static final String TAG = "AppUpdateManager";
    private static final AppUpdateManager INSTANCE = new AppUpdateManager();
    private static final String RELEASES_API_URL =
            "https://api.github.com/repos/Fox2Code/FoxMagiskModuleManager/releases";
    private static final String COMPAT_API_URL =
            "https://api.github.com/repos/Fox2Code/FoxMagiskModuleManager/issues/4";

    public static AppUpdateManager getAppUpdateManager() {
        return INSTANCE;
    }

    private final HashMap<String, Integer> compatDataId = new HashMap<>();
    private final Object updateLock = new Object();
    private final File compatFile;
    private String latestRelease;
    private String latestPreRelease;
    private long lastChecked;
    private boolean preReleaseNewer;
    private boolean lastCheckSuccess;

    private AppUpdateManager() {
        this.compatFile = new File(MainApplication.getINSTANCE().getFilesDir(), "compat.txt");
        this.latestRelease = MainApplication.getBootSharedPreferences()
                .getString("updater_latest_release", BuildConfig.VERSION_NAME);
        this.latestPreRelease = MainApplication.getBootSharedPreferences()
                .getString("updater_latest_pre_release", BuildConfig.VERSION_NAME);
        this.lastChecked = 0;
        this.preReleaseNewer = true;
        if (this.compatFile.isFile()) {
            try {
                this.parseCompatibilityFlags(new FileInputStream(this.compatFile));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Return true if should show a notification
    public boolean checkUpdate(boolean force) {
        if (!BuildConfig.ENABLE_AUTO_UPDATER)
            return false;
        if (!force && this.peekShouldUpdate())
            return true;
        long lastChecked = this.lastChecked;
        if (lastChecked != 0 &&
                // Avoid spam calls by putting a 60 seconds timer
                lastChecked < System.currentTimeMillis() - 60000L)
            return force && this.peekShouldUpdate();
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
                this.lastCheckSuccess = true;
            } catch (Exception ioe) {
                this.lastCheckSuccess = false;
                Log.e("AppUpdateManager", "Failed to check releases", ioe);
            }
        }
        return this.peekShouldUpdate();
    }

    public void checkUpdateCompat() {
        if (this.compatFile.exists()) {
            long lastUpdate = this.compatFile.lastModified();
            if (lastUpdate <= System.currentTimeMillis() &&
                    lastUpdate + 600_000L > System.currentTimeMillis()) {
                return; // Skip update
            }
        }
        try {
            JSONObject object = new JSONObject(new String(Http.doHttpGet(
                    COMPAT_API_URL, false), StandardCharsets.UTF_8));
            if (object.isNull("body")) {
                compatDataId.clear();
                Files.write(compatFile, new byte[0]);
                return;
            }
            byte[] rawData = object.getString("body")
                    .getBytes(StandardCharsets.UTF_8);
            this.parseCompatibilityFlags(new ByteArrayInputStream(rawData));
            Files.write(compatFile, rawData);
        } catch (Exception e) {
            Log.e("AppUpdateManager", "Failed to update compat list", e);
        }
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

    public boolean isLastCheckSuccess() {
        return lastCheckSuccess;
    }

    private void parseCompatibilityFlags(InputStream inputStream) throws IOException {
        compatDataId.clear();
        BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int i = line.indexOf('/');
            if (i == -1) continue;
            int value = 0;
            for (String arg : line.substring(i + 1).split(",")) {
                switch (arg) {
                    default:
                        break;
                    case "lowQuality":
                        value |= FLAG_COMPAT_LOW_QUALITY;
                        break;
                    case "noExt":
                        value |= FLAG_COMPAT_NO_EXT;
                        break;
                    case "magiskCmd":
                        value |= FLAG_COMPAT_MAGISK_CMD;
                        break;
                    case "need32bit":
                        value |= FLAG_COMPAT_NEED_32BIT;
                        break;
                }
            }
            compatDataId.put(line.substring(0, i), value);
        }
    }

    public int getCompatibilityFlags(String moduleId) {
        Integer compatFlags = compatDataId.get(moduleId);
        return compatFlags == null ? 0 : compatFlags;
    }

    public static int getFlagsForModule(String moduleId) {
        return INSTANCE.getCompatibilityFlags(moduleId);
    }
}
