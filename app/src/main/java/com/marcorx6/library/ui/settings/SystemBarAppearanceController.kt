package com.marcorx6.library.ui

import android.graphics.Color
import android.os.Build
import android.view.Window
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.marcorx6.library.AppDiagnostics

/**
 * One policy for every app-owned Window (Activity, Dialog and bottom panel).
 *
 * The previous implementation only guarded MainActivity and skipped hide()
 * when root insets were stale during IME animation. Dialog windows had no IME
 * guard at all. Both paths allowed Android/OEM window transitions to reveal
 * the status bar while the keyboard was opening.
 */
internal object SystemBarPolicy {
    fun apply(window: Window, darkMode: Boolean, hideNavigation: Boolean = false) {
        val surface = if (darkMode) Color.rgb(8, 9, 12) else Color.rgb(245, 247, 250)
        val decor = window.decorView
        // Keep the system-bar backing surface identical to the app's top surface.
        // The status bar is intentionally visible; the Activity app bar owns its inset.
        decor.setBackgroundColor(surface)
        window.statusBarColor = surface
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, decor).apply {
            isAppearanceLightStatusBars = !darkMode
            isAppearanceLightNavigationBars = !darkMode
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            show(WindowInsetsCompat.Type.statusBars())
            if (hideNavigation) hide(WindowInsetsCompat.Type.navigationBars())
            else show(WindowInsetsCompat.Type.navigationBars())
        }
    }
}

internal class SystemBarAppearanceController(
    private val activity: android.app.Activity,
    private val isDarkMode: () -> Boolean
) {
    fun apply() {
        val decor = activity.window.decorView
        val insets = ViewCompat.getRootWindowInsets(decor)
        AppDiagnostics.log(
            "DEBUG",
            "SYSTEM_BARS",
            "apply statusVisible=${insets?.isVisible(WindowInsetsCompat.Type.statusBars()) == true} " +
                "imeVisible=${insets?.isVisible(WindowInsetsCompat.Type.ime()) == true} " +
                "windowFlags=0x${activity.window.attributes.flags.toString(16)}"
        )
        activity.window.navigationBarColor =
            if (isDarkMode()) Color.rgb(8, 9, 12) else Color.rgb(245, 247, 250)
        SystemBarPolicy.apply(activity.window, isDarkMode())
    }
}
