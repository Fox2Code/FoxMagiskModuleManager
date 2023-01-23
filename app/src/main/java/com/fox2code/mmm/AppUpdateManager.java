package com.fox2code.mmm;

import com.fox2code.mmm.utils.io.Files;
import com.fox2code.mmm.utils.io.Http;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import timber.log.Timber;

// See https://docs.github.com/en/rest/reference/repos#releases
public class AppUpdateManager {
    public static final int FLAG_COMPAT_LOW_QUALITY = 0x0001;
    public static final int FLAG_COMPAT_NO_EXT = 0x0002;
    public static final int FLAG_COMPAT_MAGISK_CMD = 0x0004;
    public static final int FLAG_COMPAT_NEED_32BIT = 0x0008;
    public static final int FLAG_COMPAT_MALWARE = 0x0010;
    public static final int FLAG_COMPAT_NO_ANSI = 0x0020;
    public static final int FLAG_COMPAT_FORCE_ANSI = 0x0040;
    public static final int FLAG_COMPAT_FORCE_HIDE = 0x0080;
    public static final int FLAG_COMPAT_MMT_REBORN = 0x0100;
    public static final int FLAG_COMPAT_ZIP_WRAPPER = 0x0200;
    private static final AppUpdateManager INSTANCE = new AppUpdateManager();
    private static final String RELEASES_API_URL = "https://api.github.com/repos/Fox2Code/FoxMagiskModuleManager/releases";
    private final HashMap<String, Integer> compatDataId = new HashMap<>();
    private final Object updateLock = new Object();
    private final File compatFile;
    private String latestRelease;
    private String latestPreRelease;
    private long lastChecked;
    private boolean preReleaseNewer;

    private AppUpdateManager() {
        this.compatFile = new File(MainApplication.getINSTANCE().getFilesDir(), "compat.txt");
        this.latestRelease = MainApplication.getBootSharedPreferences().getString("updater_latest_release", BuildConfig.VERSION_NAME);
        this.latestPreRelease = MainApplication.getBootSharedPreferences().getString("updater_latest_pre_release", BuildConfig.VERSION_NAME);
        this.lastChecked = 0;
        this.preReleaseNewer = true;
        if (this.compatFile.isFile()) {
            try {
                this.parseCompatibilityFlags(new FileInputStream(this.compatFile));
            } catch (
                    IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static AppUpdateManager getAppUpdateManager() {
        return INSTANCE;
    }

    public static int getFlagsForModule(String moduleId) {
        return INSTANCE.getCompatibilityFlags(moduleId);
    }

    public static boolean shouldForceHide(String repoId) {
        if (BuildConfig.DEBUG || repoId.startsWith("repo_") || repoId.equals("magisk_alt_repo"))
            return false;
        return !repoId.startsWith("repo_") && (INSTANCE.getCompatibilityFlags(repoId) & FLAG_COMPAT_FORCE_HIDE) != 0;
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
                JSONArray releases = new JSONArray(new String(Http.doHttpGet(RELEASES_API_URL, false), StandardCharsets.UTF_8));
                String latestRelease = null, latestPreRelease = null;
                for (int i = 0; i < releases.length(); i++) {
                    JSONObject release = releases.getJSONObject(i);
                    // Skip invalid entries
                    if (release.getBoolean("draft"))
                        continue;
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
                if (BuildConfig.DEBUG)
                    Timber.d("Latest release: %s", latestRelease);
                if (BuildConfig.DEBUG)
                    Timber.d("Latest pre-release: %s", latestPreRelease);
                if (BuildConfig.DEBUG)
                    Timber.d("Latest pre-release newer: %s", preReleaseNewer);
                this.lastChecked = System.currentTimeMillis();
            } catch (
                    Exception ioe) {
                Timber.e(ioe);
            }
        }
        return this.peekShouldUpdate();
    }

    public void checkUpdateCompat() {
        compatDataId.clear();
        try {
            Files.write(compatFile, new byte[0]);
        } catch (
                IOException e) {
            e.printStackTrace();
        }
        // There once lived an implementation that used a GitHub API to get the compatibility flags. It was removed because it was too slow and the API was rate limited.
        Timber.w("Remote compatibility data flags are not implemented.");
    }

    public boolean peekShouldUpdate() {
        if (!BuildConfig.ENABLE_AUTO_UPDATER)
            return false;
        // Convert both BuildConfig.VERSION_NAME and latestRelease to int
        int currentVersion = 0, latestVersion = 0;
        try {
            currentVersion = Integer.parseInt(BuildConfig.VERSION_NAME.replaceAll("\\D", ""));
            latestVersion = Integer.parseInt(this.latestRelease.replace("v", "").replaceAll("\\D", ""));
        } catch (
                NumberFormatException ignored) {
        }
        int latestPreReleaseVersion = 0;
        // replace all non-numeric characters with empty string
        try {
            latestPreReleaseVersion = Integer.parseInt(this.latestPreRelease.replaceAll("\\D", ""));
        } catch (
                NumberFormatException ignored) {
        }
        return currentVersion < latestVersion || (this.preReleaseNewer && currentVersion < latestPreReleaseVersion);
    }

    public boolean peekHasUpdate() {
        if (!BuildConfig.ENABLE_AUTO_UPDATER)
            return false;
        return !BuildConfig.VERSION_NAME.equals(this.preReleaseNewer ? this.latestPreRelease : this.latestRelease);
    }

    private void parseCompatibilityFlags(InputStream inputStream) throws IOException {
        compatDataId.clear();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#"))
                continue;
            int i = line.indexOf('/');
            if (i == -1)
                continue;
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
                    case "malware":
                        value |= FLAG_COMPAT_MALWARE;
                        break;
                    case "noANSI":
                        value |= FLAG_COMPAT_NO_ANSI;
                        break;
                    case "forceANSI":
                        value |= FLAG_COMPAT_FORCE_ANSI;
                        break;
                    case "forceHide":
                        value |= FLAG_COMPAT_FORCE_HIDE;
                        break;
                    case "mmtReborn":
                        value |= FLAG_COMPAT_MMT_REBORN;
                        break;
                    case "wrapper":
                        value |= FLAG_COMPAT_ZIP_WRAPPER;
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
}
