package com.fox2code.mmm.utils;

import android.os.Build;
import android.text.TextUtils;

import com.fox2code.mmm.manager.ModuleInfo;
import com.topjohnwu.superuser.io.SuFileInputStream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;

public class PropUtils {
    private static final HashMap<String, String> moduleSupportsFallbacks = new HashMap<>();
    private static final HashMap<String, String> moduleConfigsFallbacks = new HashMap<>();
    private static final HashMap<String, Integer> moduleMinApiFallbacks = new HashMap<>();
    private static final HashSet<String> moduleImportantProp = new HashSet<>(Arrays.asList(
            "id", "name", "version", "versionCode"
    ));
    private static final int RIRU_MIN_API;

    // Note: These fallback values may not be up-to-date
    // They are only used if modules don't define the metadata
    static {
        // Support are pages or groups where the user can get support for the module
        moduleSupportsFallbacks.put("aospill", "https://t.me/PannekoX");
        moduleSupportsFallbacks.put("bromitewebview", "https://t.me/androidacy_discussions");
        moduleSupportsFallbacks.put("fontrevival", "https://t.me/androidacy_discussions");
        moduleSupportsFallbacks.put("MagiskHidePropsConf", "https://forum.xda-developers.com/t" +
                "/module-magiskhide-props-config-safetynet-prop-edits-and-more-v6-1-1.3789228/");
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

    public static void readProperties(ModuleInfo moduleInfo, String file,
                                      boolean local) throws IOException {
        readProperties(moduleInfo, SuFileInputStream.open(file), file, local);
    }

    public static void readProperties(ModuleInfo moduleInfo, String file,
                                      String name, boolean local) throws IOException {
        readProperties(moduleInfo, SuFileInputStream.open(file), name, local);
    }

    public static void readProperties(ModuleInfo moduleInfo, InputStream inputStream,
                                      String name, boolean local) throws IOException {
        boolean readId = false, readIdSec = false, readName = false,
                readVersionCode = false, readVersion = false, readDescription = false,
                readUpdateJson = false, invalid = false;
        try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            int lineNum = 0;
            while ((line = bufferedReader.readLine()) != null) {
                lineNum++;
                int index = line.indexOf('=');
                if (index == -1 || line.startsWith("#"))
                    continue;
                String key = line.substring(0, index);
                String value = line.substring(index + 1).trim();
                // name and id have their own implementation
                if (isInvalidValue(key)) {
                    if (local) {
                        invalid = true;
                        continue;
                    } else throw new IOException("Invalid key at line " + lineNum);
                } else {
                    if (value.isEmpty() && !moduleImportantProp.contains(key))
                        continue; // allow empty values to pass.
                    if (isInvalidValue(value)) {
                        if (local) {
                            invalid = true;
                            continue;
                        } else throw new IOException("Invalid value for key " + key);
                    }
                }
                switch (key) {
                    case "id":
                        if (isInvalidValue(value)) {
                            if (local) {
                                invalid = true;
                                break;
                            } throw new IOException("Invalid module id!");
                        }
                        readId = true;
                        if (!moduleInfo.id.equals(value)) {
                            if (local) {
                                invalid = true;
                            } else {
                                throw new IOException(name + " has an non matching module id! " +
                                        "(Expected \"" + moduleInfo.id + "\" got \"" + value + "\"");
                            }
                        }
                        break;
                    case "name":
                        if (readName) {
                            if (local) {
                                invalid = true;
                                break;
                            } else throw new IOException("Duplicate module name!");
                        }
                        if (isInvalidValue(value)) {
                            if (local) {
                                invalid = true;
                                break;
                            } throw new IOException("Invalid module name!");
                        }
                        readName = true;
                        moduleInfo.name = value;
                        if (moduleInfo.id.equals(value)) {
                            readIdSec = true;
                        }
                        break;
                    case "version":
                        readVersion = true;
                        moduleInfo.version = value;
                        break;
                    case "versionCode":
                        readVersionCode = true;
                        try {
                            moduleInfo.versionCode = Long.parseLong(value);
                        } catch (RuntimeException e) {
                            if (local) {
                                invalid = true;
                                moduleInfo.versionCode = 0;
                            } else throw e;
                        }
                        break;
                    case "author":
                        moduleInfo.author = value.endsWith(" development team") ?
                                value.substring(0, value.length() - 17) : value;
                        break;
                    case "description":
                        moduleInfo.description = value;
                        readDescription = true;
                        break;
                    case "updateJson":
                        if (isInvalidURL(value)) break;
                        moduleInfo.updateJson = Http.fixUpLink(value);
                        readUpdateJson = true;
                        break;
                    case "support":
                        // Do not accept invalid or too broad support links
                        if (isInvalidURL(value) ||
                                "https://forum.xda-developers.com/".equals(value))
                            break;
                        moduleInfo.support = value;
                        break;
                    case "donate":
                        // Do not accept invalid donate links
                        if (isInvalidURL(value)) break;
                        moduleInfo.donate = value;
                        break;
                    case "config":
                        moduleInfo.config = value;
                        break;
                    case "minMagisk":
                        try {
                            int i = value.indexOf('.');
                            if (i == -1) {
                                moduleInfo.minMagisk = Integer.parseInt(value);
                            } else {
                                moduleInfo.minMagisk = // Allow 24.1 to mean 24100
                                        (Integer.parseInt(value.substring(0, i)) * 1000) +
                                                (Integer.parseInt(value.substring(i + 1)) * 100);
                            }
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
            if (readIdSec && local) {
                // Using the name for module id is not really appropriate, so beautify it a bit
                moduleInfo.name = makeNameFromId(moduleInfo.id);
            } else if (local) { // Allow local module to not declare ids
                invalid = true;
            } else {
                throw new IOException("Didn't read module id at least once!");
            }
        }
        if (!readVersionCode) {
            if (local) {
                invalid = true;
                moduleInfo.versionCode = 0;
            } else {
                throw new IOException("Didn't read module versionCode at least once!");
            }
        }
        if (!readName || isInvalidValue(moduleInfo.name)) {
            moduleInfo.name = makeNameFromId(moduleInfo.id);
        }
        if (!readVersion) {
            moduleInfo.version = "v" + moduleInfo.versionCode;
        } else {
            moduleInfo.version = shortenVersionName(
                    moduleInfo.version, moduleInfo.versionCode);
        }
        if (!readDescription || isInvalidValue(moduleInfo.description)) {
            moduleInfo.description = "";
        }
        if (!readUpdateJson) {
            moduleInfo.updateJson = null;
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
        // All local modules should have an author
        // set to "Unknown" if author is missing.
        if (local && moduleInfo.author == null) {
            moduleInfo.author = "Unknown";
        }
        if (invalid) {
            moduleInfo.flags |= ModuleInfo.FLAG_METADATA_INVALID;
            // This shouldn't happen but just in case
            if (!local) throw new IOException("Invalid properties!");
        }
    }

    // Some module are really so low quality that it has become very annoying.
    public static boolean isLowQualityModule(ModuleInfo moduleInfo) {
        final String description;
        return moduleInfo == null || moduleInfo.hasFlag(ModuleInfo.FLAG_METADATA_INVALID)
                || moduleInfo.name.length() < 3 || moduleInfo.versionCode < 0
                || moduleInfo.author == null || !TextUtils.isGraphic(moduleInfo.author)
                || (description = moduleInfo.description) == null || !TextUtils.isGraphic(description)
                || description.toLowerCase(Locale.ROOT).equals(moduleInfo.name.toLowerCase(Locale.ROOT))
                || description.length() < Math.min(Math.max(moduleInfo.name.length() + 4, 16), 24);
    }

    private static boolean isInvalidValue(String name) {
        return !TextUtils.isGraphic(name) || name.indexOf('\0') != -1;
    }

    private static boolean isInvalidURL(String url) {
        int i = url.indexOf('/', 8);
        int e = url.indexOf('.', 8);
        return i == -1 || e == -1 || e >= i || !url.startsWith("https://")
                || url.length() <= 12 || url.indexOf('\0') != -1;
    }

    private static String makeNameFromId(String moduleId) {
        return moduleId.substring(0, 1).toUpperCase(Locale.ROOT) +
                moduleId.substring(1).replace('_', ' ');
    }

    // Make versionName no longer than 16 charters to avoid UI overflow.
    public static String shortenVersionName(String versionName, long versionCode) {
        if (versionName == null) return "v" + versionCode;
        if (versionName.length() <= 16) return versionName;
        int i = versionName.lastIndexOf('.');
        if (i != -1 && i <= 16 && versionName.indexOf('.') != i
                && versionName.indexOf(' ') == -1)
            return versionName.substring(0, i);
        return "v" + versionCode;
    }
}
