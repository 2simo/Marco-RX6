package com.marcorx6.library.ui

import android.animation.LayoutTransition
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.animation.OvershootInterpolator
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
        if (view.isClickable) {
            // Do not overwrite semantic view tags such as marco_primary_action.
            // Reapplying the same touch listener is safe and keeps styling idempotent.
            view.setOnTouchListener { target, event ->
                if (!preferences().getBoolean("animations", true)) return@setOnTouchListener false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        target.animate().cancel()
                        target.animate().scaleX(0.96f).scaleY(0.96f).alpha(0.88f).setDuration(90L).start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> target.animate()
                        .scaleX(1f).scaleY(1f).alpha(1f).setDuration(170L)
                        .setInterpolator(OvershootInterpolator(1.35f)).start()
                }
                false
            }
        }
    }
}
