package com.fox2code.mmm.utils.io;

// Original written by tsuharesu
// Adapted to create a "drop it in and watch it work" approach by Nikhil Jha.
// Just add your package statement and drop it in the folder with all your other classes.

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKey;

import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.MainApplication;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
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
            MasterKey mainKeyAlias;
            String cookieFileName = "cookies";
            byte[] plaintext;
            try {
                mainKeyAlias = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
                EncryptedFile encryptedFile = new EncryptedFile.Builder(context, new File(MainApplication.getINSTANCE().getFilesDir(), cookieFileName), mainKeyAlias, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build();
                InputStream inputStream = encryptedFile.openFileInput();
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
                e.printStackTrace();
                plaintext = new byte[0];
            }
            HashSet<String> cookies = new HashSet<>(Arrays.asList(new String(plaintext).split("\\|")));
            HashSet<String> cookieSet = new HashSet<>(originalResponse.headers("Set-Cookie"));
            if (BuildConfig.DEBUG_HTTP) {
                Timber.d("Received cookies: %s", cookieSet);
            }
            // if we already have the cooki in cookies, remove the one in cookies
            cookies.removeIf(cookie -> cookieSet.toString().contains(cookie.split(";")[0]));
            // add the new cookies to the cookies
            cookies.addAll(cookieSet);
            // write the cookies to the file
            try {
                mainKeyAlias = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
                EncryptedFile encryptedFile = new EncryptedFile.Builder(context, new File(MainApplication.getINSTANCE().getFilesDir(), cookieFileName), mainKeyAlias, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build();
                encryptedFile.openFileOutput().write(String.join("|", cookies).getBytes());
                encryptedFile.openFileOutput().flush();
                encryptedFile.openFileOutput().close();
                Timber.d("Storing encrypted cookies: %s", String.join("|", cookies));
            } catch (
                    GeneralSecurityException e) {
                throw new IllegalStateException("Unable to get master key", e);
            }
        }

        return originalResponse;
    }
}
