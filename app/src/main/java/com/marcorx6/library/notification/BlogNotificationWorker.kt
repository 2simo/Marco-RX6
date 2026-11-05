package com.marcorx6.library.notification

import com.marcorx6.library.ui.activity.MainActivity
import com.marcorx6.library.ui.LanguageTranslator

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

open class BlogNotificationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return Result.success()
        // Do not advance last_seen_post_id when Android notification permission is
        // missing; the next periodic run can deliver the pending post after permission is granted.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        return try {
            val connection = URL(FEED_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("Accept", "application/json")
            val body = try {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                connection.disconnect()
            }
            val feed = JSONObject(body).optJSONObject("feed") ?: return Result.retry()
            val entries = feed.optJSONArray("entry") ?: return Result.success()
            if (entries.length() == 0) return Result.success()
            val newest = entries.optJSONObject(0) ?: return Result.success()
            val postId = newest.optJSONObject("id")?.optString("\$t").orEmpty()
            val title = newest.optJSONObject("title")?.optString("\$t").orEmpty()
            if (postId.isBlank() || title.isBlank()) return Result.success()
            val previousId = prefs.getString("last_seen_post_id", null)
            if (previousId == null) {
                prefs.edit().putString("last_seen_post_id", postId).apply()
                return Result.success()
            }
            if (postId != previousId) {
                // Notify for every newly published post still present in the feed.
                for (index in 0 until entries.length()) {
                    val entry = entries.optJSONObject(index) ?: continue
                    val currentId = entry.optJSONObject("id")?.optString("\$t").orEmpty()
                    if (currentId.isBlank() || currentId == previousId) break
                    val currentTitle = entry.optJSONObject("title")?.optString("\$t").orEmpty()
                    if (currentTitle.isNotBlank()) {
                        showNotification(currentTitle, findPostUrl(entry))
                    }
                }
                prefs.edit().putString("last_seen_post_id", postId).apply()
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun findPostUrl(entry: org.json.JSONObject): String {
        val links = entry.optJSONArray("link") ?: return ""
        for (index in 0 until links.length()) {
            val item = links.optJSONObject(index) ?: continue
            if (item.optString("rel") == "alternate") {
                val href = item.optString("href", "")
                if (href.startsWith("https://")) return href
            }
        }
        return ""
    }

    private fun showNotification(postTitle: String, postUrl: String) {
        val language = applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getString("language", "ar") ?: "ar"
        val localize: (String) -> String = { LanguageTranslator.translate(it, language) }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, localize("إشعارات المقالات الجديدة"), NotificationManager.IMPORTANCE_DEFAULT)
            applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val openTarget = if (postUrl.startsWith("https://")) {
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(postUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        val notificationId = postUrl.hashCode().let { if (it == 0) 4106 else it }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, notificationId, openTarget,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(localize("مقال جديد على MARCO RX6"))
            .setContentText(postTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(postTitle))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Notification permission may have been revoked while the worker was running.
        }
    }

    companion object {
        const val WORK_NAME = "marcorx6_blog_post_notifications"
        const val CHANNEL_ID = "marcorx6_new_posts"
        private const val FEED_URL = "https://marcorx6.blogspot.com/feeds/posts/default?alt=json&max-results=5"
    }
}
