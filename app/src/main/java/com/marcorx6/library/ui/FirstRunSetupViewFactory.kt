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
    fun optionCard(
        label: String,
        selected: Boolean,
        detail: String = "",
        dark: Boolean = true,
        accentColor: Int = android.graphics.Color.rgb(190, 198, 210),
        accentForeground: Int = android.graphics.Color.rgb(20, 23, 29)
    ): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                if (selected) intArrayOf(
                    accentColor,
                    android.graphics.Color.rgb(
                        (android.graphics.Color.red(accentColor) * 0.72f).toInt(),
                        (android.graphics.Color.green(accentColor) * 0.72f).toInt(),
                        (android.graphics.Color.blue(accentColor) * 0.72f).toInt()
                    )
                ) else if (dark) intArrayOf(
                    android.graphics.Color.argb(218, 34, 37, 44),
                    android.graphics.Color.argb(205, 17, 19, 24)
                ) else intArrayOf(
                    android.graphics.Color.WHITE,
                    android.graphics.Color.rgb(239, 242, 247)
                )
            ).apply {
                cornerRadius = dp(17).toFloat()
                setStroke(
                    dp(if (selected) 2 else 1),
                    if (selected) accentColor
                    else android.graphics.Color.argb(125, 130, 138, 151)
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
                if (selected) accentForeground else android.graphics.Color.rgb(230, 233, 239),
                true
            ), LinearLayout.LayoutParams(0, -2, 1f))
            if (selected) {
                optionHeader.addView(android.widget.ImageView(activity).apply {
                    setImageResource(com.marcorx6.library.R.drawable.ic_action_check)
                    imageTintList = android.content.res.ColorStateList.valueOf(accentForeground)
                    contentDescription = "Selected"
                    scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                }, LinearLayout.LayoutParams(dp(22), dp(22)))
            }
            addView(optionHeader)
            if (detail.isNotBlank()) {
                addView(makeText(detail, 10f, if (dark) android.graphics.Color.rgb(166, 173, 184) else android.graphics.Color.rgb(91, 98, 110), false).apply {
                    setPadding(0, dp(5), 0, 0)
                })
            }
            isClickable = true
            isFocusable = true
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                foreground = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.argb(70, android.graphics.Color.red(accentColor), android.graphics.Color.green(accentColor), android.graphics.Color.blue(accentColor))),
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
