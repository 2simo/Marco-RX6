package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.marcorx6.library.data.Game

internal class GameSimilarGamesViewFactory(
    private val activity: Activity,
    private val dp: (Int) -> Int,
    private val rounded: (Int, Int) -> Drawable,
    private val makeText: (String, Float, Int, Boolean) -> TextView,
    private val isDarkMode: () -> Boolean,
    private val white: () -> Int,
    private val translate: (String) -> String,
    private val loadCover: (ImageView, String, String) -> Unit
) {
    fun create(game: Game, games: List<Game>, onSelect: (Game) -> Unit): View? {
        val similar = games.filter {
            it.name != game.name && it.platform.equals(game.platform, ignoreCase = true)
        }.take(5)
        if (similar.isEmpty()) return null

        val dark = isDarkMode()
        val similarBox = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(5), dp(14), dp(9))
        }
        similarBox.addView(
            makeText(
                translate("اكتشف المزيد  •  قد يعجبك أيضاً"),
                12f,
                if (dark) Color.rgb(111, 194, 255) else Color.rgb(30, 139, 235),
                true
            ).apply { setPadding(0, 0, 0, dp(9)) }
        )
        val similarRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        similar.forEach { item ->
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(7), dp(7), dp(7), dp(8))
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    if (dark) intArrayOf(Color.rgb(22, 43, 67), Color.rgb(12, 27, 45))
                    else intArrayOf(Color.WHITE, Color.rgb(232, 243, 255))
                ).apply {
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(1), if (dark) Color.argb(145, 76, 145, 207) else Color.rgb(196, 220, 246))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { onSelect(item) }
                contentDescription = "فتح تفاصيل ${item.name}"
            }
            val cover = ImageView(activity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    setColor(if (dark) Color.rgb(12, 31, 51) else Color.rgb(222, 237, 253))
                    cornerRadius = dp(10).toFloat()
                }
                clipToOutline = true
                contentDescription = "غلاف ${item.name}"
            }
            if (item.coverLocalPath.isNotBlank() || item.coverUrl.isNotBlank()) {
                loadCover(cover, item.coverLocalPath, item.coverUrl)
            } else {
                cover.setImageResource(android.R.drawable.ic_menu_gallery)
                cover.scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            card.addView(cover, LinearLayout.LayoutParams(-1, dp(142)))
            card.addView(TextView(activity).apply {
                text = item.name
                textSize = 11f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                gravity = android.view.Gravity.CENTER
                setTextColor(if (dark) white() else Color.rgb(48, 57, 77))
                setPadding(dp(3), dp(7), dp(3), dp(2))
            }, LinearLayout.LayoutParams(-1, -2))
            similarRow.addView(card, LinearLayout.LayoutParams(dp(126), -2).apply {
                marginEnd = dp(9)
            })
        }
        val scroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            addView(similarRow)
        }
        similarBox.addView(scroll)
        return similarBox
    }
}
