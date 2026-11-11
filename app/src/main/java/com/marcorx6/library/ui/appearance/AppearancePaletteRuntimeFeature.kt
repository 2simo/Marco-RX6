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

    private fun selectedAccentId(): String =
        appearancePreferences.getString(AccentPalette.PREFERENCE_KEY, AccentPalette.DEFAULT_ID)
            ?: AccentPalette.DEFAULT_ID

    fun accentColor(): Int = AccentPalette.color(selectedAccentId())

    // Both appearance modes retain MARCO's restrained surfaces, but each surface
    // receives a visible tint from the selected primary color. These are computed
    // getters so a palette change is reflected as soon as the screen is rebuilt.
    val bg: Int
        get() {
            val accent = accentColor()
            return if (isDarkMode()) AccentPalette.surface(Color.rgb(8, 9, 12), accent, 0.20f)
            else AccentPalette.surface(Color.rgb(245, 247, 250), accent, 0.10f)
        }
    val panel: Int
        get() {
            val accent = accentColor()
            return if (isDarkMode()) AccentPalette.surface(Color.rgb(25, 28, 34), accent, 0.30f)
            else AccentPalette.surface(Color.rgb(224, 229, 237), accent, 0.20f)
        }
    val red: Int
        get() = accentColor()
    val white: Int
        get() = if (isDarkMode()) Color.rgb(245, 247, 250) else Color.rgb(25, 29, 36)
    val gray: Int
        get() = if (isDarkMode()) Color.rgb(178, 184, 194) else Color.rgb(91, 98, 110)

    fun accentForeground(): Int = AccentPalette.foreground(selectedAccentId())
}
