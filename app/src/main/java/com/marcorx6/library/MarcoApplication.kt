package com.marcorx6.library

import android.app.Application
import androidx.work.Configuration
import java.util.concurrent.Executors

/** Gives WorkManager enough bounded worker threads for the user's download concurrency setting. */
class MarcoApplication : Application(), Configuration.Provider {
    private val workExecutor by lazy { Executors.newFixedThreadPool(5) }

    override fun onCreate() {
        super.onCreate()
        AppDiagnostics.install(this)
        AppDiagnostics.log("INFO", "Application", "Process started")
        // Do not initialize WebView/WorkManager filter components during process startup.
        // The browser initializes the filter lazily when the user actually opens it.
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setExecutor(workExecutor)
            .setMaxSchedulerLimit(20)
            .build()
}
