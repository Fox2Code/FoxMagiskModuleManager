package com.fox2code.mmm.compat;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.os.Build;
import android.os.SystemProperties;
import android.util.Log;
import android.view.Display;
import android.view.DisplayCutout;

import androidx.annotation.RequiresApi;
import androidx.core.view.DisplayCutoutCompat;
import androidx.core.view.WindowInsetsCompat;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Get notch information from any Android devices.
 */
final class CompatNotch {
    private static final String TAG = "CompatNotch";

    static int getNotchHeight(CompatActivity compatActivity) {
        // Android 9.0 still need legacy check for notch detection.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return getNotchHeightModern(compatActivity);
        } else {
            int notch = getNotchHeightLegacy(compatActivity);
            DisplayCutoutCompat displayCutoutCompat =
                    WindowInsetsCompat.CONSUMED.getDisplayCutout();
            return displayCutoutCompat == null ? notch :
                    Math.max(displayCutoutCompat.getSafeInsetTop(), notch);
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private static int getNotchHeightModern(CompatActivity compatActivity) {
        Display display = compatActivity.getDisplay();
        DisplayCutout displayCutout = display == null ? null : display.getCutout();
        if (displayCutout != null) return Math.max(displayCutout.getSafeInsetTop(), 1);
        DisplayCutoutCompat displayCutoutCompat = WindowInsetsCompat.CONSUMED.getDisplayCutout();
        return displayCutoutCompat == null ? 0 : Math.max(displayCutoutCompat.getSafeInsetTop(), 1);
    }

    private static final int VIVO_NOTCH = 0x00000020;

    @SuppressLint({"InternalInsetResource", "PrivateApi"})
    private static int getNotchHeightLegacy(CompatActivity compatActivity) {
        ClassLoader classLoader = compatActivity.getClassLoader();
        int id = Resources.getSystem().getIdentifier("status_bar_height", "dimen", "android");
        int height = id <= 0 ? 1 : Resources.getSystem().getDimensionPixelSize(id);
        try { // Huawei Notch
            Class<?> HwNotchSizeUtil = classLoader.loadClass("com.huawei.android.util.HwNotchSizeUtil");
            Method get = HwNotchSizeUtil.getMethod("hasNotchInScreen");
            if ((boolean) Objects.requireNonNull(
                    get.invoke(HwNotchSizeUtil))) {
                try {
                    get = HwNotchSizeUtil.getMethod("getNotchSize");
                    return Math.max(((int[]) Objects.requireNonNull(
                            get.invoke(HwNotchSizeUtil)))[1], height);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to get Huawei notch on Huawei device", e);
                    return height;
                }
            }
        } catch (Exception e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    "Huawei".equalsIgnoreCase(Build.MANUFACTURER)) {
                Log.e(TAG, "Failed to get Huawei notch on Huawei device", e);
            }
        }
        if (compatActivity.getPackageManager() // Oppo & MIUI Notch
                .hasSystemFeature("com.oppo.feature.screen.heteromorphism") ||
                SystemProperties.getInt("ro.miui.notch", -1) == 1) {
            return height;
        }
        try { // Vivo Notch
            Class<?> FtFeature = classLoader.loadClass("android.util.FtFeature");
            Method method = FtFeature.getMethod("isFeatureSupport", int.class);
            if ((boolean) Objects.requireNonNull(
                    method.invoke(FtFeature, VIVO_NOTCH))) {
                return height;
            }
        } catch (Exception e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    "Vivo".equalsIgnoreCase(Build.MANUFACTURER)) {
                Log.e(TAG, "Failed to get Vivo notch on Vivo device", e);
            }
        }
        return 0;
    }
}
