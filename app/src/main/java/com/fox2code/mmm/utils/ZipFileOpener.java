package com.fox2code.mmm.utils;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.fox2code.foxcompat.app.FoxActivity;
import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.R;
import com.fox2code.mmm.installer.InstallerInitializer;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipFileOpener extends FoxActivity {
    AlertDialog loading = null;

    // Adds us as a handler for zip files, so we can pass them to the installer
    // We should have a content uri provided to us.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loading = BudgetProgressDialog.build(this, R.string.loading, R.string.zip_unpacking);
        new Thread(() -> {
            if (BuildConfig.DEBUG) {
                Log.d("ZipFileOpener", "onCreate: " + getIntent());
            }
            File zipFile;
            Uri uri = getIntent().getData();
            if (uri == null) {
                Log.e("ZipFileOpener", "onCreate: No data provided");
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.zip_load_failed, Toast.LENGTH_LONG).show();
                    finishAndRemoveTask();
                });
                return;
            }
            // Try to copy the file to our cache
            try {
                // check if its a file over 10MB
                Long fileSize = Files.getFileSize(this, uri);
                if (fileSize == null) fileSize = 0L;
                if (1000L * 1000 * 10 < fileSize) {
                    runOnUiThread(() -> loading.show());
                }
                zipFile = File.createTempFile("module", ".zip", getCacheDir());
                try (InputStream inputStream = getContentResolver().openInputStream(uri); FileOutputStream outputStream = new FileOutputStream(zipFile)) {
                    if (inputStream == null) {
                        Log.e("ZipFileOpener", "onCreate: Failed to open input stream");
                        runOnUiThread(() -> {
                            Toast.makeText(this, R.string.zip_load_failed, Toast.LENGTH_LONG).show();
                            finishAndRemoveTask();
                        });
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
                Log.e("ZipFileOpener", "onCreate: Failed to copy zip file", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.zip_load_failed, Toast.LENGTH_LONG).show();
                    finishAndRemoveTask();
                });
                return;
            }
            // Ensure zip is not empty
            if (zipFile.length() == 0) {
                Log.e("ZipFileOpener", "onCreate: Zip file is empty");
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.zip_load_failed, Toast.LENGTH_LONG).show();
                    finishAndRemoveTask();
                });
                return;
            } else {
                if (BuildConfig.DEBUG) {
                    Log.d("ZipFileOpener", "onCreate: Zip file is " + zipFile.length() + " bytes");
                }
            }
            ZipEntry entry;
            ZipFile zip = null;
            // Unpack the zip to validate it's a valid magisk module
            // It needs to have, at the bare minimum, a module.prop file. Everything else is technically optional.
            // First, check if it's a zip file
            try {
                zip = new ZipFile(zipFile);
                if ((entry = zip.getEntry("module.prop")) == null) {
                    Log.e("ZipFileOpener", "onCreate: Zip file is not a valid magisk module");
                    runOnUiThread(() -> {
                        Toast.makeText(this, R.string.invalid_format, Toast.LENGTH_LONG).show();
                        finishAndRemoveTask();
                    });
                    return;
                }
            } catch (
                    Exception e) {
                Log.e("ZipFileOpener", "onCreate: Failed to open zip file", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.zip_load_failed, Toast.LENGTH_LONG).show();
                    finishAndRemoveTask();
                });
                if (zip != null) {
                    try {
                        zip.close();
                    } catch (IOException exception) {
                        Log.e("ZipFileOpener", Log.getStackTraceString(exception));
                    }
                }
                return;
            }
            if (BuildConfig.DEBUG) {
                Log.d("ZipFileOpener", "onCreate: Zip file is valid");
            }
            String moduleInfo;
            try {
                moduleInfo = PropUtils.readModulePropSimple(zip.getInputStream(entry), "name");
                if (moduleInfo == null) {
                    moduleInfo = PropUtils.readModulePropSimple(zip.getInputStream(entry), "id");
                }
                if (moduleInfo == null) {
                    throw new NullPointerException("moduleInfo is null, check earlier logs for root cause");
                }
            } catch (
                    Exception e) {
                Log.e("ZipFileOpener", "onCreate: Failed to load module id", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.zip_prop_load_failed, Toast.LENGTH_LONG).show();
                    finishAndRemoveTask();
                });
                try {
                    zip.close();
                } catch (IOException exception) {
                    Log.e("ZipFileOpener", Log.getStackTraceString(exception));
                }
                return;
            }
            try {
                zip.close();
            } catch (IOException exception) {
                Log.e("ZipFileOpener", Log.getStackTraceString(exception));
            }
            runOnUiThread(() -> {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.zip_security_warning, moduleInfo))
                        .setMessage(getString(R.string.zip_intent_module_install, moduleInfo, Files.getFileName(this, uri)))
                        .setCancelable(false)
                        .setNegativeButton(R.string.no, (d, i) -> {
                            d.dismiss();
                            finishAndRemoveTask();
                        })
                        .setPositiveButton(R.string.yes, (d, i) -> {
                            d.dismiss();
                            // Pass the file to the installer
                            FoxActivity compatActivity = FoxActivity.getFoxActivity(this);
                            IntentHelper.openInstaller(compatActivity, zipFile.getAbsolutePath(),
                                    compatActivity.getString(
                                            R.string.local_install_title), null, null, false,
                                    BuildConfig.DEBUG && // Use debug mode if no root
                                            InstallerInitializer.peekMagiskPath() == null);
                            finish();
                        })
                        .show();
                loading.dismiss();
            });
        }).start();
    }
}
