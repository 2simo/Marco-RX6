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
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                if (selected) intArrayOf(
                    android.graphics.Color.argb(232, 31, 112, 190),
                    android.graphics.Color.argb(215, 10, 48, 91)
                ) else intArrayOf(
                    android.graphics.Color.argb(205, 20, 57, 91),
                    android.graphics.Color.argb(188, 8, 27, 48)
                )
            ).apply {
                cornerRadius = dp(17).toFloat()
                setStroke(
                    dp(if (selected) 2 else 1),
                    if (selected) android.graphics.Color.argb(220, 103, 186, 255)
                    else android.graphics.Color.argb(145, 77, 132, 186)
                )
            }
            elevation = dp(if (selected) 4 else 1).toFloat()
            val optionHeader = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            optionHeader.addView(makeText(
                label,
                16f,
                if (selected) android.graphics.Color.WHITE else android.graphics.Color.rgb(222, 232, 245),
                true
            ), LinearLayout.LayoutParams(0, -2, 1f))
            if (selected) {
                optionHeader.addView(android.widget.ImageView(activity).apply {
                    setImageResource(com.marcorx6.library.R.drawable.ic_action_check)
                    imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
                    contentDescription = "Selected"
                    scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                }, LinearLayout.LayoutParams(dp(22), dp(22)))
            }
            addView(optionHeader)
            if (detail.isNotBlank()) {
                addView(makeText(detail, 10f, android.graphics.Color.rgb(153, 174, 199), false).apply {
                    setPadding(0, dp(5), 0, 0)
                })
            }
            isClickable = true
            isFocusable = true
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                foreground = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.argb(82, 76, 169, 255)),
                    null,
                    android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)
                )
            }
        }
    }

    fun animateIn(view: View) {
        view.alpha = 0f
        view.translationY = dp(18).toFloat()
        view.animate().alpha(1f).translationY(0f).setDuration(360L)
            .setInterpolator(DecelerateInterpolator()).start()
    }
}
