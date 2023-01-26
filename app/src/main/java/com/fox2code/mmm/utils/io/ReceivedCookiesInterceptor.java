package com.fox2code.mmm.utils.io;

// Original written by tsuharesu
// Adapted to create a "drop it in and watch it work" approach by Nikhil Jha.
// Just add your package statement and drop it in the folder with all your other classes.

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.MainApplication;

import java.io.IOException;
import java.util.HashSet;

import okhttp3.Interceptor;
import okhttp3.Response;
import timber.log.Timber;

public class ReceivedCookiesInterceptor implements Interceptor {
    private final Context context;
    public ReceivedCookiesInterceptor(Context context) {
        this.context = context;
    } // AddCookiesInterceptor()
    @NonNull
    @SuppressLint({"MutatingSharedPrefs", "ApplySharedPref"})
    @Override
    public Response intercept(Chain chain) throws IOException {
        Response originalResponse = chain.proceed(chain.request());

        if (!originalResponse.headers("Set-Cookie").isEmpty()) {
            HashSet<String> cookies = (HashSet<String>) MainApplication.getSharedPreferences().getStringSet("PREF_COOKIES", new HashSet<>());

            cookies.addAll(originalResponse.headers("Set-Cookie"));
            if (!cookies.toString().contains("is_foxmmm")) {
                cookies.add("is_foxmmm=true; expires=Fri, 31 Dec 9999 23:59:59 GMT; path=/; domain=" + chain.request().url().host() + "; SameSite=None; Secure;");
            }

            SharedPreferences.Editor memes = MainApplication.getSharedPreferences().edit();
            if (BuildConfig.DEBUG_HTTP) {
                Timber.d("Received cookies: %s", cookies);
            }
            memes.putStringSet("PREF_COOKIES", cookies).apply();
            memes.commit();
        }

        return originalResponse;
    }
}
