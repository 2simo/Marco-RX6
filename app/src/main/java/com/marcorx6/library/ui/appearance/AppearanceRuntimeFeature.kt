package com.marcorx6.library.ui.appearance

import com.marcorx6.library.ui.*

import android.app.AlertDialog
import android.content.SharedPreferences
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.activity.ComponentActivity

/**
 * Owns appearance, typography, language and motion feature composition.
 * The Activity supplies application actions through callbacks; the feature owns UI services.
 */
internal class AppearanceRuntimeFeature(
    private val activity: ComponentActivity,
    private val appearancePreferences: () -> SharedPreferences,
    private val isDarkMode: () -> Boolean,
    private val bg: () -> Int,
    private val panel: () -> Int,
    private val red: () -> Int,
    private val white: () -> Int,
    private val gray: () -> Int,
    private val accentForeground: () -> Int,
    private val showNotifications: () -> Unit,
    private val recreateActivity: () -> Unit,
    private val recreateInterface: () -> Unit,
    private val setContentView: (View) -> Unit,
    private val onBack: () -> Unit
) {
    private val uiToolkitFeature by lazy {
        UiToolkitFeature(
            activity = activity,
            isDarkMode = isDarkMode,
            bg = bg,
            panel = panel,
            red = red,
            white = white,
            gray = gray,
            accentForeground = accentForeground
        )
    }

    private val languageFeature by lazy {
        LanguageFeature(
            activity = activity,
            preferences = activity.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE),
            styleDialog = ::styleAlertDialog,
            onLanguageChanged = recreateInterface
        )
    }

    private val motionPreferencesApplier by lazy {
        MotionPreferencesApplier(appearancePreferences)
    }

    private val appearanceFeature by lazy {
        AppearanceFeature(
            activity = activity,
            appearancePreferences = appearancePreferences,
            dp = ::dp,
            makeText = ::makeText,
            panelColor = panel,
            redColor = red,
            whiteColor = white,
            grayColor = gray,
            roundedBackground = ::rounded,
            themedSpinner = ::themedSpinner,
            applyMotionPreferences = ::applyMotionPreferences,
            styleDialog = ::styleAlertDialog,
            recreateActivity = recreateActivity,
            recreateInterface = recreateInterface,
            showLanguagePicker = ::showLanguagePicker,
            showNotificationSettings = showNotifications,
            setContentView = setContentView,
            onBack = onBack
        )
    }

    fun dp(value: Int): Int = uiToolkitFeature.dp(value)
    fun rounded(color: Int, radius: Int) = uiToolkitFeature.rounded(color, radius)
    fun makeText(value: String, size: Float, color: Int, bold: Boolean = false) =
        uiToolkitFeature.makeText(value, size, color, bold)
    fun stylePrimaryButton(button: Button, primary: Boolean = false) =
        uiToolkitFeature.stylePrimaryButton(button, primary)
    fun applyUnifiedSkin(view: View) = uiToolkitFeature.applyUnifiedSkin(view)
    fun styleAlertDialog(dialog: AlertDialog) = uiToolkitFeature.styleAlertDialog(dialog)
    fun themedSpinner(items: List<String>, selectedIndex: Int = 0): Spinner =
        uiToolkitFeature.themedSpinner(items, selectedIndex)
    fun makeInput(hintText: String): EditText = uiToolkitFeature.makeInput(hintText)

    fun currentLanguage(): String = languageFeature.currentLanguage()
    fun translate(value: String): String = languageFeature.translate(value)
    fun showLanguagePicker() = languageFeature.showPicker()
    fun applyMotionPreferences(view: View) = motionPreferencesApplier.apply(view)
    fun applyTypography(view: View, scaledDensity: Float) =
        appearanceFeature.applyTypography(view, scaledDensity)
    fun fontScale(): Float = appearanceFeature.fontScale()
    fun fontFamilyName(): String = appearanceFeature.fontFamilyName()
    fun showAppearanceSettings() = appearanceFeature.showAppearanceSettings()
    fun showTypographySettings() = appearanceFeature.showTypographySettings()
}
