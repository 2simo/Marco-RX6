package com.marcorx6.library.ui

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

        // Implicit LayoutTransition animates every child insertion/removal. Large
        // library renders and download refreshes can therefore trigger overlapping
        // layout animations that look like flicker and waste UI-thread work.
        // Screens keep their deliberate, short entrance/step animations separately.
        if (view is ViewGroup) {
            view.layoutTransition = null
            for (i in 0 until view.childCount) apply(view.getChildAt(i))
        }

        // When the user disables motion, stop any in-flight property animation.
        if (!animationsEnabled) {
            view.animate().cancel()
            view.clearAnimation()
        }
    }
}
