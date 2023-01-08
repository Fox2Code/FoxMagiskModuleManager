package com.fox2code.mmm.utils.io;

import androidx.annotation.Keep;

import java.io.IOException;

public final class HttpException extends IOException {
    private final int errorCode;

    HttpException(String text, int errorCode) {
        super(text);
        this.errorCode = errorCode;
    }

    @Keep
    public HttpException(int errorCode) {
        super("Received error code: " + errorCode);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public boolean shouldTimeout() {
        switch (errorCode) {
            case 419:
            case 429:
            case 503:
                return true;
            default:
                return false;
        }
    }

    public static boolean shouldTimeout(Exception exception) {
        return exception instanceof HttpException &&
                ((HttpException) exception).shouldTimeout();
    }
}
