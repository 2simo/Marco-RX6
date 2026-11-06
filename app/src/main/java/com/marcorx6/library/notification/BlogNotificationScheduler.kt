package com.marcorx6.library.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.marcorx6.library.notification.BlogNotificationWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Owns notification preference storage, channel creation and periodic work scheduling.
 * Runtime permission prompts and settings UI remain in MainActivity.
 */
internal class BlogNotificationScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val workManager = WorkManager.getInstance(appContext)

    fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BlogNotificationWorker.CHANNEL_ID,
                "New MARCO posts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when a new post is published on the MARCO blog"
            }
            appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun schedule() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<BlogNotificationWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            BlogNotificationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(BlogNotificationWorker.WORK_NAME)
    }

    private companion object {
        const val PREFERENCES_NAME = "notification_settings"
        const val KEY_ENABLED = "enabled"
    }
}
