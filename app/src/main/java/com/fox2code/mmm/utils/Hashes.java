package com.fox2code.mmm.utils;

import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Pattern;

public class Hashes {
    private static final String TAG = "Hashes";
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();
    private static final Pattern nonAlphaNum = Pattern.compile("[^a-zA-Z0-9]");
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static String hashMd5(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");

            return bytesToHex(md.digest(input));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String hashSha1(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");

            return bytesToHex(md.digest(input));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String hashSha256(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            return bytesToHex(md.digest(input));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String hashSha512(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");

            return bytesToHex(md.digest(input));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Check if the checksum match a file by picking the correct
     * hashing algorithm depending on the length of the checksum
     */
    public static boolean checkSumMatch(byte[] data, String checksum) {
        String hash;
        if (checksum == null) return false;
        switch (checksum.length()) {
            case 0:
                return true; // No checksum
            case 32:
                hash = Hashes.hashMd5(data); break;
            case 40:
                hash = Hashes.hashSha1(data); break;
            case 64:
                hash = Hashes.hashSha256(data); break;
            case 128:
                hash = Hashes.hashSha512(data); break;
            default:
                Log.e(TAG, "No hash algorithm for " +
                        checksum.length() * 8 + "bit checksums");
                return false;
        }
        Log.d(TAG, "Checksum result (data: " + hash+ ",expected: " + checksum + ")");
        return hash.equals(checksum.toLowerCase(Locale.ROOT));
    }

    public static boolean checkSumValid(String checksum) {
        if (checksum == null) return false;
        switch (checksum.length()) {
            case 0:
            default:
                return false;
            case 32:
            case 40:
            case 64:
            case 128:
                final int len = checksum.length();
                for (int i = 0; i < len; i++) {
                    char c = checksum.charAt(i);
                    if (c < '0' || c > 'f') return false;
                    if (c > '9' && // Easier working with bits
                            (c & 0b01011111) < 'A') return false;
                }
                return true;
        }
    }

    public static String checkSumName(String checksum) {
        if (checksum == null) return null;
        switch (checksum.length()) {
            case 0:
            default:
                return null;
            case 32:
                return "MD5";
            case 40:
                return "SHA-1";
            case 64:
                return "SHA-256";
            case 128:
                return "SHA-512";
        }
    }

    public static String checkSumFormat(String checksum) {
        if (checksum == null) return null;
        // Remove all non-alphanumeric characters
        return nonAlphaNum.matcher(checksum.trim()).replaceAll("");
    }
}
