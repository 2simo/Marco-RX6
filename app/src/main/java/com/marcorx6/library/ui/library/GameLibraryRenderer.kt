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
    private val showDetails: (Game) -> Unit,
    private val loadEngagementSummary: (Game, (Long, Double, Long) -> Unit) -> Unit,
    private val recordViewOnce: (Game, () -> Unit) -> Unit
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
            chip.background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                if (active) intArrayOf(red, red)
                else if (isDarkMode()) intArrayOf(Color.argb(205, 21, 62, 101), Color.argb(188, 8, 27, 47))
                else intArrayOf(Color.argb(228, 255, 255, 255), Color.argb(210, 222, 239, 255))
            ).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), if (active) red else if (isDarkMode())
                    Color.argb(145, 78, 151, 216) else Color.argb(155, 151, 195, 237))
            }
            chip.setTextColor(if (active) accentForeground() else gray)
        }

        val sorted = GameLibraryManager.filterAndSort(
            games, selected, search.text.toString().trim(), selectedGenre, selectedYear, sortMode
        )
        countText.text = sorted.size.toString() + " " + tr("لعبة")
        if (sorted.isEmpty()) {
            gameList.addView(makeText(tr("لا توجد ألعاب حالياً"), 15f, gray, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(55), dp(10), dp(55))
            })
            return
        }

        val sections = if (selected == "ALL" || selected == "BLOG" || selected == "FAVORITES") {
            val order = listOf("PS5", "PS4", "PS3", "PS2")
            val grouped = sorted.groupBy { it.platform.trim().uppercase() }
            order.filter { grouped[it].orEmpty().isNotEmpty() }.map { it to grouped.getValue(it) } +
                grouped.keys.filter { it !in order }.sorted().map { it to grouped.getValue(it) }
        } else {
            listOf(selected to sorted)
        }

        sections.forEach { (platform, sectionGames) ->
            val heading = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(3), dp(10), dp(3), dp(7))
            }
            heading.addView(makeText(platform, 16f, white, true), LinearLayout.LayoutParams(0, -2, 1f))
            heading.addView(makeText(sectionGames.size.toString() + " " + tr("لعبة"), 10f, gray, true))
            gameList.addView(heading, LinearLayout.LayoutParams(-1, -2))

            val horizontal = android.widget.HorizontalScrollView(activity).apply {
                isHorizontalScrollBarEnabled = false
                clipToPadding = false
                overScrollMode = View.OVER_SCROLL_NEVER
                layoutDirection = if (activity.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL)
                    View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            }
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), dp(3), dp(2), dp(12))
            }

            sectionGames.forEach { game ->
                lateinit var engagementBadge: TextView
                val card = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val surface = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        if (isDarkMode()) intArrayOf(Color.argb(224, 20, 52, 84), Color.argb(210, 7, 24, 43))
                        else intArrayOf(Color.argb(232, 255, 255, 255), Color.argb(218, 225, 241, 255))).apply {
                        cornerRadius = dp(17).toFloat()
                        setStroke(dp(1), if (isDarkMode()) Color.argb(175, 78, 157, 224) else Color.argb(185, 153, 198, 239))
                    }
                    background = android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(Color.argb(75, 82, 175, 255)),
                        surface, null
                    )
                    setPadding(dp(7), dp(7), dp(10), dp(7))
                    isClickable = true
                    isFocusable = true
                    contentDescription = game.name + ", " + tr("عرض التفاصيل")
                    setOnClickListener {
                        recordViewOnce(game) { loadEngagementSummary(game) { views, average, ratings ->
                            activity.runOnUiThread {
                                engagementBadge.text = "★ ${if (ratings == 0L) "—" else String.format(java.util.Locale.US, "%.1f", average)} ($ratings)   •   $views"
                            }
                        } }
                        showDetails(game)
                    }
                }
                val imageFrame = FrameLayout(activity).apply {
                    clipToOutline = true
                    background = rounded(bg, 12)
                }
                val image = ImageView(activity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = null
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                imageFrame.addView(image, FrameLayout.LayoutParams(-1, -1))
                engagementBadge = TextView(activity).apply {
                    text = "★ —   •   Views —"
                    textSize = 9f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(dp(6), dp(4), dp(6), dp(4))
                    background = GradientDrawable().apply {
                        setColor(Color.argb(220, 8, 30, 55))
                        cornerRadius = dp(9).toFloat()
                    }
                }
                imageFrame.addView(engagementBadge, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply {
                    leftMargin = dp(5); bottomMargin = dp(5)
                })
                loadEngagementSummary(game) { views, average, ratings ->
                    activity.runOnUiThread {
                        engagementBadge.text = "★ ${if (ratings == 0L) "—" else String.format(java.util.Locale.US, "%.1f", average)}   👁 $views"
                    }
                }
                if (game.coverUrl.isNotBlank() || game.coverLocalPath.isNotBlank()) {
                    loadCover(image, game.coverLocalPath, game.coverUrl)
                } else {
                    image.setImageResource(android.R.drawable.ic_menu_gallery)
                    image.scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
                val platformBadge = TextView(activity).apply {
                    text = game.platform
                    textSize = 8f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(dp(6), dp(3), dp(6), dp(3))
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(24, 105, 196))
                        cornerRadius = dp(8).toFloat()
                    }
                }
                imageFrame.addView(platformBadge, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
                    leftMargin = dp(5); topMargin = dp(5)
                })
                if (game.isFavorite) {
                    val fav = ImageView(activity).apply {
                        setImageResource(com.marcorx6.library.R.drawable.ic_nav_favorites)
                        imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                        setPadding(dp(5), dp(5), dp(5), dp(5))
                        background = GradientDrawable().apply {
                            setColor(Color.rgb(24, 105, 196)); cornerRadius = dp(14).toFloat()
                        }
                    }
                    imageFrame.addView(fav, FrameLayout.LayoutParams(dp(27), dp(27), Gravity.TOP or Gravity.END).apply {
                        rightMargin = dp(5); topMargin = dp(5)
                    })
                }
                card.addView(imageFrame, LinearLayout.LayoutParams(dp(94), dp(124)))
                val info = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(2), dp(2), dp(2))
                }
                info.addView(makeText(game.name, 12f, white, true).apply {
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(dp(112), -2))
                val meta = listOf(game.genre, game.releaseYear).map { it.trim() }
                    .filter { it.isNotBlank() }.joinToString(" • ")
                if (meta.isNotBlank()) info.addView(makeText(meta, 9f, red, true).apply {
                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(dp(112), -2).apply { topMargin = dp(5) })
                card.addView(info)
                row.addView(card, LinearLayout.LayoutParams(dp(238), dp(138)).apply {
                    marginEnd = dp(9)
                })
            }
            horizontal.addView(row, android.widget.HorizontalScrollView.LayoutParams(-2, -2))
            gameList.addView(horizontal, LinearLayout.LayoutParams(-1, -2))
        }
    }
}
