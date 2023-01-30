package com.fox2code.mmm.utils.io;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKey;

import com.fox2code.mmm.MainApplication;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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

        // Cookies are stored in an encrypted file in the files directory in our app data
        // so we need to decrypt the file before using it
        // first, get our decryption key from MasterKey using the AES_256_GCM encryption scheme
        // then, create an EncryptedFile object using the key and the file name
        // finally, open the file and read the contents into a string
        // the string is then split into an array of cookies
        // the cookies are then added to the request builder

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
                        builder.addHeader("Cookie", cookie);
                    }
                } catch (
                        Exception ignored) {
                }
            } else {
                builder.addHeader("Cookie", cookie);
            }
        }

        return chain.proceed(builder.build());
    }
}
