package com.fox2code.mmm.utils.io;

// Original written by tsuharesu
// Adapted to create a "drop it in and watch it work" approach by Nikhil Jha.
// Just add your package statement and drop it in the folder with all your other classes.

import android.content.Context;

import androidx.annotation.NonNull;

import com.fox2code.mmm.MainApplication;

import java.io.IOException;
import java.util.HashSet;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

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
            String[] parser = cookie.split(";");
            cookiestring.append(parser[0]).append("; ");
        }
        builder.addHeader("Cookie", cookiestring.toString());

        for (String cookie : preferences) {
            builder.addHeader("Cookie", cookie);
        }

        return chain.proceed(builder.build());
    }
}
