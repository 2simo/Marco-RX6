package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.TextView
import com.marcorx6.library.data.Game

internal class GameDetailsTopBarFactory(
    private val activity: Activity,
    private val dp: (Int) -> Int,
    private val makeText: (String, Float, Int, Boolean) -> TextView,
    private val isDarkMode: () -> Boolean,
    private val white: () -> Int,
    private val red: () -> Int
) {
    fun create(game: Game, onClose: () -> Unit): LinearLayout {
        val topBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutDirection = android.view.View.LAYOUT_DIRECTION_LTR
            setPadding(0, 0, 0, 0)
            background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        }
        val close = android.widget.ImageView(activity).apply {
            setImageResource(com.marcorx6.library.R.drawable.ic_action_back)
            contentDescription = "إغلاق تفاصيل اللعبة"
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            imageTintList = android.content.res.ColorStateList.valueOf(white())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(90, 190, 198, 210)),
                null,
                android.graphics.drawable.ColorDrawable(Color.WHITE)
            )
            setOnClickListener { onClose() }
            minimumWidth = dp(48)
            minimumHeight = dp(48)
        }
        topBar.addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
        return topBar
    }
}
