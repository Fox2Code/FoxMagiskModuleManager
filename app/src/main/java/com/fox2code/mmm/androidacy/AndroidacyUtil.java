package com.fox2code.mmm.androidacy;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AndroidacyUtil {
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
}
