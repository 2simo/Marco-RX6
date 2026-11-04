package com.marcorx6.library.ui

import android.app.Activity
import android.content.SharedPreferences
import android.view.View
import android.widget.TextView
import android.widget.Spinner
import android.graphics.drawable.GradientDrawable

/** Groups appearance dialogs and typography preferences under one feature boundary. */
internal class AppearanceFeature(
    private val activity: Activity,
    private val appearancePreferences: () -> SharedPreferences,
    private val dp: (Int) -> Int,
    private val makeText: (String, Float, Int, Boolean) -> TextView,
    private val panelColor: () -> Int,
    private val redColor: () -> Int,
    private val whiteColor: () -> Int,
    private val grayColor: () -> Int,
    private val roundedBackground: (Int, Int) -> GradientDrawable,
    private val themedSpinner: (List<String>, Int) -> Spinner,
    private val applyMotionPreferences: (View) -> Unit,
    private val styleDialog: (android.app.AlertDialog) -> Unit,
    private val recreateActivity: () -> Unit,
    private val recreateInterface: () -> Unit,
    private val showLanguagePicker: () -> Unit,
    private val showNotificationSettings: () -> Unit,
    private val setContentView: (View) -> Unit,
    private val onBack: () -> Unit,
    private val translate: (String) -> String
) {
    private fun typographyPreferences() = AppearancePreferencesStore.typography(activity)
    fun fontScale(): Float = TypographyPreferences.scale(typographyPreferences())
    fun fontFamilyName(): String = TypographyPreferences.family(typographyPreferences())

    fun applyTypography(view: View, scaledDensity: Float) {
        TypographyApplier.apply(
            view = view,
            fontFamily = fontFamilyName(),
            fontScale = fontScale(),
            scaledDensity = scaledDensity
        )
    }

    fun showAppearanceSettings() {
        AppearanceSettingsController(
            activity = activity,
            preferences = appearancePreferences(),
            applyMotionPreferences = applyMotionPreferences,
            recreateActivity = recreateActivity,
            showLanguagePicker = showLanguagePicker,
            showNotificationSettings = showNotificationSettings,
            showTypographySettings = ::showTypographySettings,
            setContentView = setContentView,
            onBack = onBack,
            translate = translate
        ).show()
    }

    fun showTypographySettings() {
        TypographySettingsController(
            activity = activity,
            preferences = typographyPreferences(),
            dp = dp,
            makeText = makeText,
            panelColor = panelColor,
            redColor = redColor,
            whiteColor = whiteColor,
            grayColor = grayColor,
            roundedBackground = roundedBackground,
            themedSpinner = themedSpinner,
            recreateInterface = recreateInterface,
            styleDialog = styleDialog
        ).show()
    }
}
