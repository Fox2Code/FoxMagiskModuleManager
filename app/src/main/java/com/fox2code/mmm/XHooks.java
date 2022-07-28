package com.fox2code.mmm;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.webkit.WebView;

import androidx.annotation.Keep;

import com.fox2code.mmm.manager.ModuleManager;
import com.fox2code.mmm.repo.RepoManager;

/**
 * Class made to expose some manager functions to xposed modules.
 * It will not be obfuscated on release builds
 */
@Keep
public class XHooks {
    @Keep
    public static void onRepoManagerInitialized() {}

    @Keep
    public static boolean isModuleActive(String moduleId) {
        return ModuleManager.isModuleActive(moduleId);
    }

    @Keep
    public static void checkConfigTargetExists(Context context, String packageName, String config)
            throws PackageManager.NameNotFoundException {
        if ("org.lsposed.manager".equals(config) && "org.lsposed.manager".equals(packageName) &&
                (XHooks.isModuleActive("riru_lsposed") || XHooks.isModuleActive("zygisk_lsposed")))
            return; // Skip check for lsposed as it is probably injected into the system.
        context.getPackageManager().getPackageInfo(packageName, 0);
    }

    @Keep
    public static Intent getConfigIntent(Context context, String packageName,String config) {
        return context.getPackageManager().getLaunchIntentForPackage(packageName);
    }

    @Keep
    public static void onWebViewInitialize(WebView webView,boolean allowInstall) {
        if (webView == null) throw new NullPointerException("WebView is null!");
    }

    @Keep
    public static XRepo addXRepo(String url, String fallbackName) {
        return RepoManager.getINSTANCE().addOrGet(url, fallbackName);
    }

    @Keep
    public static XRepo getXRepo(String url) {
        return RepoManager.getINSTANCE().get(url);
    }
}
