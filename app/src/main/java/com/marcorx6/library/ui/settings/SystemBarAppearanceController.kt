package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Color
import android.os.Build
import com.marcorx6.library.AppDiagnostics
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal class SystemBarAppearanceController(
    private val activity: Activity,
    private val isDarkMode: () -> Boolean
) {
    fun apply() {
        val before = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        AppDiagnostics.log(
            "DEBUG",
            "SYSTEM_BARS",
            "apply before statusVisible=${before.isVisible(WindowInsetsCompat.Type.statusBars())} " +
                "navigationVisible=${before.isVisible(WindowInsetsCompat.Type.navigationBars())} " +
                "systemUi=0x${activity.window.decorView.systemUiVisibility.toString(16)}"
        )
        // Hide only the top status bar. Keep navigation gestures and the bottom
        // gesture indicator available, including Android's edge-back gesture.
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = if (isDarkMode()) Color.rgb(8, 9, 12) else Color.rgb(245, 247, 250)
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightNavigationBars = !isDarkMode()
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isStatusBarContrastEnforced = false
            activity.window.isNavigationBarContrastEnforced = false
        }
        val after = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        AppDiagnostics.log(
            "DEBUG",
            "SYSTEM_BARS",
            "apply after statusVisible=${after.isVisible(WindowInsetsCompat.Type.statusBars())} " +
                "navigationVisible=${after.isVisible(WindowInsetsCompat.Type.navigationBars())} " +
                "statusColor=0x${activity.window.statusBarColor.toUInt().toString(16)} " +
                "navigationColor=0x${activity.window.navigationBarColor.toUInt().toString(16)}"
        )
    }
}
