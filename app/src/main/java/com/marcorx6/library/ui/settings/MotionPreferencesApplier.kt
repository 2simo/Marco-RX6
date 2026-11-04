package com.marcorx6.library.ui

import android.animation.LayoutTransition
import android.view.View
import android.view.ViewGroup
import android.content.SharedPreferences

/**
 * Applies the app's existing motion preferences to a view tree.
 * Preference values are read when needed so toggling animations takes effect
 * without rebuilding this helper.
 */
internal class MotionPreferencesApplier(
    private val preferences: () -> SharedPreferences
) {
    fun apply(view: View) {
        val animationsEnabled = preferences().getBoolean("animations", true)
        if (view is ViewGroup) {
            view.layoutTransition = if (animationsEnabled) LayoutTransition().apply { setDuration(150) } else null
            for (i in 0 until view.childCount) apply(view.getChildAt(i))
        }
        // Avoid replacing screen-specific touch listeners. Individual controls own
        // their ripple feedback; this helper only coordinates container transitions.
    }
}
