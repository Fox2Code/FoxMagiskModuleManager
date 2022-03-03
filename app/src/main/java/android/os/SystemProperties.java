package android.os;

import androidx.annotation.Keep;

import com.topjohnwu.superuser.ShellUtils;

@Keep
public class SystemProperties {
    @Keep
    public static String get(String key) {
        String prop = ShellUtils.fastCmd("getprop " + key).trim();
        if (prop.endsWith("\n"))
            prop = prop.substring(0, prop.length() - 1).trim();
        return prop;
    }
}
