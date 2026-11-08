package com.marcorx6.library

import android.app.Application
import androidx.work.Configuration
import java.util.concurrent.Executors

/** Gives WorkManager enough bounded worker threads for the user's download concurrency setting. */
class MarcoApplication : Application(), Configuration.Provider {
    private val workExecutor by lazy { Executors.newFixedThreadPool(5) }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setExecutor(workExecutor)
            .setMaxSchedulerLimit(20)
            .build()
}
