package com.marcorx6.library

import android.app.Application
import androidx.work.Configuration
import java.util.concurrent.Executors
import io.github.edsuns.adfilter.AdFilter

/** Gives WorkManager enough bounded worker threads for the user's download concurrency setting. */
class MarcoApplication : Application(), Configuration.Provider {
    private val workExecutor by lazy { Executors.newFixedThreadPool(5) }

    override fun onCreate() {
        super.onCreate()
        // Initialize the WebView ad-filter engine once for the whole app.
        val filter = AdFilter.create(this)
        if (!filter.hasInstallation) {
            val subscriptions = linkedMapOf(
                "EasyList" to "https://easylist.to/easylist/easylist.txt",
                "EasyPrivacy" to "https://easylist.to/easylist/easyprivacy.txt",
                "AdGuard Annoyances" to "https://filters.adtidy.org/extension/chromium/filters/14.txt"
            )
            subscriptions.forEach { (name, url) ->
                val subscription = filter.viewModel.addFilter(name, url)
                filter.viewModel.download(subscription.id)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setExecutor(workExecutor)
            .setMaxSchedulerLimit(20)
            .build()
}
