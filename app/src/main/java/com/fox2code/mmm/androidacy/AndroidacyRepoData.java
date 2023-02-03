package com.fox2code.mmm.androidacy;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.manager.ModuleInfo;
import com.fox2code.mmm.repo.RepoData;
import com.fox2code.mmm.repo.RepoManager;
import com.fox2code.mmm.repo.RepoModule;
import com.fox2code.mmm.utils.io.Http;
import com.fox2code.mmm.utils.io.HttpException;
import com.fox2code.mmm.utils.io.PropUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.topjohnwu.superuser.Shell;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import okhttp3.HttpUrl;
import timber.log.Timber;

@SuppressWarnings("KotlinInternalInJava")
public final class AndroidacyRepoData extends RepoData {
    public static String token = MainApplication.getINSTANCE().getSharedPreferences("androidacy", 0).getString("pref_androidacy_api_token", null);

    static {
        HttpUrl.Builder OK_HTTP_URL_BUILDER = new HttpUrl.Builder().scheme("https");
        // Using HttpUrl.Builder.host(String) crash the app
        OK_HTTP_URL_BUILDER.setHost$okhttp(".androidacy.com");
        OK_HTTP_URL_BUILDER.build();
    }

    @SuppressWarnings("unused")
    public final String ClientID = BuildConfig.ANDROIDACY_CLIENT_ID;
    public final SharedPreferences cachedPreferences = MainApplication.getINSTANCE().getSharedPreferences("androidacy", 0);
    private final boolean testMode;
    private final String host;
    public String[][] userInfo = new String[][]{{"role", null}, {"permissions", null}};
    public String memberLevel;
    // Avoid spamming requests to Androidacy
    private long androidacyBlockade = 0;

    public AndroidacyRepoData(File cacheRoot, SharedPreferences cachedPreferences, boolean testMode) {
        super(testMode ? RepoManager.ANDROIDACY_TEST_MAGISK_REPO_ENDPOINT : RepoManager.ANDROIDACY_MAGISK_REPO_ENDPOINT, cacheRoot, cachedPreferences);
        this.defaultName = "Androidacy Modules Repo";
        this.defaultWebsite = RepoManager.ANDROIDACY_MAGISK_REPO_HOMEPAGE;
        this.defaultSupport = "https://t.me/androidacy_discussions";
        this.defaultDonate = "https://www.androidacy.com/membership-account/membership-checkout/?level=2&discount_code=FOXWINTER2&utm_souce=foxmmm&utm_medium=android-app&utm_campaign=fox-upgrade-promo";
        this.defaultSubmitModule = "https://www.androidacy.com/module-repository-applications/";
        this.host = testMode ? "staging-api.androidacy.com" : "production-api.androidacy.com";
        this.testMode = testMode;
    }

    public static AndroidacyRepoData getInstance() {
        return RepoManager.getINSTANCE().getAndroidacyRepoData();
    }

    private static String filterURL(String url) {
        if (url == null || url.isEmpty() || PropUtils.isInvalidURL(url)) {
            return null;
        }
        return url;
    }

    // Generates a unique device ID. This is used to identify the device in the API for rate
    // limiting and fraud detection.
    public static String generateDeviceId() throws NoSuchAlgorithmException {
        // Try to get the device ID from the shared preferences
        SharedPreferences sharedPreferences = MainApplication.getINSTANCE().getSharedPreferences("androidacy", 0);
        String deviceIdPref = sharedPreferences.getString("device_id", null);
        if (deviceIdPref != null) {
            return deviceIdPref;
        } else {
            // AAAA we're fingerprintiiiiing
            // Really not that scary - just hashes some device info. We can't even get the info
            // we originally hashed, so it's not like we can use it to track you.
            String deviceId = null;
            // Get ro.serialno if it exists
            // First, we need to get an su shell
            Shell.Result result = Shell.cmd("getprop ro.serialno").exec();
            // Check if the command was successful
            if (result.isSuccess()) {
                // Get the output
                String output = result.getOut().get(0);
                // Check if the output is valid
                if (output != null && !output.isEmpty()) {
                    deviceId = output;
                }
            }
            // Now, get device model, manufacturer, and Android version originally from
            String deviceModel = android.os.Build.MODEL;
            String deviceManufacturer = android.os.Build.MANUFACTURER;
            String androidVersion = android.os.Build.VERSION.RELEASE;
            // Append it all together
            deviceId += deviceModel + deviceManufacturer + androidVersion;
            // Hash it
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(deviceId.getBytes());
            // Convert it to a hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            // Save it to shared preferences
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("device_id", hexString.toString());
            editor.apply();
            // Return it
            return hexString.toString();
        }
    }

    public boolean isValidToken(String token) throws IOException, NoSuchAlgorithmException {
        String deviceId = generateDeviceId();
        try {
            byte[] resp = Http.doHttpGet("https://" + this.host + "/auth/me?token=" + token + "&device_id=" + deviceId, false);
            // response is JSON
            JSONObject jsonObject = new JSONObject(new String(resp));
            memberLevel = jsonObject.getString("role");
            JSONArray memberPermissions = jsonObject.getJSONArray("permissions");
            // set role and permissions on userInfo property
            userInfo = new String[][]{{"role", memberLevel}, {"permissions", String.valueOf(memberPermissions)}};
            return true;
        } catch (
                HttpException e) {
            if (e.getErrorCode() == 401) {
                Timber.w("Invalid token, resetting...");
                // Remove saved preference
                SharedPreferences.Editor editor = MainApplication.getINSTANCE().getSharedPreferences("androidacy", 0).edit();
                editor.remove("pref_androidacy_api_token");
                editor.apply();
                return false;
            }
            throw e;
        } catch (
                JSONException e) {
            // response is not JSON
            Timber.w("Invalid token, resetting...");
            Timber.w(e);
            // Remove saved preference
            SharedPreferences.Editor editor = MainApplication.getINSTANCE().getSharedPreferences("androidacy", 0).edit();
            editor.remove("pref_androidacy_api_token");
            editor.apply();
            return false;
        }
    }

    @SuppressLint("RestrictedApi")
    @Override
    protected boolean prepare() throws NoSuchAlgorithmException {
        // If ANDROIDACY_CLIENT_ID is not set or is empty, disable this repo and return
        if (Objects.equals(BuildConfig.ANDROIDACY_CLIENT_ID, "")) {
            SharedPreferences.Editor editor = MainApplication.getSharedPreferences().edit();
            editor.putBoolean("pref_androidacy_repo_enabled", false);
            editor.apply();
            return false;
        }
        if (Http.needCaptchaAndroidacy())
            return false;
        // Implementation details discussed on telegram
        // First, ping the server to check if it's alive
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL("https://" + this.host + "/ping").openConnection();
            connection.setRequestMethod("GET");
            connection.setReadTimeout(5000);
            connection.connect();
            if (connection.getResponseCode() != 200 && connection.getResponseCode() != 204) {
                // If it's a 400, the app is probably outdated. Show a snackbar suggesting user update app and webview
                if (connection.getResponseCode() == 400) {
                    // Show a dialog using androidacy_update_needed string
                    new MaterialAlertDialogBuilder(MainApplication.getINSTANCE()).setTitle(R.string.androidacy_update_needed).setMessage(R.string.androidacy_update_needed_message).setPositiveButton(R.string.update, (dialog, which) -> {
                        // Open the app's page on the Play Store
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse("https://www.androidacy.com/downloads/?view=FoxMMM&utm_source=foxmnm&utm_medium=app&utm_campaign=android-app"));
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        MainApplication.getINSTANCE().startActivity(intent);
                    }).setNegativeButton(R.string.cancel, null).show();
                }
                return false;
            }
        } catch (
                Exception e) {
            Timber.e(e, "Failed to ping server");
            return false;
        }
        String deviceId = generateDeviceId();
        long time = System.currentTimeMillis();
        if (this.androidacyBlockade > time)
            return true; // fake it till you make it. Basically,
        // don't fail just becaue we're rate limited. API and web rate limits are different.
        this.androidacyBlockade = time + 30_000L;
        try {
            if (token == null) {
                token = this.cachedPreferences.getString("pref_androidacy_api_token", null);
                if (token != null && !this.isValidToken(token)) {
                    token = null;
                } else {
                    Timber.i("Using cached token");
                }
            } else if (!this.isValidToken(token)) {
                if (BuildConfig.DEBUG) {
                    throw new IllegalStateException("Invalid token: " + token);
                }
                token = null;
            }
        } catch (
                IOException e) {
            if (HttpException.shouldTimeout(e)) {
                Timber.e(e, "We are being rate limited!");
                this.androidacyBlockade = time + 3_600_000L;
            }
            return false;
        }
        if (token == null) {
            try {
                Timber.i("Requesting new token...");
                // POST json request to https://production-api.androidacy.com/auth/register
                token = new String(Http.doHttpPost("https://" + this.host + "/auth/register?client_id=" + BuildConfig.ANDROIDACY_CLIENT_ID, "{\"device_id\":\"" + deviceId + "\"}", false));
                // Parse token
                try {
                    JSONObject jsonObject = new JSONObject(token);
                    Timber.d("Token: %s", token);
                    token = jsonObject.getString("token");
                    memberLevel = jsonObject.getString("role");
                } catch (
                        JSONException e) {
                    Timber.e(e, "Failed to parse token");
                    // Show a toast
                    Looper mainLooper = Looper.getMainLooper();
                    Handler handler = new Handler(mainLooper);
                    handler.post(() -> Toast.makeText(MainApplication.getINSTANCE(), R.string.androidacy_failed_to_parse_token, Toast.LENGTH_LONG).show());
                    return false;
                }
                // Ensure token is valid
                if (!isValidToken(token)) {
                    Timber.e("Failed to validate token");
                    // Show a toast
                    Looper mainLooper = Looper.getMainLooper();
                    Handler handler = new Handler(mainLooper);
                    handler.post(() -> Toast.makeText(MainApplication.getINSTANCE(), R.string.androidacy_failed_to_validate_token, Toast.LENGTH_LONG).show());
                    return false;
                }
                // Save token to shared preference
                SharedPreferences.Editor editor = MainApplication.getINSTANCE().getSharedPreferences("androidacy", 0).edit();
                editor.putString("pref_androidacy_api_token", token);
                editor.apply();
            } catch (
                    Exception e) {
                if (HttpException.shouldTimeout(e)) {
                    Timber.e(e, "We are being rate limited!");
                    this.androidacyBlockade = time + 3_600_000L;
                }
                Timber.e(e, "Failed to get a new token");
                return false;
            }
        }
        return true;
    }

    @Override
    protected List<RepoModule> populate(JSONObject jsonObject) throws JSONException, NoSuchAlgorithmException {
        Timber.d("AndroidacyRepoData populate start");
        if (!jsonObject.getString("status").equals("success"))
            throw new JSONException("Response is not a success!");
        String name = jsonObject.optString("name", "Androidacy Modules Repo");
        String nameForModules = name.endsWith(" (Official)") ? name.substring(0, name.length() - 11) : name;
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
            // Normally, we'd validate the module id here, but we don't need to because the server does it for us
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
            repoModule.zipUrl = filterURL(jsonObject.optString("zipUrl", ""));
            repoModule.notesUrl = filterURL(jsonObject.optString("notesUrl", ""));
            if (repoModule.zipUrl == null) {
                repoModule.zipUrl = // Fallback url in case the API doesn't have zipUrl
                        "https://" + this.host + "/magisk/info/" + moduleId;
            }
            if (repoModule.notesUrl == null) {
                repoModule.notesUrl = // Fallback url in case the API doesn't have notesUrl
                        "https://" + this.host + "/magisk/readme/" + moduleId;
            }
            repoModule.zipUrl = this.injectToken(repoModule.zipUrl);
            repoModule.notesUrl = this.injectToken(repoModule.notesUrl);
            repoModule.qualityText = R.string.module_downloads;
            repoModule.qualityValue = jsonObject.optInt("downloads", 0);
            String checksum = jsonObject.optString("checksum", "");
            repoModule.checksum = checksum.isEmpty() ? null : checksum;
            ModuleInfo moduleInfo = repoModule.moduleInfo;
            moduleInfo.name = jsonObject.getString("name");
            moduleInfo.versionCode = jsonObject.getLong("versionCode");
            moduleInfo.version = jsonObject.optString("version", "v" + moduleInfo.versionCode);
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
                            (Integer.parseInt(minMagisk.substring(0, c)) * 1000) + (Integer.parseInt(minMagisk.substring(c + 1)) * 100);
                }
            } catch (
                    Exception e) {
                moduleInfo.minMagisk = 0;
            }
            moduleInfo.needRamdisk = jsonObject.optBoolean("needRamdisk", false);
            moduleInfo.changeBoot = jsonObject.optBoolean("changeBoot", false);
            moduleInfo.mmtReborn = jsonObject.optBoolean("mmtReborn", false);
            moduleInfo.support = filterURL(jsonObject.optString("support"));
            moduleInfo.donate = filterURL(jsonObject.optString("donate"));
            String config = jsonObject.optString("config", "");
            moduleInfo.config = config.isEmpty() ? null : config;
            PropUtils.applyFallbacks(moduleInfo); // Apply fallbacks
        }
        Iterator<RepoModule> moduleInfoIterator = this.moduleHashMap.values().iterator();
        while (moduleInfoIterator.hasNext()) {
            RepoModule repoModule = moduleInfoIterator.next();
            if (!repoModule.processed) {
                moduleInfoIterator.remove();
            } else {
                repoModule.moduleInfo.verify();
            }
        }
        this.lastUpdate = lastLastUpdate;
        this.name = name;
        this.website = jsonObject.optString("website");
        this.support = jsonObject.optString("support");
        this.donate = jsonObject.optString("donate");
        this.submitModule = jsonObject.optString("submitModule");
        return newModules;
    }

    @Override
    public void storeMetadata(RepoModule repoModule, byte[] data) {
    }

    @Override
    public boolean tryLoadMetadata(RepoModule repoModule) {
        if (this.moduleHashMap.containsKey(repoModule.id)) {
            repoModule.moduleInfo.flags &= ~ModuleInfo.FLAG_METADATA_INVALID;
            return true;
        }
        repoModule.moduleInfo.flags |= ModuleInfo.FLAG_METADATA_INVALID;
        return false;
    }

    @Override
    public String getUrl() throws NoSuchAlgorithmException {
        return token == null ? this.url : this.url + "?token=" + token + "&v=" + BuildConfig.VERSION_CODE + "&c=" + BuildConfig.VERSION_NAME + "&device_id=" + generateDeviceId() + "&client_id=" + BuildConfig.ANDROIDACY_CLIENT_ID;
    }

    private String injectToken(String url) throws NoSuchAlgorithmException {
        // Do not inject token for non Androidacy urls
        if (!AndroidacyUtil.isAndroidacyLink(url))
            return url;
        if (this.testMode) {
            if (url.startsWith("https://production-api.androidacy.com/")) {
                Timber.e("Got non test mode url: %s", AndroidacyUtil.hideToken(url));
                url = "https://staging-api.androidacy.com/" + url.substring(38);
            }
        } else {
            if (url.startsWith("https://staging-api.androidacy.com/")) {
                Timber.e("Got test mode url: %s", AndroidacyUtil.hideToken(url));
                url = "https://production-api.androidacy.com/" + url.substring(35);
            }
        }
        String token = "token=" + AndroidacyRepoData.token;
        String deviceId = "device_id=" + generateDeviceId();
        if (!url.contains(token)) {
            if (url.lastIndexOf('/') < url.lastIndexOf('?')) {
                return url + '&' + token;
            } else {
                return url + '?' + token;
            }
        }
        if (!url.contains(deviceId)) {
            if (url.lastIndexOf('/') < url.lastIndexOf('?')) {
                return url + '&' + deviceId;
            } else {
                return url + '?' + deviceId;
            }
        }
        return url;
    }

    @NonNull
    @Override
    public String getName() {
        return this.testMode ? super.getName() + " (Test Mode)" : super.getName();
    }

    public void setToken(String token) {
        if (Http.hasWebView()) {
            AndroidacyRepoData.token = token;
        }
    }
}
