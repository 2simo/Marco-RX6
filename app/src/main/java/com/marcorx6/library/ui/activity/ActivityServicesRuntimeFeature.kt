package com.marcorx6.library.ui.activity

import com.marcorx6.library.ui.*

import android.app.AlertDialog
import androidx.activity.ComponentActivity

/**
 * Owns Activity-scoped infrastructure services: notifications, downloads, UI dispatch and system bars.
 */
internal class ActivityServicesRuntimeFeature(
    activity: ComponentActivity,
    styleAlertDialog: (AlertDialog) -> Unit,
    private val isDarkMode: () -> Boolean,
    private val translate: (String) -> String,
    setContentView: (android.view.View) -> Unit,
    showHome: () -> Unit,
    openGameSource: (String) -> Unit
) {
    // Bound concurrent background work so bursts of sync/download tasks cannot
    // create an unbounded number of threads on lower-memory phones.
    val ioExecutor = java.util.concurrent.Executors.newFixedThreadPool(3)

    private val downloadFeature by lazy {
        DownloadFeature(activity, styleAlertDialog, isDarkMode, setContentView, translate, { showHome() }, openGameSource)
    }
    private val notificationsFeature by lazy { NotificationsFeature(activity, styleAlertDialog, translate) }
    private val systemBarAppearanceController by lazy {
        SystemBarAppearanceController(activity, isDarkMode)
    }
    private val uiThreadDispatcher by lazy { UiThreadDispatcher(activity) }

    fun postToUi(action: () -> Unit) = uiThreadDispatcher.post(action)
    fun applySystemBarAppearance() = systemBarAppearanceController.apply()

    fun createNotificationChannel() = notificationsFeature.createChannel()
    fun areNotificationsEnabled(): Boolean = notificationsFeature.isEnabled()
    fun setNotificationsEnabled(enabled: Boolean) = notificationsFeature.setEnabled(enabled)
    fun scheduleNotifications() = notificationsFeature.schedule()
    fun cancelNotifications() = notificationsFeature.cancel()
    fun requestNotificationPermissionIfNeeded() = notificationsFeature.requestPermissionIfNeeded()
    fun showNotificationSettings() = notificationsFeature.showSettings()

    fun enqueueGameDownload(
        link: String,
        title: String,
        userAgent: String = "",
        contentDisposition: String = "",
        hintedMimeType: String = "",
        referrer: String = "",
        sourcePostUrl: String = ""
    ) = downloadFeature.enqueueGameDownload(
        link, title, userAgent, contentDisposition, hintedMimeType, referrer, sourcePostUrl
    )

    fun showDownloadManager() = downloadFeature.showManager()
    fun openGameLink(link: String) = downloadFeature.openGameLink(link)

    fun shutdown() {
        ioExecutor.shutdownNow()
    }
}
