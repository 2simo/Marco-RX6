package com.marcorx6.library.ui

import android.app.Activity
import android.content.SharedPreferences
import android.view.View
import android.widget.TextView
import android.widget.Spinner
import android.graphics.drawable.GradientDrawable

/** Groups appearance dialogs and typography preferences under one feature boundary. */
internal class AppearanceFeature(
    activity: Activity,
    appearancePreferences: () -> SharedPreferences,
    dp: (Int) -> Int,
    makeText: (String, Float, Int, Boolean) -> TextView,
    panelColor: () -> Int,
    redColor: () -> Int,
    whiteColor: () -> Int,
    grayColor: () -> Int,
    roundedBackground: (Int, Int) -> GradientDrawable,
    themedSpinner: (List<String>, Int) -> Spinner,
    applyMotionPreferences: (View) -> Unit,
    styleDialog: (android.app.AlertDialog) -> Unit,
    recreateActivity: () -> Unit,
    recreateInterface: () -> Unit
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
            styleDialog = styleDialog,
            recreateActivity = recreateActivity
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
