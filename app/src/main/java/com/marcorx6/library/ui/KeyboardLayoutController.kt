package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import android.widget.EditText

/**
 * Shared IME behavior for app screens and dialogs.
 *
 * ADJUST_RESIZE keeps the usable window above the keyboard. When an input gains
 * focus, asking Android to reveal its rectangle lets enclosing ScrollViews move
 * it into the visible area instead of leaving it underneath the IME.
 */
internal object KeyboardLayoutController {
    fun install(activity: Activity, root: View) = install(activity.window, root)

    fun install(window: Window, root: View) {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        com.marcorx6.library.AppDiagnostics.log(
            "DEBUG",
            "KEYBOARD_LAYOUT",
            "Installed root=${root.javaClass.simpleName} size=${root.width}x${root.height} " +
                "softInputMode=0x${window.attributes.softInputMode.toString(16)}"
        )

        val observer = root.viewTreeObserver
        if (!observer.isAlive) return

        observer.addOnGlobalFocusChangeListener(
            object : ViewTreeObserver.OnGlobalFocusChangeListener {
                override fun onGlobalFocusChanged(oldFocus: View?, newFocus: View?) {
                    val input = newFocus as? EditText ?: return
                    com.marcorx6.library.AppDiagnostics.log(
                        "INFO",
                        "KEYBOARD_FOCUS",
                        "field=${input.javaClass.simpleName} id=${input.id} size=${input.width}x${input.height} " +
                            "inputType=${input.inputType} imeOptions=0x${input.imeOptions.toString(16)} " +
                            "singleLine=${input.maxLines == 1} hasFocus=${input.hasFocus()} shown=${input.isShown}"
                    )
                    input.post {
                        if (!input.isShown || !input.hasFocus() || input.width <= 0 || input.height <= 0) {
                            return@post
                        }
                        val requested = input.requestRectangleOnScreen(
                            Rect(0, 0, input.width, input.height),
                            true
                        )
                        com.marcorx6.library.AppDiagnostics.log(
                            "DEBUG",
                            "KEYBOARD_LAYOUT",
                            "requestRectangleOnScreen result=$requested fieldSize=${input.width}x${input.height}"
                        )
                    }
                }
            }
        )
    }
}
