package com.fox2code.mmm.androidacy;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AndroidacyUtil {
    public static final String REFERRER = "utm_source=FoxMMM&utm_medium=app";

    public static boolean isAndroidacyLink(@Nullable Uri uri) {
        return uri != null && isAndroidacyLink(uri.toString(), uri);
    }

    public static boolean isAndroidacyLink(@Nullable String url) {
        return url != null && isAndroidacyLink(url, Uri.parse(url));
    }

    static boolean isAndroidacyLink(@NonNull String url,@NonNull Uri uri) {
        int i; // Check both string and Uri to mitigate parse exploit
        return url.startsWith("https://") &&
                (i = url.indexOf("/", 8)) != -1 &&
                url.substring(8, i).endsWith(".androidacy.com") &&
                        uri.getHost().endsWith(".androidacy.com");
    }

    // Avoid logging token
    public static String hideToken(@NonNull String url) {
        int i = url.lastIndexOf("token=");
        if (i == -1) return url;
        int i2 = url.indexOf('&', i);
        int i3 = url.indexOf(' ', i);
        if (i3 != -1 && i3 < i2) i2 = i3;
        if (i2 == -1) {
            return url.substring(0, i + 6) +
                    "<token>";
        } else {
            return url.substring(0, i + 6) +
                    "<token>" + url.substring(i2);
        }
    }
}
