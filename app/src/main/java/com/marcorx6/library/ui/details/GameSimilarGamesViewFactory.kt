package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
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
    private val red: () -> Int,
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
                red(),
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
                    if (dark) intArrayOf(
                        AccentPalette.surface(Color.rgb(35, 38, 45), red(), 0.13f),
                        AccentPalette.surface(Color.rgb(15, 17, 22), red(), 0.10f)
                    ) else intArrayOf(
                        AccentPalette.surface(Color.WHITE, red(), 0.07f),
                        AccentPalette.surface(Color.rgb(242, 244, 247), red(), 0.10f)
                    )
                ).apply {
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(1), if (dark)
                        AccentPalette.surface(Color.rgb(130, 138, 151), red(), 0.38f)
                    else AccentPalette.surface(Color.rgb(160, 168, 181), red(), 0.32f))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { onSelect(item) }
                contentDescription = "${translate("فتح تفاصيل")} ${item.name}"
            }
            val cover = ImageView(activity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    setColor(if (dark) Color.rgb(35, 38, 45) else Color.rgb(222, 226, 232))
                    cornerRadius = dp(10).toFloat()
                }
                clipToOutline = true
                contentDescription = "${translate("غلاف")} ${item.name}"
            }
            if (item.coverLocalPath.isNotBlank() || item.coverUrl.isNotBlank()) {
                loadCover(cover, item.coverLocalPath, item.coverUrl)
            } else {
                cover.setImageResource(android.R.drawable.ic_menu_gallery)
                cover.scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            val coverFrame = FrameLayout(activity).apply {
                background = GradientDrawable().apply {
                    setColor(if (dark) Color.rgb(35, 38, 45) else Color.rgb(222, 226, 232))
                    cornerRadius = dp(10).toFloat()
                }
                clipToOutline = true
                addView(cover, FrameLayout.LayoutParams(-1, -1))
            }
            val languageLabel = GameLanguageBadge.resolve(item)
            if (languageLabel.isNotBlank()) {
                val languageBadge = TextView(activity).apply {
                    text = languageLabel
                    textSize = 8.5f
                    setTextColor(AccentPalette.foregroundForColor(red()))
                    typeface = android.graphics.Typeface.create(
                        "sans-serif-medium", android.graphics.Typeface.BOLD
                    )
                    gravity = Gravity.CENTER
                    setPadding(dp(7), dp(4), dp(7), dp(4))
                    background = GradientDrawable().apply {
                        setColor(red())
                        cornerRadius = dp(8).toFloat()
                        setStroke(dp(1), red())
                    }
                }
                coverFrame.addView(
                    languageBadge,
                    FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
                        rightMargin = dp(6)
                        bottomMargin = dp(6)
                    }
                )
            }
            card.addView(coverFrame, LinearLayout.LayoutParams(-1, dp(142)))
            card.addView(TextView(activity).apply {
                text = item.name
                textSize = 11f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                gravity = android.view.Gravity.CENTER
                setTextColor(if (dark) white() else Color.rgb(25, 29, 36))
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
