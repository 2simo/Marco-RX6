package com.marcorx6.library.ui

import android.graphics.Color
import android.os.Build
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.marcorx6.library.AppDiagnostics
import java.util.WeakHashMap

/**
 * One policy for every app-owned Window (Activity, Dialog and bottom panel).
 *
 * The previous implementation only guarded MainActivity and skipped hide()
 * when root insets were stale during IME animation. Dialog windows had no IME
 * guard at all. Both paths allowed Android/OEM window transitions to reveal
 * the status bar while the keyboard was opening.
 */
internal object SystemBarPolicy {
    private val lock = Any()
    private val guardedDecorViews = WeakHashMap<android.view.View, Boolean>()

    fun apply(window: Window, darkMode: Boolean, hideNavigation: Boolean = false) {
        val decor = window.decorView
        window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, decor).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = !darkMode
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            hide(
                WindowInsetsCompat.Type.statusBars() or
                    if (hideNavigation) WindowInsetsCompat.Type.navigationBars() else 0
            )
        }
        installImeGuard(window, decor, hideNavigation)
        // Always issue hide, not only when cached root insets report visible:
        // cached insets can lag behind the animation callback's insets snapshot.
        decor.post {
            if (decor.isAttachedToWindow) {
                WindowInsetsControllerCompat(window, decor).hide(WindowInsetsCompat.Type.statusBars())
                if (hideNavigation) {
                    WindowInsetsControllerCompat(window, decor).hide(WindowInsetsCompat.Type.navigationBars())
                }
            }
        }
    }

    private fun installImeGuard(window: Window, decor: android.view.View, hideNavigation: Boolean) {
        synchronized(lock) {
            if (guardedDecorViews.containsKey(decor)) return
            guardedDecorViews[decor] = true
        }
        ViewCompat.setWindowInsetsAnimationCallback(
            decor,
            object : WindowInsetsAnimationCompat.Callback(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE
            ) {
                override fun onStart(
                    animation: WindowInsetsAnimationCompat,
                    bounds: WindowInsetsAnimationCompat.BoundsCompat
                ): WindowInsetsAnimationCompat.BoundsCompat {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                        hideBars(window, decor, hideNavigation)
                    }
                    return bounds
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    val imeAnimating = runningAnimations.any {
                        it.typeMask and WindowInsetsCompat.Type.ime() != 0
                    }
                    if (imeAnimating || insets.isVisible(WindowInsetsCompat.Type.statusBars())) {
                        hideBars(window, decor, hideNavigation)
                    }
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() == 0) return
                    decor.post { hideBars(window, decor, hideNavigation) }
                }
            }
        )
        AppDiagnostics.log("INFO", "SYSTEM_BARS", "IME guard installed for window decor=${decor.javaClass.simpleName}")
    }

    private fun hideBars(window: Window, decor: android.view.View, hideNavigation: Boolean) {
        if (!decor.isAttachedToWindow) return
        val types = WindowInsetsCompat.Type.statusBars() or
            if (hideNavigation) WindowInsetsCompat.Type.navigationBars() else 0
        WindowInsetsControllerCompat(window, decor).hide(types)
        AppDiagnostics.log("DEBUG", "SYSTEM_BARS", "Unconditionally reasserted hidden bars during IME/window transition")
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
