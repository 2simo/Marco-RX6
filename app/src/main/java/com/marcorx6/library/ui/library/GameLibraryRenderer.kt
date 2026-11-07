package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
) {
    fun render(
        gameList: LinearLayout,
        filters: LinearLayout,
        countText: TextView,
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
                else if (isDarkMode()) intArrayOf(Color.argb(205, 35, 38, 45), Color.argb(188, 15, 17, 22))
                else intArrayOf(Color.argb(228, 255, 255, 255), Color.argb(210, 222, 226, 232))
            ).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(
                    dp(1),
                    if (active) red
                    else if (isDarkMode()) Color.argb(145, 130, 138, 151)
                    else Color.argb(155, 160, 168, 181)
                )
            }
            chip.setTextColor(if (active) accentForeground() else gray)
        }

        val sorted = GameLibraryManager.filterAndSort(
            games, selected, "", selectedGenre, selectedYear, sortMode
        )
        countText.text = sorted.size.toString() + " " + tr("لعبة")
        if (sorted.isEmpty()) {
            gameList.addView(makeText(tr("لا توجد ألعاب حالياً"), 15f, gray, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(55), dp(10), dp(55))
            })
            return
        }

        val sectioned = selected == "ALL" || selected == "BLOG" || selected == "FAVORITES"
        val priority = listOf("PS4", "PS3", "PS2")
        // Remove invisible bidi/zero-width marks before grouping so visually identical
        // platform names cannot produce duplicate sections in Arabic locale.
        fun canonicalPlatform(raw: String): String {
            val clean = raw
                .replace(Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069]"), "")
                .trim()
                .uppercase(Locale.ROOT)
            return GameLibraryManager.normalizePlatform(clean) ?: clean.replace(
                Regex("^PS\\s*([0-9]+)$"), "PS$1"
            )
        }

        val sections = if (sectioned) {
            val grouped = sorted.groupBy { canonicalPlatform(it.platform) }
            priority.filter { grouped[it].orEmpty().isNotEmpty() }.map { it to grouped.getValue(it) } +
                grouped.keys.filter { it !in priority }.sorted().map { it to grouped.getValue(it) }
        } else {
            val label = sorted.firstOrNull()?.platform?.let(::canonicalPlatform)
                ?.takeIf { it.isNotBlank() } ?: selected
            listOf(label to sorted)
        }

        sections.forEach { (platform, sectionGames) ->
            val heading = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                // Isolate Latin platform codes from Arabic text to avoid bidi reordering.
                layoutDirection = activity.resources.configuration.layoutDirection
                setPadding(dp(3), dp(15), dp(3), dp(9))
            }
            val platformTitle = makeText(platform, 16f, white, true).apply {
                textDirection = View.TEXT_DIRECTION_LTR
                gravity = Gravity.CENTER_VERTICAL
            }
            heading.addView(
                platformTitle,
                LinearLayout.LayoutParams(0, -2, 1f)
            )
            val sectionCount = makeText(
                sectionGames.size.toString() + " " + tr("لعبة"),
                11f,
                gray,
                true
            ).apply {
                textDirection = View.TEXT_DIRECTION_RTL
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(5), dp(10), dp(5))
                background = GradientDrawable().apply {
                    setColor(if (isDarkMode()) Color.argb(105, 35, 38, 45) else Color.argb(115, 222, 226, 232))
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), if (isDarkMode()) Color.argb(120, 130, 138, 151) else Color.argb(130, 160, 168, 181))
                }
            }
            heading.addView(sectionCount)
            gameList.addView(heading, LinearLayout.LayoutParams(-1, -2))

            val horizontal = HorizontalScrollView(activity).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                clipToPadding = false
                layoutDirection = activity.resources.configuration.layoutDirection
                setPadding(0, 0, 0, dp(5))
            }
            val carousel = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                layoutDirection = activity.resources.configuration.layoutDirection
                setPadding(dp(2), dp(2), dp(2), dp(3))
            }

            sectionGames.forEach { game ->
                val card = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    val surface = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        if (isDarkMode()) intArrayOf(Color.argb(232, 35, 38, 45), Color.argb(220, 15, 17, 22))
                        else intArrayOf(Color.argb(240, 255, 255, 255), Color.argb(228, 222, 226, 232))
                    ).apply {
                        cornerRadius = dp(18).toFloat()
                        setStroke(
                            dp(1),
                            if (isDarkMode()) Color.argb(175, 130, 138, 151)
                            else Color.argb(185, 160, 168, 181)
                        )
                    }
                    background = android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(
                            if (isDarkMode()) Color.argb(86, 190, 198, 210)
                            else Color.argb(62, 140, 148, 160)
                        ),
                        surface,
                        GradientDrawable().apply {
                            setColor(Color.WHITE)
                            cornerRadius = dp(18).toFloat()
                        }
                    )
                    setPadding(dp(7), dp(7), dp(7), dp(9))
                    elevation = dp(4).toFloat()
                    stateListAnimator = null
                    isClickable = true
                    isFocusable = true
                    minimumHeight = dp(48)
                    contentDescription = listOf(game.name, game.platform, game.releaseYear)
                        .filter { it.isNotBlank() }.joinToString(", ") + ", " + tr("عرض التفاصيل")
                    setOnClickListener { showDetails(game) }
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
                val platformBadge = TextView(activity).apply {
                    text = game.platform
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    textSize = 9f
                    setTextColor(Color.rgb(20, 23, 29))
                    typeface = android.graphics.Typeface.create(
                        "sans-serif-medium", android.graphics.Typeface.BOLD
                    )
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(190, 198, 210))
                        cornerRadius = dp(9).toFloat()
                    }
                }
                imageFrame.addView(
                    platformBadge,
                    FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
                        leftMargin = dp(7)
                        topMargin = dp(7)
                    }
                )

                // Resolve the game's language from its stored field, with the original
                // Blogger article as a fallback for older/imported entries.
                val languageLabel = GameLanguageBadge.resolve(game)
                if (languageLabel.isNotEmpty() && selected != "BLOG") {
                    val languageBadge = TextView(activity).apply {
                        text = languageLabel
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        textSize = 9f
                        setTextColor(Color.WHITE)
                        typeface = android.graphics.Typeface.create(
                            "sans-serif-medium", android.graphics.Typeface.BOLD
                        )
                        gravity = Gravity.CENTER
                        setPadding(dp(8), dp(4), dp(8), dp(4))
                        background = GradientDrawable().apply {
                            setColor(Color.argb(220, 35, 38, 45))
                            cornerRadius = dp(9).toFloat()
                            setStroke(dp(1), Color.argb(210, 145, 153, 166))
                        }
                    }
                    imageFrame.addView(
                        languageBadge,
                        FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
                            rightMargin = dp(7)
                            bottomMargin = dp(7)
                        }
                    )
                }
                if (game.isFavorite) {
                    val favoriteBadge = ImageView(activity).apply {
                        setImageResource(com.marcorx6.library.R.drawable.ic_nav_favorites)
                        imageTintList = android.content.res.ColorStateList.valueOf(Color.rgb(20, 23, 29))
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        setPadding(dp(6), dp(6), dp(6), dp(6))
                        background = GradientDrawable().apply {
                            setColor(Color.rgb(190, 198, 210))
                            cornerRadius = dp(17).toFloat()
                        }
                    }
                    imageFrame.addView(
                        favoriteBadge,
                        FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP or Gravity.END).apply {
                            rightMargin = dp(7)
                            topMargin = dp(7)
                        }
                    )
                }
                if (game.coverUrl.isNotBlank() || game.coverLocalPath.isNotBlank()) {
                    loadCover(image, game.coverLocalPath, game.coverUrl)
                } else {
                    image.setImageResource(android.R.drawable.ic_menu_gallery)
                    image.scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
                imageFrame.background = GradientDrawable().apply {
                    setColor(if (isDarkMode()) Color.rgb(8, 9, 12) else Color.rgb(242, 244, 247))
                    cornerRadius = dp(13).toFloat()
                    setStroke(
                        dp(1),
                        if (isDarkMode()) Color.argb(125, 130, 138, 151)
                        else Color.argb(135, 160, 168, 181)
                    )
                }
                card.addView(imageFrame, LinearLayout.LayoutParams(-1, dp(164)))

                card.addView(makeText(game.name, 12f, white, true).apply {
                    maxLines = 2
                    minLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER_VERTICAL
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    setPadding(dp(3), dp(7), dp(3), dp(3))
                })

                val meta = listOf(game.genre, game.releaseYear)
                    .map { it.trim() }.filter { it.isNotBlank() }.joinToString(" • ")
                if (meta.isNotBlank()) {
                    card.addView(makeText(meta, 9f, gray, false).apply {
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setPadding(dp(3), 0, dp(3), dp(4))
                    })
                }

                val engagement = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutDirection = activity.resources.configuration.layoutDirection
                    setPadding(dp(2), dp(4), dp(2), 0)
                }
                val ratingValue = makeText("—", 9f, gray, true)
                val viewsValue = makeText("0", 9f, gray, true)
                fun metricItem(iconRes: Int, description: String, valueView: TextView, tint: Int): LinearLayout =
                    LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        contentDescription = description
                        addView(ImageView(activity).apply {
                            setImageResource(iconRes)
                            imageTintList = android.content.res.ColorStateList.valueOf(tint)
                            contentDescription = null
                        }, LinearLayout.LayoutParams(dp(13), dp(13)))
                        addView(valueView.apply { setPadding(dp(4), 0, 0, 0) })
                    }
                engagement.addView(
                    metricItem(
                        com.marcorx6.library.R.drawable.ic_engagement_rating,
                        tr("التقييمات"),
                        ratingValue,
                        Color.rgb(255, 190, 55)
                    ),
                    LinearLayout.LayoutParams(0, -2, 1f)
                )
                engagement.addView(
                    metricItem(
                        com.marcorx6.library.R.drawable.ic_engagement_views,
                        tr("المشاهدات"),
                        viewsValue,
                        Color.rgb(190, 198, 210)
                    ),
                    LinearLayout.LayoutParams(0, -2, 1f)
                )
                card.addView(engagement)
                loadEngagementSummary(game) { views, average, count ->
                    activity.runOnUiThread {
                        ratingValue.text = if (count == 0L) "—"
                        else String.format(Locale.US, "%.1f", average)
                        viewsValue.text = compactCount(views)
                    }
                }

                carousel.addView(
                    card,
                    LinearLayout.LayoutParams(dp(158), -2).apply {
                        marginStart = dp(5)
                        marginEnd = dp(5)
                    }
                )
            }
            horizontal.addView(carousel, FrameLayout.LayoutParams(-2, -2))
            gameList.addView(horizontal, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun compactCount(value: Long): String = when {
        value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}
