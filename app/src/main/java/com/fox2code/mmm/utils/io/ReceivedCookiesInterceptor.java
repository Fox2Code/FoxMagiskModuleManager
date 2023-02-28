package com.fox2code.mmm.utils.io;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Base64;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
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
            StringBuilder cookieBuffer = new StringBuilder();
            for (String header : originalResponse.headers("Set-Cookie")) {
                cookieBuffer.append(header).append("|");
            } // for

            int lastPipe = cookieBuffer.lastIndexOf("|");
            if (lastPipe > 0) {
                cookieBuffer.deleteCharAt(lastPipe);
            }

            String cookieFileName = "cookies";
            String[] cookies = new String[0];
            try {
                File cookieFile = new File(context.getFilesDir(), cookieFileName);
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(cookieFile)));
                char[] buf = new char[1024];
                int read;
                StringBuilder stringBuilder = new StringBuilder();
                while ((read = bufferedReader.read(buf)) != -1) {
                    stringBuilder.append(buf, 0, read);
                }
                bufferedReader.close();
                String cookieString = stringBuilder.toString();
                // base64 decode the string
                cookieString = Arrays.toString(Base64.decode(cookieString, Base64.DEFAULT));
                // split the string into an array of cookies
                cookies = cookieString.split("\\|");
            } catch (Exception e) {
                Timber.e(e, "Error reading cookies from file");
            }

            // Logic to merge our cookies with received cookies
            // We need to check if the cookie we received is already in our file
            // If it is, we need to replace it with the new one
            // If it isn't, we need to add it to the end of the file
            HashSet<String> cookieSet = new HashSet<>(Arrays.asList(cookies));
            String[] newCookies = cookieBuffer.toString().split("\\|");
            for (String cookie : newCookies) {
                cookieSet.remove(cookie);
                cookieSet.add(cookie);
            }

            // convert the set back into a string
            StringBuilder newCookieBuffer = new StringBuilder();
            for (String cookie : cookieSet) {
                newCookieBuffer.append(cookie).append("|");
            }

            // remove the last pipe
            lastPipe = newCookieBuffer.lastIndexOf("|");
            if (lastPipe > 0) {
                newCookieBuffer.deleteCharAt(lastPipe);
            }

            // write the new cookies to the file
            File cookieFile = new File(context.getFilesDir(), cookieFileName);
            FileOutputStream fileOutputStream = new FileOutputStream(cookieFile);
            fileOutputStream.write(newCookieBuffer.toString().getBytes());
            fileOutputStream.close();
        }

        return originalResponse;
    }
}
