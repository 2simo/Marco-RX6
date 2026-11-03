package com.marcorx6.library.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.marcorx6.library.data.DownloadRepository
import com.marcorx6.library.data.DownloadStartResult
import com.marcorx6.library.network.SecureUrlPolicy

/**
 * Owns download-related presentation and external-link navigation.
 * Actual URL inspection and DownloadManager operations remain in DownloadRepository.
 */
internal class DownloadUiController(
    private val activity: Activity,
    private val downloadRepository: DownloadRepository,
    private val styleDialog: (AlertDialog) -> Unit
) {
    fun enqueueGameDownload(
        link: String,
        title: String,
        userAgent: String = "",
        contentDisposition: String = "",
        hintedMimeType: String = "",
        referrer: String = ""
    ) {
        val secureLink = SecureUrlPolicy.toHttpsUrl(link)
        if (secureLink == null) {
            Toast.makeText(activity, "رابط التنزيل غير آمن أو غير صالح؛ يجب استخدام HTTPS", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(activity, "جارٍ التحقق من رابط الملف…", Toast.LENGTH_SHORT).show()
        downloadRepository.enqueue(
            secureLink, title, userAgent, contentDisposition, hintedMimeType, referrer
        ) { result ->
            activity.runOnUiThread {
                when (result) {
                    is DownloadStartResult.Enqueued ->
                        Toast.makeText(activity, "تم التحقق من الملف وإضافته إلى مدير التنزيلات", Toast.LENGTH_LONG).show()
                    is DownloadStartResult.OpenInBrowser -> {
                        Toast.makeText(activity, "هذا رابط صفحة أو تعذّر التأكد أنه ملف مباشر؛ سيتم فتحه في المتصفح", Toast.LENGTH_LONG).show()
                        openGameLink(result.finalUrl)
                    }
                    is DownloadStartResult.Failed ->
                        Toast.makeText(activity, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun showDownloadManager() {
        val entries = try {
            downloadRepository.getDownloads().map { item ->
                val statusText = when (item.status) {
                    DownloadManager.STATUS_PENDING -> "في الانتظار"
                    DownloadManager.STATUS_RUNNING -> "قيد التنزيل"
                    DownloadManager.STATUS_PAUSED -> "متوقف مؤقتاً"
                    DownloadManager.STATUS_SUCCESSFUL -> "اكتمل"
                    DownloadManager.STATUS_FAILED -> "فشل"
                    else -> "غير معروف"
                }
                val progress = if (item.totalBytes > 0L) " • ${(item.downloadedBytes * 100L / item.totalBytes).coerceIn(0L, 100L)}%" else ""
                "${item.title}\\n$statusText$progress"
            }
        } catch (error: Exception) {
            Toast.makeText(activity, "تعذر قراءة التنزيلات: ${error.message}", Toast.LENGTH_LONG).show()
            return
        }
        val message = if (entries.isEmpty()) {
            "لا توجد تنزيلات مسجلة حتى الآن. افتح تفاصيل لعبة واضغط على رابط التنزيل لإضافته هنا."
        } else {
            entries.joinToString("\n\n")
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("مدير التنزيلات • ${entries.size}")
            .setMessage(message)
            .setPositiveButton("تحديث", null)
            .setNeutralButton("فتح التنزيلات", null)
            .setNegativeButton("إغلاق", null)
            .create().apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        dismiss()
                        showDownloadManager()
                    }
                    getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                        try {
                            activity.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
                        } catch (_: Exception) {
                            Toast.makeText(activity, "تعذر فتح مدير تنزيلات النظام", Toast.LENGTH_SHORT).show()
                        }
                        dismiss()
                    }
                }
            }
        dialog.show()
        styleDialog(dialog)
    }

    fun openGameLink(link: String) {
        if (link.isBlank()) {
            Toast.makeText(activity, "لا يوجد رابط متاح", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val secureLink = SecureUrlPolicy.upgradeToHttps(link)
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(secureLink)))
        } catch (_: Exception) {
            Toast.makeText(activity, "تعذر فتح هذا الرابط", Toast.LENGTH_SHORT).show()
        }
    }
}
