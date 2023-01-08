/*
 * Copyright (c) 2021 Fox2Code
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * */

package com.fox2code.mmm.utils.io;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

/** Open implementation of ProviderInstaller.installIfNeeded
 * (Compatible with MicroG even without signature spoofing)
 */
// Note: This code is MIT because I took it from another unpublished project I had
// I might upstream this to MicroG at some point
public class GMSProviderInstaller {
    private static final String TAG = "GMSProviderInstaller";
    private static boolean called = false;

    public static void installIfNeeded(final Context context) {
        if (context == null) {
            throw new NullPointerException("Context must not be null");
        }
        if (called) return;
        called = true;
        try {
            // Trust default GMS implementation
            Context remote = context.createPackageContext("com.google.android.gms",
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            Class<?> cl = remote.getClassLoader().loadClass(
                    "com.google.android.gms.common.security.ProviderInstallerImpl");
            cl.getDeclaredMethod("insertProvider", Context.class).invoke(null, remote);
            Log.i(TAG, "Installed GMS security providers!");
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "No GMS Implementation are installed on this device");
        } catch (Exception e) {
            Log.w(TAG, "Failed to install the provider of the current GMS Implementation", e);
        }
    }
}
