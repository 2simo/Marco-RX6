package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.marcorx6.library.data.Game

internal class GameSimilarGamesViewFactory(
    private val activity: Activity,
    private val dp: (Int) -> Int,
    private val rounded: (Int, Int) -> Drawable,
    private val makeText: (String, Float, Int, Boolean) -> TextView,
    private val isDarkMode: () -> Boolean,
    private val white: () -> Int
) {
    fun create(game: Game, games: List<Game>, onSelect: (Game) -> Unit): View? {
        val similar = games.filter {
            it.name != game.name && (it.platform == game.platform || it.blogImported == game.blogImported)
        }.take(5)
        if (similar.isEmpty()) return null

        val dark = isDarkMode()
        val similarBox = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(5), dp(14), dp(9))
        }
        similarBox.addView(
            makeText(
                "اكتشف المزيد  •  قد يعجبك أيضاً",
                12f,
                if (dark) Color.rgb(111, 194, 255) else Color.rgb(30, 139, 235),
                true
            ).apply { setPadding(0, 0, 0, dp(9)) }
        )
        val similarRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        similar.forEach { item ->
            similarRow.addView(TextView(activity).apply {
                text = item.name
                textSize = 10f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(if (dark) white() else Color.rgb(48, 57, 77))
                background = rounded(if (dark) Color.rgb(35, 40, 57) else Color.rgb(232, 236, 245), 10)
                setPadding(dp(10), dp(9), dp(10), dp(9))
                setOnClickListener { onSelect(item) }
            }, LinearLayout.LayoutParams(dp(118), -2).apply { marginEnd = dp(7) })
        }
        val scroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            addView(similarRow)
        }
        similarBox.addView(scroll)
        return similarBox
    }
}
