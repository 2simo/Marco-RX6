package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
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
    fun install(activity: Activity, root: View) {
        activity.window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        val observer = root.viewTreeObserver
        if (!observer.isAlive) return

        observer.addOnGlobalFocusChangeListener(
            object : ViewTreeObserver.OnGlobalFocusChangeListener {
                override fun onGlobalFocusChanged(oldFocus: View?, newFocus: View?) {
                    val input = newFocus as? EditText ?: return
                    input.post {
                        if (!input.isShown || !input.hasFocus() || input.width <= 0 || input.height <= 0) {
                            return@post
                        }
                        input.requestRectangleOnScreen(
                            Rect(0, 0, input.width, input.height),
                            true
                        )
                    }
                }
            }
        )
    }
}
