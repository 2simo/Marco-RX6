package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Color
import android.os.Build
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal class SystemBarAppearanceController(
    private val activity: Activity,
    private val isDarkMode: () -> Boolean
) {
    fun apply() {
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
    }
}
