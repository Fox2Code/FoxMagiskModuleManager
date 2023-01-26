package com.fox2code.mmm.utils.io;

// Original written by tsuharesu
// Adapted to create a "drop it in and watch it work" approach by Nikhil Jha.
// Just add your package statement and drop it in the folder with all your other classes.

import android.content.Context;

import androidx.annotation.NonNull;

import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.MainApplication;

import java.io.IOException;
import java.util.HashSet;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;

/**
 * This interceptor put all the Cookies in Preferences in the Request.
 * Your implementation on how to get the Preferences may ary, but this will work 99% of the time.
 */
public class AddCookiesInterceptor implements Interceptor {
    public static final String PREF_COOKIES = "PREF_COOKIES";
    // We're storing our stuff in a database made just for cookies called PREF_COOKIES.
    // I reccomend you do this, and don't change this default value.
    private final Context context;

    public AddCookiesInterceptor(Context context) {
        this.context = context;
    }

    @NonNull
    @Override
    public Response intercept(Interceptor.Chain chain) throws IOException {
        Request.Builder builder = chain.request().newBuilder();

        HashSet<String> preferences = (HashSet<String>) MainApplication.getSharedPreferences().getStringSet(PREF_COOKIES, new HashSet<>());

        // Use the following if you need everything in one line.
        // Some APIs die if you do it differently.
        StringBuilder cookiestring = new StringBuilder();
        for (String cookie : preferences) {
            // if cookie doesn't end in a semicolon, add one.
            if (!cookie.endsWith(";")) {
                cookie = cookie + ";";
            }
            cookiestring.append(cookie).append(" ");
        }
        // if ccokiestring doesn't have is_foxmmm cookie, add a never expiring one for the current domain.
        if (!cookiestring.toString().contains("is_foxmmm")) {
            cookiestring.append("is_foxmmm=true; expires=Fri, 31 Dec 9999 23:59:59 GMT; path=/; domain=").append(chain.request().url().host()).append("; SameSite=None; Secure;");
        }
        if (BuildConfig.DEBUG_HTTP) {
            Timber.d("Sending cookies: %s", cookiestring.toString());
        }
        builder.addHeader("Cookie", cookiestring.toString());

        return chain.proceed(builder.build());
    }
}
