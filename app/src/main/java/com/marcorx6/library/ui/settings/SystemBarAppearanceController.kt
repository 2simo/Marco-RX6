package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Color
import android.os.Build
import androidx.core.view.WindowInsetsControllerCompat

internal class SystemBarAppearanceController(
    private val activity: Activity,
    private val isDarkMode: () -> Boolean
) {
    fun apply() {
        // Keep app content below the Android system bars on every screen.
        // This prevents headers and controls from drawing underneath the status bar.
        activity.window.statusBarColor = if (isDarkMode()) Color.rgb(8, 9, 12) else Color.rgb(245, 247, 250)
        activity.window.navigationBarColor = if (isDarkMode()) Color.rgb(8, 9, 12) else Color.rgb(245, 247, 250)
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkMode()
            isAppearanceLightNavigationBars = !isDarkMode()
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isStatusBarContrastEnforced = false
            activity.window.isNavigationBarContrastEnforced = false
        }
    }
}
