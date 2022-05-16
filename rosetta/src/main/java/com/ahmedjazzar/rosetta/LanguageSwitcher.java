package com.ahmedjazzar.rosetta;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import java.util.HashSet;
import java.util.Locale;

/**
 * This class is the application door to this Library. It handles the ongoing and outgoing requests,
 * initializations, preferences, ..
 * I think that there's no need for logging here because other classes already handle logs for these
 * actions based on their returned results.
 *
 * Created by ahmedjazzar on 1/16/16.
 */
public class LanguageSwitcher {

    private Context mContext;
    private LocalesPreferenceManager mLocalesPreferences;
    private final String TAG = LanguageSwitcher.class.getName();

    /**
     * A constructor that accepts context and sets the base and first launch locales to en_US
     * @param context the context of the dealer
     */
    public LanguageSwitcher(@NonNull Context context) {
        this(context, Locale.US);
    }

    /**
     * A constructor that accepts context and sets the base and first launch locales to
     * firstLaunchLocale.
     *
     * NOTE: Please do not use unless:
     *  1. You wanna set your locales by calling {@link LanguageSwitcher#setSupportedLocales}
     *  2. You know for sure that the preferred locale is as same as your base locale
     *
     * @param context the context of the dealer
     * @param firstLaunchLocale the locale that owner wanna use at its first launch
     */
    public LanguageSwitcher(@NonNull Context context, Locale firstLaunchLocale) {
        this(context, firstLaunchLocale, firstLaunchLocale);
    }

    /**
     * This is supposed to be more specific; It has three parameters cover all owner needs
     * @param context the context of the dealer
     * @param firstLaunchLocale the locale that owner wanna use at its first launch
     * @param baseLocale the locale that used in the main xml strings file (most likely 'en')
     */
    public LanguageSwitcher(@NonNull Context context, Locale firstLaunchLocale, Locale baseLocale) {
        this.mContext = context.getApplicationContext();

        this.mLocalesPreferences =
                new LocalesPreferenceManager(context, firstLaunchLocale, baseLocale);

        // initializing Locales utils needed objects (detector, preferences)
        LocalesUtils.setDetector(new LocalesDetector(this.mContext));
        LocalesUtils.setLocalesPreferenceManager(mLocalesPreferences);

        // Setting app locale to match the user preferred one
        LocalesUtils.setAppLocale(mContext,
                mLocalesPreferences
                        .getPreferredLocale(LocalesPreferenceManager.USER_PREFERRED_LOCALE));
    }

    /**
     * Responsible for displaying Change dialog fragment
     */
    public void showChangeLanguageDialog(FragmentActivity activity)  {
        new LanguagesListDialogFragment()
                .show(activity.getSupportFragmentManager(), TAG);
    }

    /**
     *
     * @return the application supported locales
     */
    public HashSet<Locale> getLocales()   {
        return LocalesUtils.getLocales();
    }

    /**
     * Sets the app locales from a string Set
     * @param sLocales supported locales in a String form
     */
    public void setSupportedStringLocales(HashSet<String> sLocales)    {

        HashSet<Locale> locales = new HashSet<>();
        for (String sLocale: sLocales) {
            locales.add(new Locale(sLocale));
        }
        this.setSupportedLocales(locales);
    }

    /**
     * set supported locales from the given Set
     * @param locales supported locales
     */
    public void setSupportedLocales(HashSet<Locale> locales)    {
        LocalesUtils.setSupportedLocales(locales);
    }

    /**
     * Sets the supported locales after fetching there availability using fetchAvailableLocales
     * method
     * @param stringId the string that this library gonna use to detect current app available
     *                 locales
     */
    public void setSupportedLocales(int stringId)    {
        this.setSupportedLocales(this.fetchAvailableLocales(stringId));
    }

    /**
     * Fetching the application available locales inside the resources folder dynamically
     * @param stringId the string that this library gonna use to detect current app available
     *                 locales
     * @return a set of detected application locales
     */
    public HashSet<Locale> fetchAvailableLocales(int stringId) {
        return LocalesUtils.fetchAvailableLocales(stringId);
    }

    /**
     * Setting the application locale manually
     * @param newLocale the locale in a string format
     * @param activity the current activity in order to refresh the app
     *
     * @return true if the operation succeed, false otherwise
     */
    public boolean setLocale(String newLocale, Activity activity)   {
        return setLocale(new Locale(newLocale), activity);
    }

    /**
     * Setting the application locale manually
     * @param newLocale the desired locale
     * @param activity the current activity in order to refresh the app
     *
     * @return true if the operation succeed, false otherwise
     */
    public boolean setLocale(Locale newLocale, Activity activity)  {

        return LocalesUtils.setLocale(newLocale, activity);
    }

    /**
     *
     * @return the first launch locale
     */
    public Locale getLaunchLocale()  {

        return LocalesUtils.getLaunchLocale();
    }

    /**
     *
     * @return the current locale
     */
    public Locale getCurrentLocale()  {

        return LocalesUtils.getCurrentLocale(this.mContext);
    }

    /**
     * Return to the first launch locale
     * @param activity the current activity in order to refresh the app
     *
     * @return true if the operation succeed, false otherwise
     */
    public boolean switchToLaunch(Activity activity)  {

        return setLocale(getLaunchLocale(), activity);
    }
}
