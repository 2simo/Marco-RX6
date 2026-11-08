package com.marcorx6.library.data.auth

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.marcorx6.library.ui.activity.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

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
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(this).notify(
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            notification
        )
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
