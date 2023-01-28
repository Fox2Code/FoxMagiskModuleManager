package com.fox2code.mmm.utils.io;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKey;

import com.fox2code.mmm.MainApplication;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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

            // Cookies are stored in an encrypted file in the files directory in our app data
            // so we need to decrypt the file before using it
            // first, get our decryption key from MasterKey using the AES_256_GCM encryption scheme
            // then, create an EncryptedFile object using the key and the file name
            // finally, open the file and read the contents into a string
            // the string is then split into an array of cookies

            String cookieFileName = "cookies";
            String[] cookies = new String[0];
            MasterKey mainKeyAlias;
            try {
                mainKeyAlias = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
                EncryptedFile encryptedFile = new EncryptedFile.Builder(context, new File(MainApplication.getINSTANCE().getFilesDir(), cookieFileName), mainKeyAlias, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build();
                InputStream inputStream = encryptedFile.openFileInput();
                byte[] buffer = new byte[1024];
                int bytesRead;
                StringBuilder outputString = new StringBuilder();
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputString.append(new String(buffer, 0, bytesRead));
                }
                cookies = outputString.toString().split("\\|");
                inputStream.close();
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
            try {
                mainKeyAlias = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
                EncryptedFile encryptedFile = new EncryptedFile.Builder(context, new File(MainApplication.getINSTANCE().getFilesDir(), cookieFileName), mainKeyAlias, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build();
                encryptedFile.openFileOutput().write(newCookieBuffer.toString().getBytes());
            } catch (Exception e) {
                Timber.e(e, "Error writing cookies to file");
            }

        }

        return originalResponse;
    }
}
