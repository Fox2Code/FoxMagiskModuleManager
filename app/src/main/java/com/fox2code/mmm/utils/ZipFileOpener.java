package com.fox2code.mmm.utils;

import static androidx.fragment.app.FragmentManager.TAG;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.fox2code.foxcompat.FoxActivity;
import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.R;
import com.fox2code.mmm.installer.InstallerInitializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ZipFileOpener extends FoxActivity {
    // Adds us as a handler for zip files, so we can pass them to the installer
    // We should have a content uri provided to us.
    @SuppressLint("RestrictedApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (BuildConfig.DEBUG) {
            Log.d("ZipFileOpener", "onCreate: " + getIntent());
        }
        File zipFile;
        Uri uri = getIntent().getData();
        if (uri == null) {
            Log.e("ZipFileOpener", "onCreate: No data provided");
            Toast.makeText(this, R.string.zip_load_failed, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // Try to copy the file to our cache
        try {
            zipFile = File.createTempFile("module", ".zip", getCacheDir());
            try (InputStream inputStream = getContentResolver().openInputStream(uri); FileOutputStream outputStream = new FileOutputStream(zipFile)) {
                if (inputStream == null) {
                    Log.e(TAG, "onCreate: Failed to open input stream");
                    Toast.makeText(this, R.string.zip_load_failed, Toast.LENGTH_LONG).show();
                    finishAndRemoveTask();
                    return;
                }
                byte[] buffer = new byte[4096];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
            }
        } catch (
                Exception e) {
            Log.e(TAG, "onCreate: Failed to copy zip file", e);
            Toast.makeText(this, R.string.zip_load_failed, Toast.LENGTH_LONG).show();
            finishAndRemoveTask();
            return;
        }
        // Ensure zip is not empty
        if (zipFile.length() == 0) {
            Log.e(TAG, "onCreate: Zip file is empty");
            Toast.makeText(this, R.string.zip_load_failed, Toast.LENGTH_LONG).show();
            finishAndRemoveTask();
            return;
        } else {
            if (BuildConfig.DEBUG) {
                Log.d("ZipFileOpener", "onCreate: Zip file is " + zipFile.length() + " bytes");
            }
        }
        // Unpack the zip to validate it's a valid magisk module
        // It needs to have, at the bare minimum, a module.prop file. Everything else is technically optional.
        // First, check if it's a zip file
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile)) {
            if (zip.getEntry("module.prop") == null) {
                Log.e(TAG, "onCreate: Zip file is not a valid magisk module");
                Toast.makeText(this, R.string.invalid_format, Toast.LENGTH_LONG).show();
                finishAndRemoveTask();
                return;
            }
        } catch (
                IOException e) {
            Log.e(TAG, "onCreate: Failed to open zip file", e);
            Toast.makeText(this, R.string.zip_load_failed, Toast.LENGTH_LONG).show();
            finishAndRemoveTask();
            return;
        }
        if (BuildConfig.DEBUG) {
            Log.d("ZipFileOpener", "onCreate: Zip file is valid");
        }
        // Pass the file to the installer
        FoxActivity compatActivity = FoxActivity.getFoxActivity(this);
        IntentHelper.openInstaller(compatActivity, zipFile.getAbsolutePath(),
                compatActivity.getString(
                        R.string.local_install_title), null, null, false,
                BuildConfig.DEBUG && // Use debug mode if no root
                        InstallerInitializer.peekMagiskPath() == null);
    }
}
