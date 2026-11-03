package com.marcorx6.library.ui.appearance

import com.marcorx6.library.ui.*

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

/**
 * Resolves the current appearance preferences and shared palette colors.
 */
internal class AppearancePaletteRuntimeFeature(context: Context) {
    private val appearancePreferences = AppearancePreferencesStore.appearance(context)

    fun preferences(): SharedPreferences = appearancePreferences
    fun isDarkMode(): Boolean = AppearancePreferencesStore.isDarkMode(appearancePreferences)
    fun accentColor(): Int =
        AccentPalette.color(appearancePreferences.getString("accent", "blue"))

    val bg: Int
        get() = if (isDarkMode()) Color.rgb(7, 13, 25) else Color.rgb(245, 247, 251)
    val panel: Int
        get() = if (isDarkMode()) Color.rgb(16, 27, 45) else Color.rgb(229, 234, 243)
    val red: Int
        get() = accentColor()
    val white: Int
        get() = if (isDarkMode()) Color.WHITE else Color.rgb(24, 29, 42)
    val gray: Int
        get() = if (isDarkMode()) Color.LTGRAY else Color.rgb(86, 96, 115)

    fun accentForeground(): Int = AccentPalette.foreground()
}
