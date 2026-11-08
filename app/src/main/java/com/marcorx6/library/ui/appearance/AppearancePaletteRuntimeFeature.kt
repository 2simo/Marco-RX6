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
        AccentPalette.color()

    val bg: Int
        get() = if (isDarkMode()) Color.rgb(8, 9, 12) else Color.rgb(242, 244, 247)
    val panel: Int
        get() = if (isDarkMode()) Color.rgb(25, 28, 34) else Color.rgb(222, 226, 232)
    val red: Int
        get() = accentColor()
    val white: Int
        get() = if (isDarkMode()) Color.rgb(245, 247, 250) else Color.rgb(25, 29, 36)
    val gray: Int
        get() = if (isDarkMode()) Color.rgb(178, 184, 194) else Color.rgb(91, 98, 110)

    fun accentForeground(): Int = AccentPalette.foreground()
}
