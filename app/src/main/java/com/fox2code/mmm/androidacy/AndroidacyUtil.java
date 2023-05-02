package com.fox2code.mmm.androidacy;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fox2code.mmm.BuildConfig;

import java.util.Objects;

public enum AndroidacyUtil {
    ;
    public static final String REFERRER = "utm_source=FoxMMM&utm_medium=app";

    public static boolean isAndroidacyLink(@Nullable Uri uri) {
        return uri != null && isAndroidacyLink(uri.toString(), uri);
    }

    public static boolean isAndroidacyLink(@Nullable String url) {
        return url != null && isAndroidacyLink(url, Uri.parse(url));
    }

    static boolean isAndroidacyLink(@NonNull String url, @NonNull Uri uri) {
        int i; // Check both string and Uri to mitigate parse exploit
        return url.startsWith("https://") && (i = url.indexOf("/", 8)) != -1 && url.substring(8, i).endsWith("api.androidacy.com") && Objects.requireNonNull(uri.getHost()).endsWith("api.androidacy.com");
    }

    public static boolean isAndroidacyFileUrl(@Nullable String url) {
        if (url == null)
            return false;
        for (String prefix : new String[]{"https://production-api.androidacy.com/downloads/", "https://production-api.androidacy.com/magisk/file/", "https://staging-api.androidacy.com/magisk/file/"}) { // Make both staging and non staging act the same
            if (url.startsWith(prefix))
                return true;
        }
        return false;
    }

    // Avoid logging token
    public static String hideToken(@NonNull String url) {
        // for token, device_id, and client_id, replace with <hidden> by using replaceAll to match until the next non-alphanumeric character or end
        // Also, URL decode
        url = Uri.decode(url);
        url = url + "&";
        url = url.replaceAll("token=[^&]*", "token=<hidden>");
        url = url.replaceAll("device_id=[^&]*", "device_id=<hidden>");
        url = url.replaceAll("client_id=[^&]*", "client_id=<hidden>");
        // remove last & added at the end
        url = url.substring(0, url.length() - 1);
        return url;
    }

    public static String getModuleId(String moduleUrl) {
        // Get the &module= part
        int i = moduleUrl.indexOf("&module=");
        String moduleId;
        // Match until next & or end
        if (i != -1) {
            int j = moduleUrl.indexOf('&', i + 1);
            if (j == -1) {
                moduleId = moduleUrl.substring(i + 8);
            } else {
                moduleId = moduleUrl.substring(i + 8, j);
            }
            // URL decode
            moduleId = Uri.decode(moduleId);
            // Strip non alphanumeric
            moduleId = moduleId.replaceAll("[^a-zA-Z\\d]", "");
            return moduleId;
        }
        if (BuildConfig.DEBUG) {
            throw new IllegalArgumentException("Invalid module url: " + moduleUrl);
        }
        return null;
    }

    public static String getModuleTitle(String moduleUrl) {
        // Get the &title= part
        int i = moduleUrl.indexOf("&moduleTitle=");
        // Match until next & or end
        if (i != -1) {
            int j = moduleUrl.indexOf('&', i + 1);
            if (j == -1) {
                return Uri.decode(moduleUrl.substring(i + 13));
            } else {
                return Uri.decode(moduleUrl.substring(i + 13, j));
            }
        }
        return null;
    }

    public static String getChecksumFromURL(String moduleUrl) {
        // Get the &version= part
        int i = moduleUrl.indexOf("&checksum=");
        // Match until next & or end
        if (i != -1) {
            int j = moduleUrl.indexOf('&', i + 1);
            if (j == -1) {
                return moduleUrl.substring(i + 10);
            } else {
                return moduleUrl.substring(i + 10, j);
            }
        }
        return null;
    }
}
