package com.fox2code.mmm.utils.io;

// Original written by tsuharesu
// Adapted to create a "drop it in and watch it work" approach by Nikhil Jha.
// Just add your package statement and drop it in the folder with all your other classes.

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKey;

import com.fox2code.mmm.MainApplication;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;


public class AddCookiesInterceptor implements Interceptor {
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
        MasterKey mainKeyAlias;
        String cookieFileName = "cookies";
        byte[] plaintext;
        try {
            // create cookie file if it doesn't exist
            mainKeyAlias = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            EncryptedFile encryptedFile = new EncryptedFile.Builder(context, new File(MainApplication.getINSTANCE().getFilesDir(), cookieFileName), mainKeyAlias, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build();
            InputStream inputStream;
            inputStream = encryptedFile.openFileInput();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            int nextByte = inputStream.read();
            while (nextByte != -1) {
                byteArrayOutputStream.write(nextByte);
                nextByte = inputStream.read();
            }

            plaintext = byteArrayOutputStream.toByteArray();
            inputStream.close();
        } catch (
                Exception e) {
            Timber.e(e, "Error while reading cookies");
            plaintext = new byte[0];
        }
        String[] preferences = new String(plaintext).split("\\|");
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
        Timber.d("Sending cookies: %s", cookiestring.toString());
        builder.addHeader("Cookie", cookiestring.toString());

        return chain.proceed(builder.build());
    }
}
