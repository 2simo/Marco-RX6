package com.marcorx6.library.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.marcorx6.library.network.SecureUrlPolicy
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit

internal data class DownloadEntry(
    val id: Long,
    val title: String,
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val mimeType: String?,
    val sourcePostUrl: String
)

internal sealed class DownloadStartResult {
    data class Enqueued(val title: String) : DownloadStartResult()
    data class OpenInBrowser(val finalUrl: String) : DownloadStartResult()
    data class Failed(val message: String) : DownloadStartResult()
}

/** Owns the app's persistent, resumable download queue. */
internal class DownloadRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = DownloadDatabase.get(appContext)
    private val workManager = WorkManager.getInstance(appContext)

    fun enqueue(
        link: String,
        title: String,
        userAgent: String = "",
        contentDisposition: String = "",
        hintedMimeType: String = "",
        referrer: String = "",
        sourcePostUrl: String = "",
        onResult: (DownloadStartResult) -> Unit
    ) {
        Thread {
            val result = try {
                val secureLink = SecureUrlPolicy.toHttpsUrl(link)
                    ?: throw SecurityException("رابط التنزيل غير آمن أو غير صالح؛ يجب استخدام HTTPS")
                val probe = inspect(secureLink, userAgent, contentDisposition, hintedMimeType, referrer)
                if (!probe.isDirectFile) {
                    DownloadStartResult.OpenInBrowser(probe.finalUrl)
                } else {
                    if (!probe.finalUrl.startsWith("https://", ignoreCase = true)) {
                        throw SecurityException("رابط التحويل النهائي لا يستخدم HTTPS؛ تم إيقاف التنزيل لحماية بياناتك")
                    }
                    val finalTitle = probe.fileName ?: title.ifBlank { "MARCO RX6" }
                    val cookie = CookieManager.getInstance().getCookie(probe.finalUrl).orEmpty()
                    val previous = database.findFailedBySource(sourcePostUrl)
                    if (previous != null) {
                        database.refreshSource(previous.id, probe.finalUrl, userAgent, cookie, referrer, probe.mimeType)
                        enqueueWork(previous.id, ExistingWorkPolicy.REPLACE)
                    } else {
                        val id = System.currentTimeMillis().coerceAtLeast(1L)
                        database.insert(id, probe.finalUrl, finalTitle, userAgent, cookie, referrer, probe.mimeType, sourcePostUrl)
                        enqueueWork(id, ExistingWorkPolicy.KEEP)
                    }
                    DownloadStartResult.Enqueued(finalTitle)
                }
            } catch (error: Exception) {
                DownloadStartResult.Failed(error.message ?: "تعذر بدء تنزيل الملف")
            }
            onResult(result)
        }.start()
    }

    private fun enqueueWork(id: Long, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<ResumableDownloadWorker>()
            .setInputData(workDataOf(ResumableDownloadWorker.KEY_ID to id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(workName(id), policy, request)
    }

    fun getDownloads(): List<DownloadEntry> = database.all().map { item ->
        DownloadEntry(
            id = item.id,
            title = item.title,
            status = item.status,
            downloadedBytes = item.downloadedBytes,
            totalBytes = item.totalBytes,
            mimeType = item.mimeType,
            sourcePostUrl = item.sourcePostUrl
        )
    }

    fun pauseDownload(id: Long): Boolean {
        val item = database.get(id) ?: return false
        if (item.status == DownloadManager.STATUS_SUCCESSFUL) return false
        // Request cancellation first, then persist PAUSED so the row remains visible
        // while WorkManager transitions the worker to its cancelled state.
        workManager.cancelUniqueWork(workName(id))
        database.status(id, DownloadManager.STATUS_PAUSED)
        return true
    }

    fun resumeDownload(id: Long): Boolean {
        val item = database.get(id) ?: return false
        if (item.status == DownloadManager.STATUS_SUCCESSFUL) return false
        database.status(id, DownloadManager.STATUS_PENDING)
        enqueueWork(id, ExistingWorkPolicy.REPLACE)
        return true
    }

    fun openDownloadedFile(id: Long): Uri? {
        val item = database.get(id) ?: return null
        if (item.status != DownloadManager.STATUS_SUCCESSFUL || item.localPath.isBlank()) return null
        if (item.localPath.startsWith("content://")) return Uri.parse(item.localPath)
        val file = File(item.localPath)
        if (!file.isFile) return null
        return FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.files",
            file
        )
    }

    fun deleteDownload(id: Long): Boolean {
        val item = database.get(id) ?: return false
        database.cancel(id)
        workManager.cancelUniqueWork(workName(id))
        if (item.localPath.startsWith("content://")) {
            runCatching { DocumentsContract.deleteDocument(appContext.contentResolver, Uri.parse(item.localPath)) }
        } else if (item.localPath.isNotBlank()) {
            File(item.localPath).delete()
        }
        val directory = File(appContext.filesDir, "MARCO_RX6_downloads")
        File(directory, "$id.part").delete()
        database.delete(id)
        return true
    }

    private fun workName(id: Long) = "marco_rx6_download_$id"

    private data class DownloadProbe(
        val finalUrl: String,
        val mimeType: String,
        val isDirectFile: Boolean,
        val fileName: String?
    )

    private fun inspect(
        link: String,
        userAgent: String,
        hintedDisposition: String,
        hintedMimeType: String,
        referrer: String
    ): DownloadProbe {
        val connection = (URL(link).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 12000
            readTimeout = 12000
            requestMethod = "GET"
            setRequestProperty("Range", "bytes=0-0")
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Accept-Encoding", "identity")
            if (userAgent.isNotBlank()) setRequestProperty("User-Agent", userAgent)
            if (referrer.isNotBlank()) setRequestProperty("Referer", referrer)
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
                return DownloadProbe(finalUrl, mime, false, null)
            }
            val path = Uri.parse(finalUrl).lastPathSegment.orEmpty().lowercase(Locale.ROOT)
            val hasFileExtension = Regex("""\.(apk|zip|7z|rar|iso|pkg|bin|cue|chd|img|pbp|cso|nsp|xci|pdf|mp4|mp3)$""").containsMatchIn(path)
            val responseFileName = DownloadFileNames.fromResponse(
                connection.getHeaderField("Content-Disposition"),
                finalUrl
            ) ?: DownloadFileNames.fromResponse(hintedDisposition, finalUrl)
            val isAttachment = disposition.contains("attachment", ignoreCase = true) || responseFileName != null
            val structuredText = mime in setOf(
                "application/json", "application/ld+json", "application/xml",
                "application/javascript", "application/x-javascript",
                "application/x-www-form-urlencoded", "application/problem+json"
            ) || mime.startsWith("text/")
            val isBinary = mime == "application/octet-stream" ||
                (mime.startsWith("application/") && !structuredText) ||
                mime.startsWith("video/") || mime.startsWith("audio/")
            return DownloadProbe(finalUrl, mime, isAttachment || isBinary || hasFileExtension, responseFileName)
        } finally {
            connection.disconnect()
        }
    }
}
