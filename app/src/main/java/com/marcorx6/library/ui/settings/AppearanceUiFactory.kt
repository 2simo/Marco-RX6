package com.marcorx6.library.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.view.WindowManager
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Context-bound appearance and formatting helpers extracted from MainActivity.
 *
 * Values that depend on the current Activity theme are supplied lazily so
 * existing appearance preferences and runtime theme changes keep their behavior.
 */
internal class AppearanceUiFactory(
    private val activity: Activity,
    private val dp: (Int) -> Int,
    private val isDarkMode: () -> Boolean,
    private val bg: () -> Int,
    private val panel: () -> Int,
    private val red: () -> Int,
    private val white: () -> Int,
    private val gray: () -> Int,
    private val accentForeground: () -> Int
) {

    fun stylePrimaryButton(button: Button, primary: Boolean = false) {
        button.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            if (primary) intArrayOf(Color.rgb(42, 145, 255), Color.rgb(24, 91, 205))
            else if (isDarkMode()) intArrayOf(Color.rgb(24, 48, 76), Color.rgb(13, 29, 49))
            else intArrayOf(Color.rgb(250, 251, 255), Color.rgb(232, 237, 247))).apply {
            cornerRadius = dp(14).toFloat()
            if (!primary) setStroke(dp(1), if (isDarkMode()) Color.rgb(55, 83, 113) else Color.rgb(211, 219, 232))
        }
        button.setTextColor(if (primary) accentForeground() else white())
        button.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        button.textSize = 13.5f
        button.isAllCaps = false
        button.letterSpacing = 0.01f
        button.minHeight = dp(48)
        button.minWidth = dp(48)
        button.setPadding(dp(16), dp(10), dp(16), dp(10))
        button.stateListAnimator = null
    }

    fun applyUnifiedSkin(view: View) {
        when (view) {
            is EditText -> {
                view.background = GradientDrawable().apply {
                    setColor(if (isDarkMode()) Color.rgb(25, 30, 47) else Color.rgb(248, 250, 254))
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(1), if (isDarkMode()) Color.rgb(55, 83, 113) else Color.rgb(211, 219, 232))
                }
                view.setPadding(dp(14), dp(12), dp(14), dp(12))
                view.minHeight = dp(52)
                view.textSize = 14f
                view.setTextColor(white())
                view.setHintTextColor(gray())
            }
            // CompoundButton subclasses Button, so handle them first to avoid
            // applying the generic button background to checkboxes and radio buttons.
            is CheckBox -> {
                view.setTextColor(white())
                view.buttonTintList = android.content.res.ColorStateList.valueOf(red())
                view.minHeight = dp(48)
            }
            is RadioButton -> {
                view.setTextColor(white())
                view.buttonTintList = android.content.res.ColorStateList.valueOf(red())
                view.minHeight = dp(48)
            }
            is Button -> stylePrimaryButton(view, view.tag == "marco_primary_action")
            is SeekBar -> {
                view.progressTintList = android.content.res.ColorStateList.valueOf(red())
                view.thumbTintList = android.content.res.ColorStateList.valueOf(red())
            }
            is Spinner -> {
                view.backgroundTintList = android.content.res.ColorStateList.valueOf(gray())
                view.minimumHeight = dp(48)
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) applyUnifiedSkin(view.getChildAt(i))
        }
    }

    fun styleAlertDialog(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawable(GradientDrawable(GradientDrawable.Orientation.TL_BR,
            if (isDarkMode()) intArrayOf(Color.rgb(17, 48, 80), Color.rgb(7, 21, 38))
            else intArrayOf(Color.WHITE, Color.rgb(239, 243, 251))).apply {
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), if (isDarkMode()) Color.rgb(47, 117, 188) else Color.rgb(211, 219, 234))
        })
        dialog.window?.let { window ->
            // Alert dialogs use their own window: keep system bars visible and let
            // Android place the dialog inside the safe area on edge-to-edge devices.
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            window.statusBarColor = bg()
            window.navigationBarColor = bg()
            WindowInsetsControllerCompat(window, window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes = window.attributes.apply { dimAmount = 0.66f }
        }
        dialog.findViewById<TextView>(activity.resources.getIdentifier("alertTitle", "id", "android"))?.apply {
            setTextColor(white())
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        dialog.findViewById<View>(android.R.id.message)?.let {
            (it as? TextView)?.apply {
                setTextColor(gray())
                textSize = 14f
                setLineSpacing(dp(3).toFloat(), 1f)
            }
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { stylePrimaryButton(it, true) }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { stylePrimaryButton(it) }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.let { stylePrimaryButton(it) }
        dialog.listView?.let { list ->
            list.setBackgroundColor(if (isDarkMode()) Color.rgb(12, 31, 52) else Color.WHITE)
            list.divider = android.graphics.drawable.ColorDrawable(if (isDarkMode()) Color.rgb(39, 68, 99) else Color.rgb(226, 231, 241))
            list.dividerHeight = dp(1)
            list.post {
                for (index in 0 until list.childCount) {
                    val row = list.getChildAt(index)
                    if (row is ViewGroup) {
                        for (childIndex in 0 until row.childCount) {
                            (row.getChildAt(childIndex) as? TextView)?.setTextColor(white())
                        }
                    } else {
                        (row as? TextView)?.setTextColor(white())
                    }
                }
            }
        }
        dialog.window?.decorView?.let { applyUnifiedSkin(it) }
    }

    fun themedSpinner(items: List<String>, selectedIndex: Int = 0): Spinner {
        val spinner = Spinner(activity)
        val adapter = object : ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(white())
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(white())
                    setBackgroundColor(panel())
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                }
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
        spinner.backgroundTintList = android.content.res.ColorStateList.valueOf(gray())
        return spinner
    }

    fun makeInput(hintText: String): EditText =
        EditText(activity).apply {
            hint = hintText
            textSize = 14f
            minHeight = dp(52)
            setTextColor(white())
            setHintTextColor(gray())
            background = GradientDrawable().apply {
                setColor(panel())
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(13), dp(10), dp(13), dp(10))
        }
}
