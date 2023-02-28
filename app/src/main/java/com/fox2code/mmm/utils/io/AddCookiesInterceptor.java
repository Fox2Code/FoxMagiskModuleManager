package com.fox2code.mmm.utils.io;

import android.content.Context;
import android.util.Base64;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.regex.Pattern;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;


public class AddCookiesInterceptor implements Interceptor {
    private final Context context;

    public AddCookiesInterceptor(Context context) {
        this.context = context;
    }

    @NonNull
    @Override
    public Response intercept(Interceptor.Chain chain) throws IOException {
        Request.Builder builder = chain.request().newBuilder();

        String cookieFileName = "cookies";
        String[] cookies;
        // cookies are split by | so we can split the string into an array of cookies
        File cookieFile = new File(context.getFilesDir(), cookieFileName);
        if (cookieFile.exists()) {
            // read the file into a string
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
        } else {
            cookies = new String[0];
        }

        for (String cookie : cookies) {
            // ensure the cookie applies to the current domain
            if (cookie.contains("domain=")) {
                // match from the start of the string to the first semicolon
                try {
                    Pattern pattern = Pattern.compile("domain=([^;]+)");
                    String domain = pattern.matcher(cookie).group(1);
                    if (domain != null && !chain.request().url().host().contains(domain)) {
                        //noinspection UnnecessaryContinue
                        continue;
                    } else {
                        // yeet any newlines from the cookie
                        cookie = cookie.replaceAll("[\\r\\n]", "");
                        builder.addHeader("Cookie", cookie);
                    }
                } catch (
                        Exception ignored) {
                }
            } else {
                try {
                    builder.addHeader("Cookie", cookie);
                } catch (Exception e) {
                    Timber.e(e, "Error adding cookie to request: %s", cookie);
                }
            }
        }

        return chain.proceed(builder.build());
    }
}
