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
    private val red: () -> Int,
    private val favoriteToggleFactory: GameFavoriteToggleFactory
) {
    fun create(game: Game, onClose: () -> Unit): LinearLayout {
        val topBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutDirection = android.view.View.LAYOUT_DIRECTION_LTR
            setPadding(dp(16), dp(10), dp(12), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                if (isDarkMode()) intArrayOf(Color.argb(222, 35, 38, 45), Color.argb(204, 8, 9, 12))
                else intArrayOf(Color.argb(228, 255, 255, 255), Color.argb(210, 222, 226, 232))
            ).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), if (isDarkMode()) Color.argb(150, 130, 138, 151) else Color.argb(160, 160, 168, 181))
            }
        }
        val close = android.widget.ImageView(activity).apply {
            setImageResource(com.marcorx6.library.R.drawable.ic_action_back)
            contentDescription = "إغلاق تفاصيل اللعبة"
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            imageTintList = android.content.res.ColorStateList.valueOf(white())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(45, 190, 198, 210))
                cornerRadius = dp(14).toFloat()
            }
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
        // Keep navigation on the left and move the favorite action to the
        // right side of the upper toolbar, as requested.
        topBar.addView(android.view.View(activity), LinearLayout.LayoutParams(0, dp(48), 1f))
        topBar.addView(favoriteToggleFactory.create(game), LinearLayout.LayoutParams(dp(48), dp(48)))
        return topBar
    }
}
