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
                if (isDarkMode()) intArrayOf(Color.rgb(19, 49, 83), Color.rgb(8, 20, 38))
                else intArrayOf(Color.WHITE, Color.rgb(235, 242, 251))
            )
        }
        val close = TextView(activity).apply {
            text = "‹"
            textSize = 30f
            setTextColor(white())
            gravity = android.view.Gravity.CENTER
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
        topBar.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        topBar.addView(favoriteToggleFactory.create(game))
        return topBar
    }
}
