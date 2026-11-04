package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.marcorx6.library.data.Game

internal class GameDetailsIdentityViewFactory(
    private val activity: Activity,
    private val dp: (Int) -> Int,
    private val rounded: (Int, Int) -> Drawable,
    private val makeText: (String, Float, Int, Boolean) -> TextView,
    private val isDarkMode: () -> Boolean,
    private val white: () -> Int,
    private val gray: () -> Int,
    private val loadCover: (ImageView, String, String) -> Unit,
    private val watchlistViewFactory: GameWatchlistViewFactory
) {
    fun create(game: Game): LinearLayout {
        val dark = isDarkMode()
        val identity = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(13), dp(13), dp(13))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                if (dark) intArrayOf(Color.argb(229, 29, 82, 132), Color.argb(216, 13, 36, 61), Color.argb(208, 7, 20, 36))
                else intArrayOf(Color.argb(232, 255, 255, 255), Color.argb(218, 226, 241, 255), Color.argb(220, 235, 245, 255))
            ).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), if (dark) Color.argb(180, 86, 164, 229) else Color.argb(185, 148, 195, 238))
            }
            elevation = dp(3).toFloat()
        }
        val cover = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                setColor(if (dark) Color.rgb(13, 34, 57) else Color.rgb(225, 240, 255))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), if (dark) Color.argb(150, 85, 158, 222) else Color.argb(155, 145, 193, 237))
            }
            contentDescription = null
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        if (game.coverUrl.isNotBlank() || game.coverLocalPath.isNotBlank()) {
            loadCover(cover, game.coverLocalPath, game.coverUrl)
        } else {
            cover.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        identity.addView(cover, LinearLayout.LayoutParams(dp(124), dp(168)))

        val info = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        info.addView(makeText(
            "GAME DETAILS  •  MARCO RX6",
            8f,
            if (dark) Color.rgb(142, 205, 255) else Color.rgb(37, 119, 190),
            true
        ).apply { letterSpacing = 0.08f })
        info.addView(makeText(game.name, 19f, white(), true).apply {
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(5), 0, 0)
        })
        info.addView(makeText(
            game.platform + "  •  MARCO RX6",
            10f,
            if (dark) Color.rgb(111, 194, 255) else Color.rgb(30, 139, 235),
            true
        ).apply { setPadding(0, dp(7), 0, dp(5)) })
        if (game.genre.isNotBlank() || game.releaseYear.isNotBlank()) {
            val metadata = listOf(game.genre, game.releaseYear)
                .filter { it.isNotBlank() }
                .joinToString("  •  ")
            info.addView(makeText(metadata, 10f, gray(), true).apply {
                setPadding(0, 0, 0, dp(7))
            })
        }
        if (game.description.isNotBlank()) {
            info.addView(makeText(
                game.description.replace(Regex("\\s+"), " ").trim(),
                10f,
                gray(),
                false
            ).apply {
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
                setLineSpacing(dp(2).toFloat(), 1.05f)
                setPadding(0, dp(3), 0, dp(2))
            })
        }
        val watchlistButton = watchlistViewFactory.create(game)
        info.addView(watchlistButton, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(7)
        })
        identity.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        return identity
    }
}
