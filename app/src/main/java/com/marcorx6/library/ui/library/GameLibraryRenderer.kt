package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.marcorx6.library.data.Game
import com.marcorx6.library.data.GameLibraryManager

internal class GameLibraryRenderer(
    private val activity: Activity,
    private val dp: (Int) -> Int,
    private val rounded: (Int, Int) -> android.graphics.drawable.Drawable,
    private val makeText: (String, Float, Int, Boolean) -> TextView,
    private val isDarkMode: () -> Boolean,
    private val accentForeground: () -> Int,
    private val tr: (String) -> String,
    private val loadCover: (ImageView, String, String) -> Unit,
    private val showDetails: (Game) -> Unit
) {
    fun render(
        gameList: LinearLayout,
        filters: LinearLayout,
        countText: TextView,
        search: EditText,
        games: List<Game>,
        selected: String,
        selectedGenre: String,
        selectedYear: String,
        sortMode: String,
        bg: Int,
        panel: Int,
        red: Int,
        white: Int,
        gray: Int
    ) {
                gameList.removeAllViews()
                for (i in 0 until filters.childCount) {
                    val chip = filters.getChildAt(i) as TextView
                    val active = chip.tag?.toString() == selected
                    chip.background = rounded(if (active) red else panel, 13)
                    chip.setTextColor(if (active) accentForeground() else gray)
                }
                val sorted = GameLibraryManager.filterAndSort(games, selected, search.text.toString().trim(), selectedGenre, selectedYear, sortMode)
                countText.text = sorted.size.toString() + " " + tr("لعبة")
                if (sorted.isEmpty()) {
                    gameList.addView(makeText(tr("لا توجد ألعاب حالياً"), 15f, gray, true).apply {
                        gravity = Gravity.CENTER; setPadding(dp(10), dp(55), dp(10), dp(55))
                    })
                    return
                }
                val heading = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val title = when (selected) {
                    "ALL" -> tr("اكتشف الألعاب"); "FAVORITES" -> tr("المفضلة"); "BLOG" -> tr("المدونة")
                    "WANT_TO_PLAY" -> "أريد لعبها"; "PLAYING" -> "ألعبها حالياً"; "COMPLETED" -> "أنهيتها"
                    else -> "ألعاب " + selected
                }
                heading.addView(makeText(title, 18f, white, true), LinearLayout.LayoutParams(0, -2, 1f))
                heading.addView(makeText(sorted.size.toString() + " " + tr("لعبة"), 10f, red, true))
                gameList.addView(heading, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(2), dp(10), dp(2), dp(12)) })
                for (index in sorted.indices step 3) {
                    val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
                    for (offset in 0..2) {
                        val itemIndex = index + offset
                        if (itemIndex >= sorted.size) {
                            row.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = dp(5); marginEnd = dp(5) })
                            continue
                        }
                        val game = sorted[itemIndex]
                        val card = LinearLayout(activity).apply {
                            orientation = LinearLayout.VERTICAL
                            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                                if (isDarkMode()) intArrayOf(Color.rgb(20, 34, 57), Color.rgb(11, 20, 36)) else intArrayOf(Color.WHITE, Color.rgb(235, 241, 250))).apply {
                                cornerRadius = dp(18).toFloat()
                                setStroke(dp(1), if (isDarkMode()) Color.rgb(37, 75, 119) else Color.rgb(215, 225, 239))
                            }
                            setPadding(dp(6), dp(6), dp(6), dp(8)); elevation = dp(2).toFloat(); isClickable = true; isFocusable = true
                            setOnClickListener { showDetails(game) }
                        }
                        val imageFrame = FrameLayout(activity).apply { clipToOutline = true; background = rounded(bg, 12) }
                        val image = ImageView(activity).apply { scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = game.name }
                        imageFrame.addView(image, FrameLayout.LayoutParams(-1, -1))
                        if (game.coverUrl.isNotBlank() || game.coverLocalPath.isNotBlank()) loadCover(image, game.coverLocalPath, game.coverUrl)
                        else { image.setImageResource(android.R.drawable.ic_menu_gallery); image.scaleType = ImageView.ScaleType.CENTER_INSIDE }
                        imageFrame.background = rounded(if (isDarkMode()) Color.rgb(8, 16, 29) else Color.rgb(224, 232, 243), 13)
                        card.addView(imageFrame, LinearLayout.LayoutParams(-1, dp(142)))
                        card.addView(makeText(game.name, 12.5f, white, true).apply {
                            maxLines = 2; minLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(7), dp(4), dp(2))
                        })
                        val meta = listOf(game.platform, game.releaseYear).filter { it.isNotBlank() }.joinToString(" • ")
                        card.addView(makeText(meta, 9f, red, true).apply {
                            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(dp(4), 0, dp(4), dp(2))
                        })
                        row.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(3); marginEnd = dp(3); bottomMargin = dp(8) })
                    }
                    gameList.addView(row, LinearLayout.LayoutParams(-1, -2))
                }
    }
}
