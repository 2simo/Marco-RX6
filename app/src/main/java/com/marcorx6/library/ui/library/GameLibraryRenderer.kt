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
import java.util.Locale

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
                setPadding(dp(3), dp(12), dp(3), dp(7))
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
                gravity = Gravity.TOP
                setPadding(dp(2), dp(3), dp(2), dp(13))
            }

            sectionGames.forEach { game ->
                lateinit var ratingValue: TextView
                lateinit var viewsValue: TextView
                val card = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.TOP
                    val surface = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        if (isDarkMode()) intArrayOf(Color.argb(230, 20, 52, 84), Color.argb(218, 7, 24, 43))
                        else intArrayOf(Color.argb(245, 255, 255, 255), Color.argb(232, 225, 241, 255))).apply {
                        cornerRadius = dp(15).toFloat()
                        setStroke(dp(1), if (isDarkMode()) Color.argb(175, 78, 157, 224) else Color.argb(185, 153, 198, 239))
                    }
                    background = android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(Color.argb(75, 82, 175, 255)),
                        surface, null
                    )
                    setPadding(dp(6), dp(6), dp(6), dp(8))
                    isClickable = true
                    isFocusable = true
                    contentDescription = game.name + ", " + tr("عرض التفاصيل")
                    setOnClickListener {
                        recordViewOnce(game) {
                            loadEngagementSummary(game) { views, average, ratings ->
                                activity.runOnUiThread {
                                    ratingValue.text = if (ratings == 0L) "—" else String.format(Locale.US, "%.1f", average)
                                    viewsValue.text = compactCount(views)
                                }
                            }
                        }
                        showDetails(game)
                    }
                }

                val imageFrame = FrameLayout(activity).apply {
                    clipToOutline = true
                    background = rounded(bg, 11)
                }
                val image = ImageView(activity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = tr("غلاف") + " " + game.name
                }
                imageFrame.addView(image, FrameLayout.LayoutParams(-1, -1))
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
                card.addView(imageFrame, LinearLayout.LayoutParams(dp(132), dp(164)))
                if (game.coverUrl.isNotBlank() || game.coverLocalPath.isNotBlank()) {
                    loadCover(image, game.coverLocalPath, game.coverUrl)
                } else {
                    image.setImageResource(android.R.drawable.ic_menu_gallery)
                    image.scaleType = ImageView.ScaleType.CENTER_INSIDE
                }

                card.addView(makeText(game.name, 11f, white, true).apply {
                    maxLines = 2
                    minLines = 2
                    gravity = Gravity.START
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(dp(2), dp(7), dp(2), dp(2))
                }, LinearLayout.LayoutParams(dp(132), -2))

                val meta = listOf(game.genre, game.releaseYear).map { it.trim() }
                    .filter { it.isNotBlank() }.joinToString(" • ")
                if (meta.isNotBlank()) {
                    card.addView(makeText(meta, 9f, gray, false).apply {
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setPadding(dp(2), 0, dp(2), dp(5))
                    }, LinearLayout.LayoutParams(dp(178), -2))
                }

                val stats = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(2), dp(3), dp(2), 0)
                }
                fun stat(iconRes: Int, initial: String, tint: Int): Pair<LinearLayout, TextView> {
                    val item = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = GradientDrawable().apply {
                            setColor(if (isDarkMode()) Color.argb(150, 5, 19, 34) else Color.argb(180, 235, 244, 255))
                            cornerRadius = dp(8).toFloat()
                        }
                        setPadding(dp(5), dp(4), dp(5), dp(4))
                    }
                    val icon = ImageView(activity).apply {
                        setImageResource(iconRes)
                        imageTintList = android.content.res.ColorStateList.valueOf(tint)
                        contentDescription = null
                    }
                    item.addView(icon, LinearLayout.LayoutParams(dp(13), dp(13)))
                    val value = makeText(initial, 9f, white, true).apply {
                        setPadding(dp(3), 0, 0, 0)
                    }
                    item.addView(value)
                    return item to value
                }
                val (ratingChip, ratingText) = stat(com.marcorx6.library.R.drawable.ic_engagement_rating, "—", Color.rgb(255, 190, 55))
                val (viewsChip, viewsText) = stat(com.marcorx6.library.R.drawable.ic_engagement_views, "—", Color.rgb(72, 174, 255))
                ratingValue = ratingText
                viewsValue = viewsText
                stats.addView(ratingChip, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
                stats.addView(viewsChip, LinearLayout.LayoutParams(0, -2, 1f))
                card.addView(stats, LinearLayout.LayoutParams(dp(132), -2))

                loadEngagementSummary(game) { views, average, ratings ->
                    activity.runOnUiThread {
                        ratingValue.text = if (ratings == 0L) "—" else String.format(Locale.US, "%.1f", average)
                        viewsValue.text = compactCount(views)
                    }
                }
                row.addView(card, LinearLayout.LayoutParams(dp(144), -2).apply {
                    marginEnd = dp(9)
                })
            }
            horizontal.addView(row, FrameLayout.LayoutParams(-2, -2))
            gameList.addView(horizontal, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun compactCount(value: Long): String = when {
        value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}
