package com.marcorx6.library.ui

import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Lightweight one-shot reveal for content cards as they enter a scrolling screen.
 * The caller invokes this when a lazy card is materialized or a section item is added.
 */
internal object ScrollRevealAnimator {
    fun reveal(view: View) {
        val enabled = view.context
            .getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
            .getBoolean("animations", true)
        if (!enabled) return

        val distance = 16f * view.resources.displayMetrics.density
        view.alpha = 0f
        view.translationY = distance
        view.post {
            if (!view.isAttachedToWindow) return@post
            view.animate().cancel()
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(320L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }
}
