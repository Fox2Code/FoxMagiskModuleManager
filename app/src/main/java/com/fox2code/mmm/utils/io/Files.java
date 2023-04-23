package com.fox2code.mmm.utils.io;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fox2code.mmm.MainApplication;
import com.topjohnwu.superuser.io.SuFile;
import com.topjohnwu.superuser.io.SuFileInputStream;
import com.topjohnwu.superuser.io.SuFileOutputStream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FileUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import timber.log.Timber;

/** @noinspection ResultOfMethodCallIgnored*/
public enum Files {
    ;
    private static final boolean is64bit = Build.SUPPORTED_64_BIT_ABIS.length > 0;

    // stolen from https://stackoverflow.com/a/25005243
    public static @NonNull String getFileName(Context context, Uri uri) {
        String result = null;
        if (Objects.equals(uri.getScheme(), "content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = Objects.requireNonNull(result).lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    // based on https://stackoverflow.com/a/63018108
    public static @Nullable Long getFileSize(Context context, Uri uri) {
        Long result = null;
        try {
            String scheme = uri.getScheme();
            if (Objects.equals(scheme, "content")) {
                Cursor returnCursor = context.getContentResolver().
                        query(uri, null, null, null, null);
                int sizeIndex = Objects.requireNonNull(returnCursor).getColumnIndex(OpenableColumns.SIZE);
                returnCursor.moveToFirst();

                long size = returnCursor.getLong(sizeIndex);
                returnCursor.close();

                result = size;
            }
            if (Objects.equals(scheme, "file")) {
                result = new File(Objects.requireNonNull(uri.getPath())).length();
            }
        } catch (Exception e) {
            Timber.e(Log.getStackTraceString(e));
            return result;
        }
        return result;
    }

    public static void write(File file, byte[] bytes) throws IOException {
        // make the dir if necessary
        Objects.requireNonNull(file.getParentFile()).mkdirs();
        try (OutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(bytes);
            outputStream.flush();
        }
    }

    public static byte[] read(File file) throws IOException {
        try (InputStream inputStream = new FileInputStream(file)) {
            return readAllBytes(inputStream);
        }
    }

    public static void writeSU(File file, byte[] bytes) throws IOException {
        // make the dir if necessary
        Objects.requireNonNull(file.getParentFile()).mkdirs();
        try (OutputStream outputStream = SuFileOutputStream.open(file)) {
            outputStream.write(bytes);
            outputStream.flush();
        }
    }

    public static byte[] readSU(File file) throws IOException {
        if (file.isFile() && file.canRead()) {
            try { // Read as app if su not required
                return read(file);
            } catch (IOException ignored) {
            }
        }
        try (InputStream inputStream = SuFileInputStream.open(file)) {
            return readAllBytes(inputStream);
        }
    }

    public static boolean existsSU(File file) {
        return file.exists() || new SuFile(file.getAbsolutePath()).exists();
    }

    public static void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            outputStream.write(data, 0, nRead);
        }
        outputStream.flush();
    }

    public static void closeSilently(Closeable closeable) {
        try {
            if (closeable != null) closeable.close();
        } catch (IOException ignored) {
        }
    }

    public static ByteArrayOutputStream makeBuffer(long capacity) {
        // Cap buffer to 1 Gib (or 512 Mib for 32bit) to avoid memory errors
        return Files.makeBuffer((int) Math.min(capacity, is64bit ? 0x40000000 : 0x20000000));
    }

    public static ByteArrayOutputStream makeBuffer(int capacity) {
        return new ByteArrayOutputStream(Math.max(0x20, capacity)) {
            @NonNull
            @Override
            public byte[] toByteArray() {
                return this.buf.length == this.count ?
                        this.buf : super.toByteArray();
            }
        };
    }

    public static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = Files.makeBuffer(inputStream.available());
        copy(inputStream, buffer);
        return buffer.toByteArray();
    }

    public static void fixJavaZipHax(byte[] bytes) {
        if (bytes.length > 8 && bytes[0x6] == 0x0 && bytes[0x7] == 0x0 && bytes[0x8] == 0x8)
            bytes[0x7] = 0x8; // Known hax to prevent java zip file read
    }

    public static void patchModuleSimple(byte[] bytes, OutputStream outputStream) throws IOException {
        fixJavaZipHax(bytes);
        patchModuleSimple(new ByteArrayInputStream(bytes), outputStream);
    }

    public static void patchModuleSimple(InputStream inputStream, OutputStream outputStream) throws IOException {
        ZipInputStream zipInputStream = new ZipInputStream(inputStream);
        ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);
        int nRead;
        byte[] data = new byte[16384];
        ZipEntry zipEntry;
        while ((zipEntry = zipInputStream.getNextEntry()) != null) {
            String name = zipEntry.getName();
            int i = name.indexOf('/', 1);
            if (i == -1) continue;
            String newName = name.substring(i + 1);
            if (newName.startsWith(".git")) continue; // Skip metadata
            zipOutputStream.putNextEntry(new ZipEntry(newName));
            while ((nRead = zipInputStream.read(data, 0, data.length)) != -1) {
                zipOutputStream.write(data, 0, nRead);
            }
            zipOutputStream.flush();
            zipOutputStream.closeEntry();
            zipInputStream.closeEntry();
        }
        zipOutputStream.finish();
        zipOutputStream.flush();
        zipOutputStream.close();
        zipInputStream.close();
    }

    public static void fixSourceArchiveShit(byte[] rawModule) {
        // unzip the module, check if it has just one folder within. if so, switch to the folder and zip up contents, and replace the original file with that
        try {
            File tempDir = new File(MainApplication.getINSTANCE().getCacheDir(), "temp");
            if (tempDir.exists()) {
                FileUtils.deleteDirectory(tempDir);
            }
            if (!tempDir.mkdirs()) {
                throw new IOException("Unable to create temp dir");
            }
            File tempFile = new File(tempDir, "module.zip");
            Files.write(tempFile, rawModule);
            File tempUnzipDir = new File(tempDir, "unzip");
            if (!tempUnzipDir.mkdirs()) {
                throw new IOException("Unable to create temp unzip dir");
            }
            // unzip
            Timber.d("Unzipping module to %s", tempUnzipDir.getAbsolutePath());
            try (ZipFile zipFile = new ZipFile(tempFile)) {
                Enumeration<ZipArchiveEntry> files = zipFile.getEntries();
                // check if there is only one folder in the top level
                int folderCount = 0;
                while (files.hasMoreElements()) {
                    ZipArchiveEntry entry = files.nextElement();
                    if (entry.isDirectory()) {
                        folderCount++;
                    }
                }
                if (folderCount == 1) {
                    files = zipFile.getEntries();
                    while (files.hasMoreElements()) {
                        ZipArchiveEntry entry = files.nextElement();
                        if (entry.isDirectory()) {
                            continue;
                        }
                        File file = new File(tempUnzipDir, entry.getName());
                        if (!Objects.requireNonNull(file.getParentFile()).exists()) {
                            if (!file.getParentFile().mkdirs()) {
                                throw new IOException("Unable to create parent dir");
                            }
                        }
                        try (ZipArchiveOutputStream zipArchiveOutputStream = new ZipArchiveOutputStream(file)) {
                            zipArchiveOutputStream.putArchiveEntry(entry);
                            try (InputStream inputStream = zipFile.getInputStream(entry)) {
                                copy(inputStream, zipArchiveOutputStream);
                            }
                            zipArchiveOutputStream.closeArchiveEntry();
                        }
                    }
                    // zip up the contents of the folder but not the folder itself
                    File[] filesInFolder = Objects.requireNonNull(tempUnzipDir.listFiles());
                    // create a new zip file
                    try (ZipArchiveOutputStream archive = new ZipArchiveOutputStream(new FileOutputStream("new.zip"))) {
                        for (File files2 : filesInFolder) {
                            // create a new ZipArchiveEntry and add it to the ZipArchiveOutputStream
                            ZipArchiveEntry entry = new ZipArchiveEntry(files2, files2.getName());
                            archive.putArchiveEntry(entry);
                            try (InputStream input = new FileInputStream(files2)) {
                                copy(input, archive);
                            }
                            archive.closeArchiveEntry();
                        }
                    } catch (IOException e) {
                        Timber.e(e, "Unable to zip up module");
                    }
                } else {
                    Timber.d("Module does not have a single folder in the top level, skipping");
                }
            } catch (IOException e) {
                Timber.e(e, "Unable to unzip module");
            }
        } catch (IOException e) {
            Timber.e(e, "Unable to create temp dir");
        }
    }
}
