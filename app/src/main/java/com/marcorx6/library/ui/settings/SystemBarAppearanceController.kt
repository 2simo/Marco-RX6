package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.marcorx6.library.AppDiagnostics

/**
 * Single owner of the Activity status-bar policy.
 *
 * IME transitions can make Android 15/16 vendor builds briefly restore the
 * status-bar surface. Keep the policy here (rather than competing keyboard
 * listeners), reassert it through the complete IME animation, and use an
 * opaque fallback color so a transient system reveal never becomes a pink/
 * keyboard-tinted strip.
 */
internal class SystemBarAppearanceController(
    private val activity: Activity,
    private val isDarkMode: () -> Boolean
) {
    private var imeCallbackInstalled = false

    fun apply() {
        val window = activity.window
        val decor = window.decorView
        val beforeInsets = ViewCompat.getRootWindowInsets(decor)
        AppDiagnostics.log(
            "DEBUG",
            "SYSTEM_BARS",
            "apply statusVisible=${beforeInsets?.isVisible(WindowInsetsCompat.Type.statusBars()) == true} " +
                "imeVisible=${beforeInsets?.isVisible(WindowInsetsCompat.Type.ime()) == true} " +
                "systemUi=0x${decor.systemUiVisibility.toString(16)}"
        )

        // Keep a solid dark system surface as a fallback if the OS momentarily
        // exposes the status bar during keyboard/window transitions.
        window.statusBarColor = Color.rgb(8, 9, 12)
        window.navigationBarColor =
            if (isDarkMode()) Color.rgb(8, 9, 12) else Color.rgb(245, 247, 250)

        WindowInsetsControllerCompat(window, decor).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = !isDarkMode()
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            hide(WindowInsetsCompat.Type.statusBars())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        installImeStatusBarGuard(window, decor)
        // Posting after the current layout/insets pass avoids a competing
        // synchronous request while Android is dispatching IME insets.
        decor.post {
            if (!activity.isFinishing && !activity.isDestroyed) hideStatusBar(window, decor, "post-layout")
        }
    }

    private fun installImeStatusBarGuard(window: Window, decor: android.view.View) {
        if (imeCallbackInstalled) return
        imeCallbackInstalled = true
        ViewCompat.setWindowInsetsAnimationCallback(
            decor,
            object : WindowInsetsAnimationCompat.Callback(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE
            ) {
                override fun onStart(
                    animation: WindowInsetsAnimationCompat,
                    bounds: WindowInsetsAnimationCompat.Bounds
                ): WindowInsetsAnimationCompat.Bounds {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                        hideStatusBar(window, decor, "ime-start")
                    }
                    return bounds
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    if (runningAnimations.any {
                            it.typeMask and WindowInsetsCompat.Type.ime() != 0
                        } && insets.isVisible(WindowInsetsCompat.Type.statusBars())
                    ) {
                        hideStatusBar(window, decor, "ime-progress")
                    }
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() == 0) return
                    decor.post {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            hideStatusBar(window, decor, "ime-end")
                        }
                    }
                }
            }
        )
        AppDiagnostics.log("INFO", "SYSTEM_BARS", "Installed centralized IME/status-bar guard")
    }

    private fun hideStatusBar(window: Window, decor: android.view.View, source: String) {
        val insets = ViewCompat.getRootWindowInsets(decor)
        if (insets?.isVisible(WindowInsetsCompat.Type.statusBars()) == true) {
            WindowInsetsControllerCompat(window, decor)
                .hide(WindowInsetsCompat.Type.statusBars())
            AppDiagnostics.log("INFO", "SYSTEM_BARS", "Re-hid status bar source=$source")
        }
    }
}
