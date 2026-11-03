package com.marcorx6.library.ui

import android.app.AlertDialog
import android.content.SharedPreferences
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.activity.ComponentActivity

/**
 * Owns appearance, typography, language, motion and side-menu feature composition.
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
    private val isSyncing: () -> Boolean,
    private val isAdminSession: () -> Boolean,
    private val sync: () -> Unit,
    private val showDownloads: () -> Unit,
    private val showNotifications: () -> Unit,
    private val showProfile: () -> Unit,
    private val signOut: () -> Unit,
    private val signIn: () -> Unit,
    private val showGameDialog: () -> Unit,
    private val showGameOfDay: () -> Unit,
    private val openLink: (String) -> Unit,
    private val applyUnifiedSkin: (View) -> Unit,
    private val recreateActivity: () -> Unit,
    private val recreateInterface: () -> Unit
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

    private val sideMenuFeature by lazy {
        SideMenuFeature(
            activity = activity,
            dp = ::dp,
            makeText = ::makeText,
            translate = ::translate,
            isDark = isDarkMode,
            language = ::currentLanguage,
            gold = red(),
            ink = bg(),
            surface = panel(),
            white = white(),
            gray = gray(),
            syncing = isSyncing,
            isAdmin = isAdminSession,
            sync = sync,
            showDownloads = showDownloads,
            showNotifications = showNotifications,
            showProfile = showProfile,
            signOut = signOut,
            signIn = signIn,
            showGameDialog = showGameDialog,
            showGameOfDay = showGameOfDay,
            showLanguage = ::showLanguagePicker,
            showAppearance = ::showAppearanceSettings,
            showTypography = ::showTypographySettings,
            openLink = openLink,
            applyMotion = ::applyMotionPreferences,
            applySkin = applyUnifiedSkin
        )
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
            recreateInterface = recreateInterface
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
    fun showSideMenu() = sideMenuFeature.show()
    fun applyMotionPreferences(view: View) = motionPreferencesApplier.apply(view)
    fun applyTypography(view: View, scaledDensity: Float) =
        appearanceFeature.applyTypography(view, scaledDensity)
    fun fontScale(): Float = appearanceFeature.fontScale()
    fun fontFamilyName(): String = appearanceFeature.fontFamilyName()
    fun showAppearanceSettings() = appearanceFeature.showAppearanceSettings()
    fun showTypographySettings() = appearanceFeature.showTypographySettings()
}
