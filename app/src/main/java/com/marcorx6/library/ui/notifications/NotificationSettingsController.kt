package com.marcorx6.library.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
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
        val systemPermissionEnabled = NotificationManagerCompat.from(activity).areNotificationsEnabled()
        AlertDialog.Builder(activity)
            .setTitle(translate("إشعارات المقالات الجديدة"))
            .setMessage(
                if (enabled && !systemPermissionEnabled)
                    translate("جدولة الإشعارات مفعّلة، لكن إشعارات التطبيق محظورة من إعدادات Android. اسمح بها من إعدادات التطبيق لتصلك التنبيهات.")
                else if (enabled)
                    translate("الإشعارات مفعّلة. يتحقق تطبيق MARCO من مدونتك دورياً وينبهك عند نشر مقال جديد.")
                else
                    translate("الإشعارات متوقفة. فعّلها ليصلك تنبيه عند نشر مقال جديد.")
            )
            .setPositiveButton(translate(when {
                enabled && !systemPermissionEnabled -> "فتح إعدادات الإشعارات"
                enabled -> "حسناً"
                else -> "تفعيل"
            })) { _, _ ->
                if (enabled && !systemPermissionEnabled) {
                    val appSettings = Intent("android.settings.APP_NOTIFICATION_SETTINGS").apply {
                        putExtra("android.provider.extra.APP_PACKAGE", activity.packageName)
                    }
                    try {
                        activity.startActivity(appSettings)
                    } catch (_: Exception) {
                        val details = Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + activity.packageName)
                        )
                        activity.startActivity(details)
                    }
                } else if (!enabled) {
                    scheduler.setEnabled(true)
                    scheduler.schedule()
                    requestPermission()
                    Toast.makeText(activity, translate("تم تفعيل إشعارات المقالات الجديدة"), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(translate(if (enabled) "إيقاف" else "لاحقاً")) { _, _ ->
                if (enabled) {
                    scheduler.setEnabled(false)
                    scheduler.cancel()
                    Toast.makeText(activity, translate("تم إيقاف الإشعارات"), Toast.LENGTH_SHORT).show()
                }
            }
            .show()
            .also { dialog ->
                styleDialog(dialog)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                    minHeight = (48 * activity.resources.displayMetrics.density).toInt()
                    contentDescription = text
                }
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                    minHeight = (48 * activity.resources.displayMetrics.density).toInt()
                    contentDescription = text
                }
            }
    }
}
