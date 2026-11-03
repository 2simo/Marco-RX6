package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import android.view.animation.DecelerateInterpolator

internal class FirstRunSetupViewFactory(
    private val activity: Activity,
    private val dp: (Int) -> Int,
    private val makeText: (String, Float, Int, Boolean) -> android.widget.TextView
) {
    fun optionCard(label: String, selected: Boolean, detail: String = ""): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(17).toFloat()
                setColor(if (selected) android.graphics.Color.rgb(12, 48, 83) else android.graphics.Color.rgb(14, 29, 48))
                setStroke(
                    dp(if (selected) 2 else 1),
                    if (selected) android.graphics.Color.rgb(42, 145, 255) else android.graphics.Color.rgb(54, 76, 103)
                )
            }
            addView(makeText(
                if (selected) "✓   $label" else label,
                16f,
                if (selected) android.graphics.Color.WHITE else android.graphics.Color.rgb(222, 232, 245),
                true
            ))
            if (detail.isNotBlank()) {
                addView(makeText(detail, 10f, android.graphics.Color.rgb(153, 174, 199), false).apply {
                    setPadding(0, dp(5), 0, 0)
                })
            }
            isClickable = true
            isFocusable = true
        }
    }

    fun animateIn(view: View) {
        view.alpha = 0f
        view.translationY = dp(18).toFloat()
        view.animate().alpha(1f).translationY(0f).setDuration(360L)
            .setInterpolator(DecelerateInterpolator()).start()
    }
}
