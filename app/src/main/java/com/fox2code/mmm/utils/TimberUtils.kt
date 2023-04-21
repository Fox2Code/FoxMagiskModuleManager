package com.fox2code.mmm.utils
import com.fox2code.mmm.BuildConfig
import com.fox2code.mmm.MainApplication
import com.fox2code.mmm.MainApplication.ReleaseTree
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.android.timber.SentryTimberTree
import timber.log.Timber
import timber.log.Timber.Forest.plant

@Suppress("UnstableApiUsage")
object TimberUtils {

    @JvmStatic
    fun configTimber() {
        // init timber
        // init timber
        if (BuildConfig.DEBUG) {
            plant(Timber.DebugTree())
        } else {
            if (MainApplication.isCrashReportingEnabled()) {
                plant(SentryTimberTree(Sentry.getCurrentHub(), SentryLevel.ERROR, SentryLevel.ERROR))
            } else {
                plant(ReleaseTree())
            }
        }
    }
}