package com.ahmedjazzar.rosetta;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * This class is a helper class that connects all library classes activities together and make it
 * easier for every class in the library to use and look at the shared info without a need to
 * initialize a new object from the desired class
 *
 * Created by ahmedjazzar on 1/19/16.
 */
final class LocalesUtils {

    @SuppressLint("StaticFieldLeak")
    private static LocalesDetector sDetector;
    private static LocalesPreferenceManager sLocalesPreferenceManager;
    private static HashSet<Locale> sLocales;
    private static final Locale[] PSEUDO_LOCALES = {
            new Locale("en", "XA"),
            new Locale("ar", "XB")
    };
    private static final String TAG = LocalesDetector.class.getName();
    private static Logger sLogger = new Logger(TAG);

    /**
     *
     * @param detector just a setter because I don't want to declare any constructors in this class
     */
    static void setDetector(@NonNull LocalesDetector detector) {
        LocalesUtils.sDetector = detector;
    }

    /**
     *
     * @param localesPreferenceManager just a setter because I don't want to declare any
     *                                 constructors in this class
     */
    static void setLocalesPreferenceManager(
            @NonNull LocalesPreferenceManager localesPreferenceManager)   {

        LocalesUtils.sLocalesPreferenceManager = localesPreferenceManager;
    }

    /**
     *
     * @param stringId a string to start discovering sLocales in
     * @return a HashSet of discovered sLocales
     */
    static HashSet<Locale> fetchAvailableLocales(int stringId)  {
        return sDetector.fetchAvailableLocales(stringId);
    }

    /**
     *
     * @param localesSet sLocales  user wanna use
     */
    static void setSupportedLocales(HashSet<Locale> localesSet)    {
        LocalesUtils.sLocales = sDetector.validateLocales(localesSet);
        sLogger.debug("Locales have been changed");
    }

    /**
     *
     * @return a HashSet of the available sLocales discovered in the application
     */
    static HashSet<Locale> getLocales()    {
        return LocalesUtils.sLocales;
    }

    /**
     *
     * @return a list of locales for displaying on the layout purposes
     */
    static ArrayList<String> getLocalesWithDisplayName()   {
        ArrayList<String> stringLocales = new ArrayList<>();

        for (Locale loc: LocalesUtils.getLocales()) {
            String langDisplay = loc.getDisplayName(loc);
            stringLocales.add(langDisplay.substring(0, 1).toUpperCase() + langDisplay.substring(1).toLowerCase());
        }
        return stringLocales;
    }

    /**
     *
     * @return the index of the current app locale
     */
    static int getCurrentLocaleIndex()    {
        Locale locale = LocalesUtils.getCurrentLocale();
        int index = -1;
        int itr = 0;

        for (Locale l : sLocales)  {
            if(locale.equals(l))    {
                index = itr;
                break;
            }
            itr++;
        }

        if (index == -1)    {
            //TODO: change the index to the most closer available locale
            sLogger.warn("Current device locale '" + locale.toString() +
                    "' does not appear in your given supported locales");

            index = sDetector.detectMostClosestLocale(locale);
            if(index == -1)   {
                index = 0;
                sLogger.warn("Current locale index changed to 0 as the current locale '" +
                                locale +
                                "' not supported."
                );
            }
        }

        return index;
    }

    /**
     *
     * @see <a href="http://en.wikipedia.org/wiki/Pseudolocalization">Pseudolocalization</a> for
     *      more information about pseudo localization
     * @return pseudo locales list
     */
    static List<Locale> getPseudoLocales()  {
        return Arrays.asList(LocalesUtils.PSEUDO_LOCALES);
    }

    /**
     *
     * @return the locale at the given index
     */
    static Locale getLocaleFromIndex(int index)  {
        return LocalesUtils.sLocales.toArray(new Locale[LocalesUtils.sLocales.size()])[index];
    }

    /**
     *
     * @param context
     * @param index the selected locale position
     * @return true if the application locale changed
     */
    static boolean setAppLocale(Context context, int index)    {
        return setAppLocale(context, getLocaleFromIndex(index));
    }

    /**
     *
     * @return true if the application locale changed
     */
    static boolean setAppLocale(Context context, Locale newLocale)    {

        Resources resources = context.getResources();
        DisplayMetrics displayMetrics = resources.getDisplayMetrics();
        Configuration configuration = resources.getConfiguration();

        Locale oldLocale = new Locale(configuration.locale.getLanguage(), configuration.locale.getCountry());
        configuration.locale = newLocale;
        // Sets the layout direction from the Locale
        sLogger.debug("Setting the layout direction");
        configuration.setLayoutDirection(newLocale);
        resources.updateConfiguration(configuration, displayMetrics);

        if(oldLocale.equals(newLocale)) {
            return false;
        }

        if (LocalesUtils.updatePreferredLocale(newLocale))    {
            sLogger.info("Locale preferences updated to: " + newLocale);
            Locale.setDefault(newLocale);
        } else  {
            sLogger.error("Failed to update locale preferences.");
        }

        return true;
    }

    /**
     *
     * @return application's base locale
     */
    static Locale getBaseLocale()    {
        return LocalesUtils.sLocalesPreferenceManager.getPreferredLocale(LocalesPreferenceManager.BASE_LOCALE);
    }

    /**
     *
     * @param stringId the target string
     * @return a localized string
     */
    static String getInSpecificLocale(FragmentActivity activity, Locale locale, int stringId) {

        Configuration conf = activity.getResources().getConfiguration();
        Locale old = conf.locale;

        conf.locale = locale;
        DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        Resources resources = new Resources(activity.getAssets(), metrics, conf);
        conf.locale = old;

        return  resources.getString(stringId);
    }

    /**
     * Refreshing the application so no weired results occurred after changing the locale.
     */
    static void refreshApplication(Activity activity) {

        Intent app = activity.getBaseContext().getPackageManager()
                .getLaunchIntentForPackage(activity.getBaseContext().getPackageName());
        app.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        Intent current = new Intent(activity, activity.getClass());
        sLogger.debug("Refreshing the application: " +
                activity.getBaseContext().getPackageName());

        sLogger.debug("Finishing current activity.");
        activity.finish();

        sLogger.debug("Start the application");
        activity.startActivity(app);
        activity.startActivity(current);

        sLogger.debug("Application refreshed");
    }

    /**
     *
     * @return the first launch locale
     */
    static Locale getLaunchLocale()  {

        return sLocalesPreferenceManager.getPreferredLocale(LocalesPreferenceManager.LAUNCH_LOCALE);
    }

    /**
     * Setting the application locale manually
     * @param newLocale the desired locale
     * @param activity the current activity in order to refresh the app
     *
     * @return true if the operation succeed, false otherwise
     */
    static boolean setLocale(Locale newLocale, Activity activity) {
        if (newLocale == null || !getLocales().contains(newLocale))  {
            return false;
        }

        if (LocalesUtils.setAppLocale(activity.getApplicationContext(), newLocale))   {
            LocalesUtils.refreshApplication(activity);
            return true;
        }

        return false;
    }

    /**
     * @param context application base context
     * @return the current locale
     */
    public static Locale getCurrentLocale(Context context) {
        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();

        return new Locale(configuration.locale.getLanguage(), configuration.locale.getCountry());
    }

    /**
     *
     * @param locale the new preferred locale
     * @return true if the preferred locale updated
     */
    private static boolean updatePreferredLocale(Locale locale)    {

        return LocalesUtils.sLocalesPreferenceManager
                .setPreferredLocale(LocalesPreferenceManager.USER_PREFERRED_LOCALE, locale);
    }

    /**
     *
     * @return current application locale
     */
    private static Locale getCurrentLocale() {
        return sDetector.getCurrentLocale();
    }
}