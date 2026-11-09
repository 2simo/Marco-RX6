package com.marcorx6.library.ui

import android.content.Context
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.util.WeakHashMap

/** Lifecycle-safe, one-shot reveal for cards and section rows. */
internal object ScrollRevealAnimator {
    private val scheduled = WeakHashMap<View, Boolean>()

    fun reveal(view: View) {
        val enabled = view.context
            .getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getBoolean("animations", true)
        if (!enabled) {
            normalize(view)
            return
        }
        synchronized(scheduled) {
            if (scheduled.put(view, true) != null) return
        }

        val distance = 14f * view.resources.displayMetrics.density
        val startAnimation = {
            if (view.isAttachedToWindow) {
                view.animate().cancel()
                view.alpha = 0f
                view.translationY = distance
                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(280L)
                    .setInterpolator(DecelerateInterpolator(1.35f))
                    .withEndAction { normalize(view) }
                    .start()
            } else {
                normalize(view)
            }
        }

        if (view.isAttachedToWindow) {
            view.postOnAnimation { startAnimation() }
        } else {
            val attachListener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(attached: View) {
                    attached.removeOnAttachStateChangeListener(this)
                    attached.postOnAnimation { startAnimation() }
                }

                override fun onViewDetachedFromWindow(detached: View) {
                    detached.removeOnAttachStateChangeListener(this)
                    detached.animate().cancel()
                    normalize(detached)
                }
            }
            view.addOnAttachStateChangeListener(attachListener)
        }
    }

    private fun normalize(view: View) {
        view.alpha = 1f
        view.translationX = 0f
        view.translationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
    }
}