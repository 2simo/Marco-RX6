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
                    chip.isSelected = active
                    chip.contentDescription = chip.text.toString() + ", " +
                        tr(if (active) "محدد" else "غير محدد")
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
                for (index in sorted.indices step 2) {
                    val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
                    for (offset in 0..1) {
                        val itemIndex = index + offset
                        if (itemIndex >= sorted.size) {
                            row.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = dp(5); marginEnd = dp(5) })
                            continue
                        }
                        val game = sorted[itemIndex]
                        val card = LinearLayout(activity).apply {
                            orientation = LinearLayout.VERTICAL
                            val surface = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                                if (isDarkMode())
                                    intArrayOf(Color.argb(224, 20, 52, 84), Color.argb(210, 7, 24, 43))
                                else intArrayOf(Color.argb(232, 255, 255, 255), Color.argb(218, 225, 241, 255))).apply {
                                cornerRadius = dp(20).toFloat()
                                setStroke(dp(1), if (isDarkMode()) Color.argb(175, 78, 157, 224) else Color.argb(185, 153, 198, 239))
                            }
                            background = android.graphics.drawable.RippleDrawable(
                                android.content.res.ColorStateList.valueOf(
                                    if (isDarkMode()) Color.argb(86, 83, 177, 255) else Color.argb(62, 35, 126, 224)
                                ),
                                surface,
                                GradientDrawable().apply {
                                    setColor(Color.WHITE)
                                    cornerRadius = dp(20).toFloat()
                                }
                            )
                            setPadding(dp(7), dp(7), dp(7), dp(10))
                            elevation = dp(5).toFloat()
                            stateListAnimator = null
                            isClickable = true
                            isFocusable = true
                            minimumHeight = dp(48)
                            contentDescription = listOf(game.name, game.platform, game.releaseYear)
                                .filter { it.isNotBlank() }.joinToString(", ") + ", " + tr("عرض التفاصيل")
                            setOnClickListener { showDetails(game) }
                        }
                        val imageFrame = FrameLayout(activity).apply { clipToOutline = true; background = rounded(bg, 12) }
                        val image = ImageView(activity).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            contentDescription = null
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        }
                        imageFrame.addView(image, FrameLayout.LayoutParams(-1, -1))
                        val platformBadge = TextView(activity).apply {
                            text = game.platform
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            textSize = 9f
                            setTextColor(Color.WHITE)
                            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                            gravity = Gravity.CENTER
                            setPadding(dp(9), dp(5), dp(9), dp(5))
                            background = GradientDrawable().apply {
                                setColor(Color.rgb(24, 105, 196))
                                cornerRadius = dp(10).toFloat()
                            }
                        }
                        imageFrame.addView(platformBadge, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
                            leftMargin = dp(8)
                            topMargin = dp(8)
                        })
                        if (game.isFavorite) {
                            val favoriteBadge = ImageView(activity).apply {
                                setImageResource(com.marcorx6.library.R.drawable.ic_nav_favorites)
                                imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                                scaleType = ImageView.ScaleType.CENTER_INSIDE
                                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                                setPadding(dp(7), dp(7), dp(7), dp(7))
                                background = GradientDrawable().apply {
                                    setColor(Color.rgb(24, 105, 196))
                                    cornerRadius = dp(18).toFloat()
                                }
                            }
                            imageFrame.addView(favoriteBadge, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.TOP or Gravity.END).apply {
                                rightMargin = dp(8)
                                topMargin = dp(8)
                            })
                        }
                        if (game.coverUrl.isNotBlank() || game.coverLocalPath.isNotBlank()) loadCover(image, game.coverLocalPath, game.coverUrl)
                        else { image.setImageResource(android.R.drawable.ic_menu_gallery); image.scaleType = ImageView.ScaleType.CENTER_INSIDE }
                        imageFrame.background = GradientDrawable().apply {
                            setColor(if (isDarkMode()) Color.rgb(7, 18, 34) else Color.rgb(225, 240, 255))
                            cornerRadius = dp(13).toFloat()
                            setStroke(dp(1), if (isDarkMode()) Color.argb(125, 81, 157, 224) else Color.argb(135, 150, 197, 241))
                        }
                        card.addView(imageFrame, LinearLayout.LayoutParams(-1, dp(184)))
                        card.addView(makeText(game.name, 13f, white, true).apply {
                            maxLines = 2; minLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(7), dp(4), dp(2))
                        })
                        val meta = listOf(game.platform, game.releaseYear).filter { it.isNotBlank() }.joinToString(" • ")
                        card.addView(makeText(meta, 9f, red, true).apply {
                            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(dp(4), 0, dp(4), dp(2))
                        })
                        row.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5); marginEnd = dp(5); bottomMargin = dp(12) })
                    }
                    gameList.addView(row, LinearLayout.LayoutParams(-1, -2))
                }
    }
}
