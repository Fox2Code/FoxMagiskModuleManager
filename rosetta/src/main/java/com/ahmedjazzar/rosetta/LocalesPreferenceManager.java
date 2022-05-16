package com.ahmedjazzar.rosetta;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.Locale;

/**
 * This class is responsible for setting and getting the preferred locale and manage any related
 * actions. I think that there's no need for logging here because the utils class already handles
 * logs for these actions based on their returned results.
 *
 * Created by ahmedjazzar on 1/22/16.
 */
class LocalesPreferenceManager  {

    private SharedPreferences mSharedPreferences;
    private SharedPreferences.Editor mEditor;
    
    static final int BASE_LOCALE = 1;
    private final String BASE_LANGUAGE_KEY = "base_language";
    private final String BASE_COUNTRY_KEY = "base_country";
    
    static final int LAUNCH_LOCALE = 2;
    private final String LAUNCH_LANGUAGE_KEY = "launch_language";
    private final String LAUNCH_COUNTRY_KEY = "launch_country";
    
    static final int USER_PREFERRED_LOCALE = 3;
    private final String USER_PREFERRED_LANGUAGE_KEY = "user_preferred_language";
    private final String USER_PREFERRED_COUNTRY_KEY = "user_preferred_country";

    LocalesPreferenceManager(Context context, Locale firstLaunchLocale, Locale baseLocale)   {

        this.mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.mEditor = this.mSharedPreferences.edit();

        if (!isLocaleExists(BASE_LOCALE))  {
            this.setPreferredLocale(BASE_LOCALE, baseLocale);
        }

        if (!isLocaleExists(LAUNCH_LOCALE))  {
            this.setPreferredLocale(LAUNCH_LOCALE, firstLaunchLocale);
        }

        if (!isLocaleExists(USER_PREFERRED_LOCALE))  {
            this.setPreferredLocale(USER_PREFERRED_LOCALE, firstLaunchLocale);
        }
    }

    boolean isLocaleExists(int key)    {

        switch (key)    {
            case BASE_LOCALE:
                return mSharedPreferences.contains(this.BASE_LANGUAGE_KEY);
            case LAUNCH_LOCALE:
                return mSharedPreferences.contains(this.LAUNCH_LANGUAGE_KEY);
            case USER_PREFERRED_LOCALE:
                return mSharedPreferences.contains(this.USER_PREFERRED_LANGUAGE_KEY);
            default:
                return false;
        }
    }

    /**
     * Sets user preferred locale
     *
     * @param locale user desired locale
     * @return true if the preference updated
     */
    boolean setPreferredLocale(int key, Locale locale)   {
        return this.setPreferredLocale(key, locale.getLanguage(), locale.getCountry());
    }

    /**
     *
     * @return preferred locale after concatenating language and country
     */
    Locale getPreferredLocale(int key)    {

        String languageKey;
        String countryKey;

        switch (key)    {
            case BASE_LOCALE:
                languageKey = this.BASE_LANGUAGE_KEY;
                countryKey = this.BASE_COUNTRY_KEY;
                break;
            case LAUNCH_LOCALE:
                languageKey = this.LAUNCH_LANGUAGE_KEY;
                countryKey = this.LAUNCH_COUNTRY_KEY;
                break;
            case USER_PREFERRED_LOCALE:
                languageKey = this.USER_PREFERRED_LANGUAGE_KEY;
                countryKey = this.USER_PREFERRED_COUNTRY_KEY;
                break;
            default:
                return null;
        }

        String language = getPreferredLanguage(languageKey);
        String country = getPreferredCountry(countryKey);

        if (language == null)    {
            return null;
        }

        return new Locale(language, country);
    }

    /**
     * Sets user preferred locale by setting a language preference and a country preference since
     * there's no supported preferences for locales
     * @param language of the locale; ex. en
     * @param country of the locale; ex. US
     * @return true if the preferences updated
     */
    private boolean setPreferredLocale(int key, String language, String country)   {
        
        String languageKey;
        String countryKey;

        switch (key)    {
            case BASE_LOCALE:
                languageKey = this.BASE_LANGUAGE_KEY;
                countryKey = this.BASE_COUNTRY_KEY;
                break;
            case LAUNCH_LOCALE:
                languageKey = this.LAUNCH_LANGUAGE_KEY;
                countryKey = this.LAUNCH_COUNTRY_KEY;
                break;
            case USER_PREFERRED_LOCALE:
                languageKey = this.USER_PREFERRED_LANGUAGE_KEY;
                countryKey = this.USER_PREFERRED_COUNTRY_KEY;
                break;
            default:
                return false;
        }
        
        mEditor.putString(languageKey, language);
        mEditor.putString(countryKey, country);

        return mEditor.commit();
    }

    /**
     *
     * @return preferred language
     */
    private String getPreferredLanguage(String key)  {
        return mSharedPreferences.getString(key, null);
    }

    /**
     *
     * @return preferred country
     */
    private String getPreferredCountry(String key)  {
        return mSharedPreferences.getString(key, null);
    }
}
