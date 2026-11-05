package com.marcorx6.library.ui

import androidx.activity.ComponentActivity
import com.marcorx6.library.data.DownloadRepository

/** Owns download persistence and its manager UI entry points. */
internal class DownloadFeature(
    activity: ComponentActivity,
    styleDialog: (android.app.AlertDialog) -> Unit,
    isDarkMode: () -> Boolean,
    setContentView: (android.view.View) -> Unit,
    translate: (String) -> String,
    onBack: () -> Unit,
    openGameSource: (String) -> Unit
) {
    private val repository = DownloadRepository(activity.applicationContext)
    private val controller = DownloadUiController(activity, repository, styleDialog, isDarkMode, setContentView, translate, onBack, openGameSource)

    fun enqueueGameDownload(
        link: String,
        title: String,
        userAgent: String = "",
        contentDisposition: String = "",
        hintedMimeType: String = "",
        referrer: String = "",
        sourcePostUrl: String = ""
    ) = controller.enqueueGameDownload(
        link, title, userAgent, contentDisposition, hintedMimeType, referrer, sourcePostUrl
    )

    fun showManager() = controller.showDownloadManager()
    fun openGameLink(link: String) = controller.openGameLink(link)
}
