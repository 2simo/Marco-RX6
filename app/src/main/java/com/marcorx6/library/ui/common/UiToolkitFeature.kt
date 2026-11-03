package com.marcorx6.library.ui

import androidx.activity.ComponentActivity
import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView

/** Centralizes generic view creation and appearance styling shared by feature screens. */
internal class UiToolkitFeature(
    activity: ComponentActivity,
    isDarkMode: () -> Boolean,
    bg: () -> Int,
    panel: () -> Int,
    red: () -> Int,
    white: () -> Int,
    gray: () -> Int,
    accentForeground: () -> Int
) {
    private val basic = BasicUiFactory(activity)
    private val appearance = AppearanceUiFactory(
        activity = activity,
        dp = basic::dp,
        isDarkMode = isDarkMode,
        bg = bg,
        panel = panel,
        red = red,
        white = white,
        gray = gray,
        accentForeground = accentForeground
    )

    fun dp(value: Int): Int = basic.dp(value)
    fun rounded(color: Int, radius: Int): GradientDrawable = basic.rounded(color, radius)
    fun makeText(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        basic.makeText(value, size, color, bold)

    fun stylePrimaryButton(button: Button, primary: Boolean = false) =
        appearance.stylePrimaryButton(button, primary)

    fun applyUnifiedSkin(view: View) = appearance.applyUnifiedSkin(view)
    fun styleAlertDialog(dialog: AlertDialog) = appearance.styleAlertDialog(dialog)
    fun themedSpinner(items: List<String>, selectedIndex: Int = 0): Spinner =
        appearance.themedSpinner(items, selectedIndex)
    fun makeInput(hintText: String): EditText = appearance.makeInput(hintText)
}
