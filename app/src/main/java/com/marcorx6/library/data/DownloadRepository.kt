package com.marcorx6.library.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import com.marcorx6.library.network.SecureUrlPolicy
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

internal data class DownloadEntry(
    val title: String,
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long
)

internal sealed class DownloadStartResult {
    data class Enqueued(val title: String) : DownloadStartResult()
    data class OpenInBrowser(val finalUrl: String) : DownloadStartResult()
    data class Failed(val message: String) : DownloadStartResult()
}

/**
 * Handles secure URL inspection and Android DownloadManager operations.
 * Presentation and user feedback are owned by the UI layer.
 */
internal class DownloadRepository(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun enqueue(
        link: String,
        title: String,
        userAgent: String = "",
        contentDisposition: String = "",
        hintedMimeType: String = "",
        referrer: String = "",
        onResult: (DownloadStartResult) -> Unit
    ) {
        Thread {
            val result = try {
                val secureLink = SecureUrlPolicy.toHttpsUrl(link)
                    ?: throw SecurityException("رابط التنزيل غير آمن أو غير صالح؛ يجب استخدام HTTPS")
                val probe = inspect(secureLink, userAgent, contentDisposition, hintedMimeType)
                if (!probe.isDirectFile) {
                    DownloadStartResult.OpenInBrowser(probe.finalUrl)
                } else {
                    if (!probe.finalUrl.startsWith("https://", ignoreCase = true)) {
                        throw SecurityException("رابط التحويل النهائي لا يستخدم HTTPS؛ تم إيقاف التنزيل لحماية بياناتك")
                    }
                    val request = DownloadManager.Request(Uri.parse(probe.finalUrl))
                        .setTitle(title.ifBlank { "MARCO RX6" })
                        .setDescription("تنزيل ملف عبر MARCO RX6")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(false)
                    if (probe.mimeType.isNotBlank()) request.setMimeType(probe.mimeType)
                    if (userAgent.isNotBlank()) request.addRequestHeader("User-Agent", userAgent)
                    if (Uri.parse(secureLink).host.equals(Uri.parse(probe.finalUrl).host, ignoreCase = true)) {
                        CookieManager.getInstance().getCookie(probe.finalUrl)?.takeIf { it.isNotBlank() }?.let {
                            request.addRequestHeader("Cookie", it)
                        }
                    }
                    if (referrer.isNotBlank()) request.addRequestHeader("Referer", referrer)
                    manager.enqueue(request)
                    DownloadStartResult.Enqueued(title.ifBlank { "MARCO RX6" })
                }
            } catch (error: Exception) {
                DownloadStartResult.Failed(error.message ?: "تعذر بدء تنزيل الملف")
            }
            onResult(result)
        }.start()
    }

    fun getDownloads(): List<DownloadEntry> {
        val query = DownloadManager.Query().setFilterByStatus(
            DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING or
                DownloadManager.STATUS_PAUSED or DownloadManager.STATUS_SUCCESSFUL or
                DownloadManager.STATUS_FAILED
        )
        val entries = mutableListOf<DownloadEntry>()
        manager.query(query)?.use { cursor ->
            while (cursor.moveToNext()) {
                entries += DownloadEntry(
                    title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)),
                    status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    downloadedBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                )
            }
        }
        return entries
    }

    private data class DownloadProbe(
        val finalUrl: String,
        val mimeType: String,
        val isDirectFile: Boolean
    )

    private fun inspect(
        link: String,
        userAgent: String,
        hintedDisposition: String,
        hintedMimeType: String
    ): DownloadProbe {
        val connection = (URL(link).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 12000
            readTimeout = 12000
            requestMethod = "GET"
            setRequestProperty("Range", "bytes=0-0")
            if (userAgent.isNotBlank()) setRequestProperty("User-Agent", userAgent)
            CookieManager.getInstance().getCookie(link)?.takeIf { it.isNotBlank() }?.let {
                setRequestProperty("Cookie", it)
            }
        }
        try {
            val code = connection.responseCode
            val mime = (connection.contentType ?: hintedMimeType)
                .substringBefore(';').trim().lowercase(Locale.ROOT)
            val disposition = connection.getHeaderField("Content-Disposition") ?: hintedDisposition
            val finalUrl = connection.url.toString()
            if (code !in 200..299) throw IllegalStateException("الخادم رفض رابط التنزيل (HTTP $code)")
            if (mime == "text/html" || mime == "application/xhtml+xml") {
                throw IllegalStateException("هذا الرابط يفتح صفحة HTML وليس ملفاً مباشراً. افتح صفحة التحميل وابحث عن رابط الملف نفسه.")
            }
            val path = Uri.parse(finalUrl).lastPathSegment.orEmpty().lowercase(Locale.ROOT)
            val hasFileExtension = Regex("""\\.(apk|zip|7z|rar|iso|pkg|bin|cue|chd|img|pbp|cso|nsp|xci|pdf|mp4|mp3)$""").containsMatchIn(path)
            val isAttachment = disposition.contains("attachment", ignoreCase = true)
            val isBinary = mime == "application/octet-stream" || mime.startsWith("application/") ||
                mime.startsWith("video/") || mime.startsWith("audio/")
            return DownloadProbe(finalUrl, mime, isAttachment || isBinary || hasFileExtension)
        } finally {
            connection.disconnect()
        }
    }
}
