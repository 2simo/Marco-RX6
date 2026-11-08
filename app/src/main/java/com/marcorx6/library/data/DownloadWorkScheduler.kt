package com.marcorx6.library.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Starts only as many transfers as the user allows. Slot reservation is performed
 * in SQLite so parallel enqueue calls cannot overbook the queue.
 */
internal object DownloadWorkScheduler {
    private val lock = Any()

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            val database = DownloadDatabase.get(appContext)
            val preferences = DownloadConcurrencyPreferences(appContext)
            val limit = preferences.maxConcurrent()
            val requiredNetwork = if (preferences.wifiOnly()) NetworkType.UNMETERED else NetworkType.CONNECTED
            val ids = database.claimPending(limit)
            if (ids.isEmpty()) return
            val manager = WorkManager.getInstance(appContext)
            ids.forEach { id ->
                val request = OneTimeWorkRequestBuilder<ResumableDownloadWorker>()
                    .setInputData(workDataOf(ResumableDownloadWorker.KEY_ID to id))
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(requiredNetwork)
                            .build()
                    )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                    .build()
                try {
                    manager.enqueueUniqueWork(workName(id), ExistingWorkPolicy.KEEP, request)
                } catch (error: Exception) {
                    database.status(id, android.app.DownloadManager.STATUS_PENDING,
                        error.message ?: "Could not schedule download")
                }
            }
        }
    }

    fun cancel(context: Context, id: Long) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName(id))
    }

    fun workName(id: Long): String = "marco_rx6_download_$id"
}
