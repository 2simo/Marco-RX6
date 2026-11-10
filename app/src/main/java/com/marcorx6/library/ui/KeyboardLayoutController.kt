package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.WeakHashMap

/**
 * Shared IME behavior for app screens and dialogs.
 *
 * The ViewTreeObserver belongs to the window, not to an individual page. Installing
 * a focus listener every time a page is replaced therefore stacks listeners on the
 * same window and makes every focus transition run the same keyboard work repeatedly.
 * Keep exactly one listener per Window; dialogs naturally get their own listener.
 */
internal object KeyboardLayoutController {
    private val lock = Any()
    private val installedWindows = WeakHashMap<Window, Boolean>()

    fun install(activity: Activity, root: View) = install(activity.window, root)

    fun install(window: Window, root: View) {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val decor = window.decorView
        val observer = decor.viewTreeObserver
        if (!observer.isAlive) {
            com.marcorx6.library.AppDiagnostics.log(
                "WARN",
                "KEYBOARD_LAYOUT",
                "Skipped listener installation because decor ViewTreeObserver is unavailable"
            )
            return
        }

        synchronized(lock) {
            if (installedWindows.containsKey(window)) {
                com.marcorx6.library.AppDiagnostics.log(
                    "DEBUG",
                    "KEYBOARD_LAYOUT",
                    "Listener already installed for window; page root=${root.javaClass.simpleName} " +
                        "size=${root.width}x${root.height}"
                )
                return
            }
            installedWindows[window] = true
        }

        com.marcorx6.library.AppDiagnostics.log(
            "INFO",
            "KEYBOARD_LAYOUT",
            "Installed one window-level focus listener root=${root.javaClass.simpleName} " +
                "size=${root.width}x${root.height} decor=${decor.width}x${decor.height} " +
                "softInputMode=0x${window.attributes.softInputMode.toString(16)}"
        )

        // The IME can cause some Android 15 vendor skins to reveal the hidden
        // status bar while resizing the window. Re-assert the app's existing
        // immersive policy once the keyboard animation finishes; do not alter
        // IME visibility, focus, or navigation-bar behavior.
        ViewCompat.setWindowInsetsAnimationCallback(
            decor,
            object : WindowInsetsAnimationCompat.Callback(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE
            ) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat = insets

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() == 0) return
                    val current = ViewCompat.getRootWindowInsets(decor) ?: return
                    if (current.isVisible(WindowInsetsCompat.Type.statusBars())) {
                        WindowInsetsControllerCompat(window, decor)
                            .hide(WindowInsetsCompat.Type.statusBars())
                        com.marcorx6.library.AppDiagnostics.log(
                            "INFO",
                            "SYSTEM_BARS",
                            "Re-hid status bar after IME animation completed"
                        )
                    }
                }
            }
        )

        observer.addOnGlobalFocusChangeListener(
            object : ViewTreeObserver.OnGlobalFocusChangeListener {
                override fun onGlobalFocusChanged(oldFocus: View?, newFocus: View?) {
                    val input = newFocus as? EditText ?: return
                    com.marcorx6.library.AppDiagnostics.log(
                        "INFO",
                        "KEYBOARD_FOCUS",
                        "field=${input.javaClass.simpleName} id=${input.id} " +
                            "size=${input.width}x${input.height} inputType=${input.inputType} " +
                            "imeOptions=0x${input.imeOptions.toString(16)} " +
                            "singleLine=${input.maxLines == 1} hasFocus=${input.hasFocus()} " +
                            "shown=${input.isShown}"
                    )

                    // Wait for the focus/layout pass to finish. Ignore a stale field
                    // belonging to a page or dialog that has already been detached.
                    input.post {
                        if (input.rootView !== decor || input.windowToken == null ||
                            !input.isShown || !input.hasFocus() ||
                            input.width <= 0 || input.height <= 0
                        ) {
                            return@post
                        }

                        val requested = input.requestRectangleOnScreen(
                            Rect(0, 0, input.width, input.height),
                            true
                        )
                        com.marcorx6.library.AppDiagnostics.log(
                            "DEBUG",
                            "KEYBOARD_LAYOUT",
                            "requestRectangleOnScreen result=$requested " +
                                "fieldSize=${input.width}x${input.height}"
                        )
                    }
                }
            }
        )
    }
}
