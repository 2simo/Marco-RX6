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
            setPadding(dp(16), dp(10), dp(12), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                if (isDarkMode()) intArrayOf(Color.argb(222, 24, 76, 124), Color.argb(204, 7, 24, 43))
                else intArrayOf(Color.argb(228, 255, 255, 255), Color.argb(210, 218, 237, 255))
            ).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), if (isDarkMode()) Color.argb(150, 86, 165, 232) else Color.argb(160, 139, 190, 238))
            }
        }
        val close = TextView(activity).apply {
            text = "‹"
            contentDescription = "إغلاق تفاصيل اللعبة"
            textSize = 30f
            setTextColor(white())
            gravity = android.view.Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(45, 92, 177, 255))
                cornerRadius = dp(14).toFloat()
            }
            if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(90, 105, 190, 255)),
                null,
                android.graphics.drawable.ColorDrawable(Color.WHITE)
            )
            setOnClickListener { onClose() }
            minWidth = dp(48)
            minHeight = dp(48)
        }
        topBar.addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
        val heading = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(makeText(game.name, 16f, white(), true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        heading.addView(makeText(game.platform, 10f, android.graphics.Color.rgb(91, 177, 255), true))
        heading.contentDescription = "${game.name}, ${game.platform}"
        topBar.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        topBar.addView(favoriteToggleFactory.create(game))
        return topBar
    }
}
