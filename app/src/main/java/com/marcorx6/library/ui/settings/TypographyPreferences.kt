package com.marcorx6.library.ui

import android.content.SharedPreferences

/** Pure preference decoding for font scale and family. */
internal object TypographyPreferences {
    fun scale(preferences: SharedPreferences): Float =
        preferences.getFloat("scale", 1.0f).coerceIn(0.85f, 1.25f)

    fun family(preferences: SharedPreferences): String =
        when (preferences.getString("family", "sans")) {
            "serif" -> "serif"
            "mono" -> "monospace"
            else -> "sans-serif"
        }
}
