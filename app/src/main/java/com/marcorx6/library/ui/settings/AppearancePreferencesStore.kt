package com.marcorx6.library.ui

import android.content.Context
import android.content.SharedPreferences

/** Provides the existing named preference stores used by appearance settings. */
internal object AppearancePreferencesStore {
    fun appearance(context: Context): SharedPreferences =
        context.getSharedPreferences("appearance_settings", Context.MODE_PRIVATE)

    fun typography(context: Context): SharedPreferences =
        context.getSharedPreferences("font_settings", Context.MODE_PRIVATE)

    fun isDarkMode(preferences: SharedPreferences): Boolean =
        preferences.getBoolean("dark_mode", true)
}
