package com.fox2code.mmm.utils;

import android.os.Build;

import androidx.annotation.NonNull;

import com.topjohnwu.superuser.io.SuFile;
import com.topjohnwu.superuser.io.SuFileInputStream;
import com.topjohnwu.superuser.io.SuFileOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Files {
    private static final boolean is64bit = Build.SUPPORTED_64_BIT_ABIS.length > 0;

    public static void write(File file, byte[] bytes) throws IOException {
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
        try (OutputStream outputStream = SuFileOutputStream.open(file)) {
            outputStream.write(bytes);
            outputStream.flush();
        }
    }

    public static byte[] readSU(File file) throws IOException {
        try (InputStream inputStream = SuFileInputStream.open(file)) {
            return readAllBytes(inputStream);
        }
    }

    public static boolean existsSU(File file) {
        return file.exists() || new SuFile(file.getAbsolutePath()).exists();
    }

    public static void copy(InputStream inputStream,OutputStream outputStream) throws IOException {
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
        } catch (IOException ignored) {}
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

    public static void patchModuleSimple(byte[] bytes,OutputStream outputStream) throws IOException {
        fixJavaZipHax(bytes); patchModuleSimple(new ByteArrayInputStream(bytes), outputStream);
    }

    public static void patchModuleSimple(InputStream inputStream,OutputStream outputStream) throws IOException {
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
}
