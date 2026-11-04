package com.marcorx6.library.ui

import android.app.Activity
import android.content.SharedPreferences
import android.view.View
import android.widget.Toast
import android.app.AlertDialog

internal class AppearanceSettingsController(
    private val activity: Activity,
    private val preferences: SharedPreferences,
    private val applyMotionPreferences: (View) -> Unit,
    private val styleDialog: (AlertDialog) -> Unit,
    private val recreateActivity: () -> Unit
) {
    fun show() {
        val choices = arrayOf("أزرق")
        val keys = arrayOf("blue")
        val selectedIndex = keys.indexOf(preferences.getString("accent", "blue")).coerceAtLeast(0)
        AlertDialog.Builder(activity).setTitle("تخصيص الواجهة")
            .setMessage("اللون الرئيسي المعتمد في MARCO RX6 هو الأزرق.")
            .setSingleChoiceItems(choices, selectedIndex) { dialog, which ->
                preferences.edit().putString("accent", keys[which]).apply()
                dialog.dismiss()
                recreateActivity()
            }
            .setNeutralButton(if (preferences.getBoolean("animations", true)) "إيقاف الحركات" else "تفعيل الحركات") { _, _ ->
                val enabled = !preferences.getBoolean("animations", true)
                preferences.edit().putBoolean("animations", enabled).apply()
                applyMotionPreferences(activity.window.decorView)
                Toast.makeText(activity, if (enabled) "تم تفعيل انتقالات الواجهة" else "تم إيقاف انتقالات الواجهة", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("إغلاق", null).show().also(styleDialog)
    }
}
