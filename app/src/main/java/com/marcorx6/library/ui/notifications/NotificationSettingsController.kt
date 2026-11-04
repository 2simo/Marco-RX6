package com.marcorx6.library.ui

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import com.marcorx6.library.notification.BlogNotificationScheduler

/**
 * Owns the notification preferences dialog and its scheduling actions.
 * Permission prompting remains in the Activity because it uses the Activity Result API.
 */
internal class NotificationSettingsController(
    private val activity: Activity,
    private val scheduler: BlogNotificationScheduler,
    private val styleDialog: (AlertDialog) -> Unit,
    private val translate: (String) -> String,
    private val requestPermission: () -> Unit
) {
    fun show() {
        val enabled = scheduler.isEnabled()
        AlertDialog.Builder(activity)
            .setTitle(translate("إشعارات المقالات الجديدة"))
            .setMessage(
                if (enabled)
                    translate("الإشعارات مفعّلة. يتحقق تطبيق MARCO RX6 من مدونتك دورياً وينبهك عند نشر مقال جديد.")
                else
                    translate("الإشعارات متوقفة. فعّلها ليصلك تنبيه عند نشر مقال جديد.")
            )
            .setPositiveButton(translate(if (enabled) "إبقاء مفعّلة" else "تفعيل")) { _, _ ->
                scheduler.setEnabled(true)
                scheduler.schedule()
                requestPermission()
                Toast.makeText(activity, translate("تم تفعيل إشعارات المقالات الجديدة"), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(translate("إيقاف")) { _, _ ->
                scheduler.setEnabled(false)
                scheduler.cancel()
                Toast.makeText(activity, translate("تم إيقاف الإشعارات"), Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(translate("إغلاق"), null)
            .show()
            .also(styleDialog)
    }
}
