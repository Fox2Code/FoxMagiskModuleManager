package com.fox2code.mmm.utils;

import androidx.annotation.NonNull;

public final class FastException extends RuntimeException {
    public static final FastException INSTANCE = new FastException();

    private FastException() {}

    @NonNull
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
