package com.ahmedjazzar.rosetta;

import android.util.Log;

/**
 * This class helps logging app events without a need to rewrite the tag name in every time
 * Created by ahmedjazzar on 1/16/16.
 */

class Logger {

    private final String mTag;

    Logger(String tag) {
        this.mTag = tag;
        this.verbose("Object from " + this.mTag + " has been created.");
    }

    void error(String log) {
        Log.e(this.mTag, log);
    }

    void warn(String log) {
        Log.w(this.mTag, log);
    }

    void debug(String log) {
        Log.d(this.mTag, log);
    }

    void info(String log) {
        Log.i(this.mTag, log);
    }

    void verbose(String log) {
        Log.v(this.mTag, log);
    }
}
