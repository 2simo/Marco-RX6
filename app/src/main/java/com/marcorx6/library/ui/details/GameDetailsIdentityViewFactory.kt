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
                if (dark) intArrayOf(Color.rgb(24, 67, 111), Color.rgb(13, 32, 55), Color.rgb(8, 20, 36))
                else intArrayOf(Color.rgb(250, 253, 255), Color.rgb(226, 239, 253), Color.rgb(232, 243, 255))
            ).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(64, 124, 184) else Color.rgb(174, 204, 236))
            }
            elevation = dp(3).toFloat()
        }
        val cover = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(if (dark) Color.rgb(29, 58, 91) else Color.rgb(235, 239, 247), 12)
            contentDescription = game.name
        }
        if (game.coverUrl.isNotBlank() || game.coverLocalPath.isNotBlank()) {
            loadCover(cover, game.coverLocalPath, game.coverUrl)
        } else {
            cover.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        identity.addView(cover, LinearLayout.LayoutParams(dp(112), dp(152)))

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
        val watchlistButton = watchlistViewFactory.create(game)
        info.addView(watchlistButton, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(7)
        })
        identity.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        return identity
    }
}
