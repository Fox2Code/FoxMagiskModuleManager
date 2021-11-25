package com.fox2code.mmm.compat;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

/**
 * I will probably outsource this to a separate library later
 */
final class CompatConfigHelper {
    // ENGLISH like this is an unnatural local, as it doesn't precise the country
    // All english locales settable by the user precise the country (Ex: en-US)
    private static final Locale english = Locale.ENGLISH;

    private final Context context;
    private Locale userLocale;

    CompatConfigHelper(Context context) {
        this.context = context;
    }

    void checkResourcesOverrides(boolean forceEnglish,
                                 Boolean nightModeOverride) {
        this.checkResourcesOverrides(
                this.context.getTheme(),
                forceEnglish, nightModeOverride);
    }

    void checkResourcesOverrides(Resources.Theme theme, boolean forceEnglish,
                                 Boolean nightModeOverride) {
        final Resources res = theme.getResources();
        final Configuration conf = res.getConfiguration();
        Locale current = conf.locale;
        boolean didChange = false;
        if (forceEnglish != current.equals(english)) {
            didChange = true;
            if (forceEnglish) {
                this.userLocale = conf.locale;
                conf.locale = english;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    conf.setLocales(LocaleList.getEmptyLocaleList());
                }
            } else {
                conf.locale = this.userLocale;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    conf.setLocales(LocaleList.getAdjustedDefault());
                }
            }
        }
        int nightMode = conf.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        int sysNightMode = Resources.getSystem()
                .getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeOverride == null ? sysNightMode != nightMode :
                nightMode != (nightModeOverride ?
                        Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO)) {
            didChange = true;
            nightMode = nightModeOverride == null ? sysNightMode : nightModeOverride ?
                    Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
            conf.uiMode = nightMode | (conf.uiMode & ~Configuration.UI_MODE_NIGHT_MASK);
        }
        if (didChange) {
            res.updateConfiguration(conf, null);
            if (!forceEnglish) this.userLocale = null;
        }
    }

    public Locale getUserLocale() {
        // Only use cached value if force english
        Locale locale = this.context.getResources().getConfiguration().locale;
        return english.equals(locale) ? this.userLocale : locale;
    }
}
