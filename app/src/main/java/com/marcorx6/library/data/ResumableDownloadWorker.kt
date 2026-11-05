package com.marcorx6.library.data

import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.marcorx6.library.R
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

internal class ResumableDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1L)
        if (id <= 0L) return Result.failure()
        val database = DownloadDatabase.get(applicationContext)
        val item = database.get(id) ?: return Result.failure()
        if (item.status == DownloadManager.STATUS_PAUSED) return Result.success()
        return try {
            setForeground(createForegroundInfo(item.title))
            download(item, database)
        } catch (error: Exception) {
            val latest = database.get(id)
            if (isStopped || latest?.status == DownloadManager.STATUS_PAUSED) {
                Result.success()
            } else {
                database.status(id, DownloadManager.STATUS_FAILED, error.message ?: "Download failed")
                Result.failure()
            }
        }
    }

    private fun download(item: StoredDownload, database: DownloadDatabase): Result {
        val directory = File(
            applicationContext.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?: applicationContext.filesDir,
            "MARCO_RX6"
        ).apply { mkdirs() }
        val safeName = item.title.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_').take(90).ifBlank { "download_${item.id}" }
        val partial = File(directory, "${item.id}.part")
        val output = File(directory, "${item.id}_$safeName")
        var offset = if (partial.exists()) partial.length() else 0L
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(item.url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20000
                readTimeout = 30000
                requestMethod = "GET"
                if (item.userAgent.isNotBlank()) setRequestProperty("User-Agent", item.userAgent)
                if (item.cookie.isNotBlank()) setRequestProperty("Cookie", item.cookie)
                if (item.referrer.isNotBlank()) setRequestProperty("Referer", item.referrer)
                if (offset > 0L) {
                    setRequestProperty("Range", "bytes=$offset-")
                    if (item.etag.isNotBlank()) setRequestProperty("If-Range", item.etag)
                    else if (item.lastModified.isNotBlank()) setRequestProperty("If-Range", item.lastModified)
                }
            }
            val code = connection.responseCode
            if (!connection.url.protocol.equals("https", true)) {
                throw SecurityException("Download redirect did not use HTTPS")
            }
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val append = offset > 0L && code == HttpURLConnection.HTTP_PARTIAL
            if (offset > 0L && !append) {
                // The server ignored Range or the entity changed. Restart safely instead of
                // appending a full response to an old partial file.
                partial.delete()
                offset = 0L
            }
            val responseLength = connection.getHeaderFieldLong("Content-Length", -1L)
            val total = if (append && responseLength >= 0L) offset + responseLength else responseLength
            val etag = connection.getHeaderField("ETag") ?: item.etag
            val modified = connection.getHeaderField("Last-Modified") ?: item.lastModified
            val mime = connection.contentType?.substringBefore(';')?.trim().orEmpty().ifBlank { item.mimeType }
            database.progress(item.id, offset, total, etag, modified)
            connection.inputStream.use { input ->
                FileOutputStream(partial, append).use { sink ->
                    val buffer = ByteArray(64 * 1024)
                    var lastUpdate = System.currentTimeMillis()
                    while (true) {
                        if (isStopped) throw InterruptedException("Download paused or cancelled")
                        val count = input.read(buffer)
                        if (count < 0) break
                        sink.write(buffer, 0, count)
                        offset += count
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 500L) {
                            database.progress(item.id, offset, total, etag, modified)
                            setProgressAsync(androidx.work.workDataOf("downloaded" to offset, "total" to total))
                            lastUpdate = now
                        }
                    }
                    sink.fd.sync()
                }
            }
            if (total > 0L && offset != total) {
                throw IllegalStateException("Incomplete file: $offset / $total")
            }
            if (output.exists()) output.delete()
            if (!partial.renameTo(output)) {
                partial.copyTo(output, overwrite = true)
                partial.delete()
            }
            database.complete(item.id, output.absolutePath, mime)
            return Result.success()
        } finally {
            connection?.disconnect()
        }
    }

    private fun createForegroundInfo(title: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "MARCO RX6 downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification: Notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title.ifBlank { "MARCO RX6" })
            .setContentText("Downloading")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_ID = "download_id"
        private const val CHANNEL_ID = "marco_rx6_downloads"
        private const val NOTIFICATION_ID = 6406
    }
}
