package com.marcorx6.library.data.auth

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import com.marcorx6.library.ui.activity.MainActivity
import com.marcorx6.library.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.net.HttpURLConnection
import java.net.URL

/** Receives FCM data messages and displays them as Android notifications. */
class MarcoFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FirebasePushTokenRepository(applicationContext).saveToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val title = data["title"]?.take(80)
            ?: message.notification?.title?.take(80)
            ?: "MARCO"
        val body = data["body"]?.take(180)
            ?: message.notification?.body?.take(180)
            ?: return

        val channelId = "marco_social_updates"
        ensureChannel(channelId)
        val openApp = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data.forEach { (key, value) -> putExtra("push_$key", value) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val appIcon = BitmapFactory.decodeResource(resources, R.drawable.marco_rx6_dragon)
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification_marco)
            .setLargeIcon(appIcon)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // Only game notifications include artwork; social messages remain text-only.
        val cover = if (data["type"] == "game") downloadCover(data["coverUrl"]) else null
        if (cover != null) {
            builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(cover)
                        .setSummaryText(body)
                )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }
        val notification = builder.build()
        // Android 13+ can revoke notification permission at runtime.
        // Do not attempt to post when permission is unavailable.
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        try {
            NotificationManagerCompat.from(this).notify(
                (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
                notification
            )
        } catch (_: SecurityException) {
            // Permission may be revoked between the check and notify call.
        }
    }

    private fun downloadCover(rawUrl: String?): Bitmap? {
        if (rawUrl.isNullOrBlank() || rawUrl.length > 2000) return null
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(rawUrl)
            if (url.protocol != "https") return null
            connection = (url.openConnection() as? HttpURLConnection) ?: return null
            connection.connectTimeout = 4000
            connection.readTimeout = 5000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "image/*")
            connection.connect()
            if (connection.responseCode !in 200..299) return null
            val length = connection.contentLength
            if (length > 5 * 1024 * 1024) return null
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun ensureChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "إشعارات MARCO",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "رسائل ومتابعون وتحديثات الألعاب" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
