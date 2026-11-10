package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.marcorx6.library.AppDiagnostics
import java.util.WeakHashMap

/**
 * Passive diagnostics for window, system-bar, layout and input behavior.
 * It records metadata only: never reads or stores text entered by the user.
 */
internal object UiDiagnosticsMonitor {
    private val lock = Any()
    private val installedWindows = WeakHashMap<Window, Boolean>()

    fun install(activity: Activity) = install(activity.window, activity.javaClass.simpleName)

    fun install(window: Window, owner: String = "Window") {
        synchronized(lock) {
            if (installedWindows.containsKey(window)) return
            installedWindows[window] = true
        }

        val decor = window.decorView
        AppDiagnostics.log(
            "INFO",
            "UI_WINDOW",
            "Monitor installed owner=$owner softInputMode=0x${window.attributes.softInputMode.toString(16)} " +
                "windowFlags=0x${window.attributes.flags.toString(16)} " +
                "statusBarColor=0x${window.statusBarColor.toUInt().toString(16)} " +
                "navigationBarColor=0x${window.navigationBarColor.toUInt().toString(16)}"
        )

        val observer = decor.viewTreeObserver
        if (observer.isAlive) {
            observer.addOnGlobalFocusChangeListener(
                object : ViewTreeObserver.OnGlobalFocusChangeListener {
                    override fun onGlobalFocusChanged(oldFocus: View?, newFocus: View?) {
                        fun describe(view: View?): String {
                            if (view == null) return "none"
                            val idName = runCatching {
                                if (view.id == View.NO_ID) "no-id"
                                else view.resources.getResourceEntryName(view.id)
                            }.getOrDefault("id-${view.id}")
                            val inputDetails = if (view is EditText) {
                                " inputType=${view.inputType} imeOptions=0x${view.imeOptions.toString(16)}" +
                                    " singleLine=${view.isSingleLine} enabled=${view.isEnabled}" +
                                    " textLength=${view.text?.length ?: 0}"
                            } else ""
                            return "${view.javaClass.simpleName}(id=$idName,w=${view.width},h=${view.height},shown=${view.isShown})$inputDetails"
                        }
                        AppDiagnostics.log(
                            "INFO",
                            "UI_FOCUS",
                            "owner=$owner old=${describe(oldFocus)} new=${describe(newFocus)}"
                        )
                    }
                }
            )

            var lastLayoutState: String? = null
            observer.addOnGlobalLayoutListener {
                val visibleFrame = Rect()
                decor.getWindowVisibleDisplayFrame(visibleFrame)
                val insets = ViewCompat.getRootWindowInsets(decor)
                val imeVisible = insets?.isVisible(WindowInsetsCompat.Type.ime()) ?: false
                val imeBottom = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: -1
                val statusInsets = insets?.getInsets(WindowInsetsCompat.Type.statusBars())
                val navInsets = insets?.getInsets(WindowInsetsCompat.Type.navigationBars())
                val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: false
                val navVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: false
                val rootHeight = decor.height
                val visibleHeight = visibleFrame.height()
                val estimatedKeyboardHeight = (rootHeight - visibleHeight).coerceAtLeast(0)
                val state = "owner=$owner decor=${decor.width}x$rootHeight visibleFrame=${visibleFrame.left},${visibleFrame.top},${visibleFrame.right},${visibleFrame.bottom}" +
                    " visibleHeight=$visibleHeight estimatedImeHeight=$estimatedKeyboardHeight" +
                    " imeVisible=$imeVisible imeBottom=$imeBottom" +
                    " statusVisible=$statusVisible statusInsetsBottom=${statusInsets?.bottom ?: -1}" +
                    " navVisible=$navVisible navInsetsBottom=${navInsets?.bottom ?: -1}" +
                    " decorSystemUi=0x${decor.systemUiVisibility.toString(16)}" +
                    " fitsSystemWindows=${ViewCompat.getFitsSystemWindows(decor)}"
                if (state != lastLayoutState) {
                    lastLayoutState = state
                    AppDiagnostics.log("DEBUG", "UI_LAYOUT_INSETS", state)
                }
            }
        } else {
            AppDiagnostics.log("WARN", "UI_DIAGNOSTICS", "ViewTreeObserver unavailable owner=$owner")
        }

        decor.addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                AppDiagnostics.log(
                    "DEBUG",
                    "UI_DECOR_LAYOUT",
                    "owner=$owner new=${right - left}x${bottom - top} old=${oldRight - oldLeft}x${oldBottom - oldTop}"
                )
            }
        }
    }

    fun logLifecycle(activity: Activity, event: String) {
        AppDiagnostics.log(
            "INFO",
            "ACTIVITY_LIFECYCLE",
            "activity=${activity.javaClass.simpleName} event=$event finishing=${activity.isFinishing} changingConfig=${activity.isChangingConfigurations}"
        )
    }

    fun logWindowFocus(activity: Activity, hasFocus: Boolean) {
        val window = activity.window
        val decor = window.decorView
        AppDiagnostics.log(
            "INFO",
            "UI_WINDOW_FOCUS",
            "activity=${activity.javaClass.simpleName} hasFocus=$hasFocus decorAttached=${decor.isAttachedToWindow} " +
                "decorFocused=${decor.hasWindowFocus()} systemUi=0x${decor.systemUiVisibility.toString(16)} " +
                "softInputMode=0x${window.attributes.softInputMode.toString(16)}"
        )
    }
}
