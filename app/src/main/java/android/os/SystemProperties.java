package android.os;

import androidx.annotation.Keep;

import com.topjohnwu.superuser.ShellUtils;

/**
 * I will probably outsource this to a separate library later
 */
@Keep
public class SystemProperties {
    @Keep
    public static String get(String key) {
        String prop = ShellUtils.fastCmd("getprop " + key).trim();
        if (prop.endsWith("\n"))
            prop = prop.substring(0, prop.length() - 1).trim();
        return prop;
    }

    @Keep
    public static int getInt(String key, int def) {
        try {
            String value = get(key);
            if (value.isEmpty()) return def;
            return Integer.parseInt(value);
        } catch (Exception e) {
            return def;
        }
    }
}
