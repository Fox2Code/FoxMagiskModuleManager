package com.fox2code.mmm.utils;

import android.os.Build;

import com.fox2code.mmm.manager.ModuleInfo;
import com.topjohnwu.superuser.io.SuFileInputStream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;

public class PropUtils {
    private static final HashMap<String, String> moduleSupportsFallbacks = new HashMap<>();
    private static final HashMap<String, String> moduleConfigsFallbacks = new HashMap<>();
    private static final HashMap<String, Integer> moduleMinApiFallbacks = new HashMap<>();
    private static final int RIRU_MIN_API;

    // Note: These fallback values may not be up-to-date
    // They are only used if modules don't define the metadata
    static {
        // Support are pages or groups where the user can get support for the module
        moduleSupportsFallbacks.put("aospill", "https://t.me/PannekoX");
        moduleSupportsFallbacks.put("quickstepswitcher", "https://t.me/QuickstepSwitcherSupport");
        moduleSupportsFallbacks.put("riru_edxposed", "https://t.me/EdXposed");
        moduleSupportsFallbacks.put("riru_lsposed", "https://github.com/LSPosed/LSPosed/issues");
        moduleSupportsFallbacks.put("substratum", "https://github.com/substratum/substratum/issues");
        // Config are application installed by modules that allow them to be configured
        moduleConfigsFallbacks.put("quickstepswitcher", "xyz.paphonb.quickstepswitcher");
        moduleConfigsFallbacks.put("riru_edxposed", "org.meowcat.edxposed.manager");
        moduleConfigsFallbacks.put("riru_lsposed", "org.lsposed.manager");
        moduleConfigsFallbacks.put("xposed_dalvik", "de.robv.android.xposed.installer");
        moduleConfigsFallbacks.put("xposed", "de.robv.android.xposed.installer");
        moduleConfigsFallbacks.put("substratum", "projekt.substratum");
        // minApi is the minimum android version required to use the module
        moduleMinApiFallbacks.put("riru_ifw_enhance", Build.VERSION_CODES.O);
        moduleMinApiFallbacks.put("riru_edxposed", Build.VERSION_CODES.O);
        moduleMinApiFallbacks.put("riru_lsposed", Build.VERSION_CODES.O_MR1);
        moduleMinApiFallbacks.put("noneDisplayCutout", Build.VERSION_CODES.P);
        moduleMinApiFallbacks.put("quickstepswitcher", Build.VERSION_CODES.P);
        moduleMinApiFallbacks.put("riru_clipboard_whitelist", Build.VERSION_CODES.Q);
        // minApi for riru core include submodules
        moduleMinApiFallbacks.put("riru-core", RIRU_MIN_API = Build.VERSION_CODES.M);
    }

    public static void readProperties(ModuleInfo moduleInfo, String file) throws IOException {
        boolean readId = false, readIdSec = false, readVersionCode = false;
        try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(SuFileInputStream.open(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                int index = line.indexOf('=');
                if (index == -1 || line.startsWith("#"))
                    continue;
                String key = line.substring(0, index);
                String value = line.substring(index + 1).trim();
                switch (key) {
                    case "id":
                        readId = true;
                        if (!moduleInfo.id.equals(value)) {
                            throw new IOException(file + " has an non matching module id! "+
                                    "(Expected \"" + moduleInfo.id + "\" got \"" + value + "\"");
                        }
                        break;
                    case "name":
                        if (readIdSec && !moduleInfo.id.equals(value))
                            throw new IOException("Duplicate module name!");
                        moduleInfo.name = value;
                        if (moduleInfo.id.equals(value)) {
                            readIdSec = true;
                        }
                        break;
                    case "version":
                        moduleInfo.version = value;
                        break;
                    case "versionCode":
                        readVersionCode = true;
                        moduleInfo.versionCode = Integer.parseInt(value);
                        break;
                    case "author":
                        moduleInfo.author = value;
                        break;
                    case "description":
                        moduleInfo.description = value;
                        break;
                    case "support":
                        // Do not accept invalid or too broad support links
                        if (!value.startsWith("https://") ||
                                "https://forum.xda-developers.com/".equals(value))
                            break;
                        moduleInfo.support = value;
                        break;
                    case "donate":
                        // Do not accept invalid donate links
                        if (!value.startsWith("https://")) break;
                        moduleInfo.donate = value;
                        break;
                    case "config":
                        moduleInfo.config = value;
                        break;
                    case "minMagisk":
                        try {
                            moduleInfo.minMagisk = Integer.parseInt(value);
                        } catch (Exception e) {
                            moduleInfo.minMagisk = 0;
                        }
                        break;
                    case "minApi":
                        // Special case for Riru EdXposed because
                        // minApi don't mean the same thing for them
                        if (moduleInfo.id.equals("riru_edxposed") &&
                                "10".equals(value)) {
                            break;
                        }
                        try {
                            moduleInfo.minApi = Integer.parseInt(value);
                        } catch (Exception e) {
                            moduleInfo.minApi = 0;
                        }
                        break;
                    case "maxApi":
                        try {
                            moduleInfo.maxApi = Integer.parseInt(value);
                        } catch (Exception e) {
                            moduleInfo.maxApi = 0;
                        }
                        break;
                }
            }
        }
        if (!readId) {
            if (readIdSec) {
                // Using the name for module id is not really appropriate, so beautify it a bit
                moduleInfo.name = moduleInfo.id.substring(0, 1).toUpperCase(Locale.ROOT) +
                        moduleInfo.id.substring(1).replace('_', ' ');
            } else {
                throw new IOException("Didn't read module id at least once!");
            }
        }
        if (!readVersionCode) {
            throw new IOException("Didn't read module versionCode at least once!");
        }
        if (moduleInfo.name == null) {
            moduleInfo.name = moduleInfo.id;
        }
        if (moduleInfo.version == null) {
            moduleInfo.version = "v" + moduleInfo.versionCode;
        }
        if (moduleInfo.minApi == 0) {
            Integer minApiFallback = moduleMinApiFallbacks.get(moduleInfo.id);
            if (minApiFallback != null)
                moduleInfo.minApi = minApiFallback;
            else if (moduleInfo.id.startsWith("riru_")
                    || moduleInfo.id.startsWith("riru-"))
                moduleInfo.minApi = RIRU_MIN_API;
        }
        if (moduleInfo.support == null) {
            moduleInfo.support = moduleSupportsFallbacks.get(moduleInfo.id);
        }
        if (moduleInfo.config == null) {
            moduleInfo.config = moduleConfigsFallbacks.get(moduleInfo.id);
        }
    }
}
