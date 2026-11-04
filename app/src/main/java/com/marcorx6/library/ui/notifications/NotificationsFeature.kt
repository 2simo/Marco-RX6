package com.marcorx6.library.ui

import androidx.activity.ComponentActivity
import android.app.AlertDialog
import com.marcorx6.library.notification.BlogNotificationScheduler

/** Owns notification scheduling, runtime permission requests, and settings UI. */
internal class NotificationsFeature(
    activity: ComponentActivity,
    styleDialog: (AlertDialog) -> Unit,
    translate: (String) -> String
) {
    private val scheduler = BlogNotificationScheduler(activity.applicationContext)
    private val permissionController = NotificationPermissionController(activity)
    private val settingsController = NotificationSettingsController(
        activity,
        scheduler,
        styleDialog,
        translate
    ) {
        requestPermissionIfNeeded()
    }

    fun createChannel() = scheduler.createNotificationChannel()
    fun isEnabled(): Boolean = scheduler.isEnabled()
    fun setEnabled(enabled: Boolean) = scheduler.setEnabled(enabled)
    fun schedule() = scheduler.schedule()
    fun cancel() = scheduler.cancel()

    fun requestPermissionIfNeeded() = permissionController.requestIfNeeded()

    fun showSettings() = settingsController.show()
}
