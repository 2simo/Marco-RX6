package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
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
    private val openCategory: (String, String) -> Unit,
) {
    private data class RenderSignature(
        val selected: String,
        val selectedGenre: String,
        val selectedYear: String,
        val sortMode: String,
        val filterLabels: List<String>,
        val colors: List<Int>,
        val darkMode: Boolean,
        val gameLabel: String
    )

    /**
     * Start expensive image/network and Firestore work only when the corresponding
     * row actually intersects the visible screen. Off-screen cards keep placeholders.
     */
    private fun runWhenVisible(view: View, action: () -> Unit) {
        var completed = false
        var observer: ViewTreeObserver? = null
        lateinit var checkVisibility: () -> Unit
        val scrollListener = ViewTreeObserver.OnScrollChangedListener { checkVisibility() }

        fun removeObserver() {
            observer?.takeIf { it.isAlive }?.removeOnScrollChangedListener(scrollListener)
            observer = null
        }

        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                observer = v.viewTreeObserver
                if (observer?.isAlive == true) observer?.addOnScrollChangedListener(scrollListener)
                v.post { checkVisibility() }
            }

            override fun onViewDetachedFromWindow(v: View) {
                removeObserver()
            }
        }

        checkVisibility = {
            if (!completed && view.isAttachedToWindow) {
                val visible = Rect()
                if (view.getGlobalVisibleRect(visible) && visible.width() > 0 && visible.height() > 0) {
                    completed = true
                    removeObserver()
                    view.removeOnAttachStateChangeListener(attachListener)
                    action()
                }
            }
        }

        view.addOnAttachStateChangeListener(attachListener)
        if (view.isAttachedToWindow) {
            observer = view.viewTreeObserver
            if (observer?.isAlive == true) observer?.addOnScrollChangedListener(scrollListener)
            view.post { checkVisibility() }
        }
    }

    private var lastRenderedContainer: LinearLayout? = null
    private var lastRenderedGames: List<Game>? = null
    private var lastRenderSignature: RenderSignature? = null

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
        // Game is a data class containing full Blogger HTML. Compare object identity
        // instead of deep equality so a refresh does not repeatedly compare hundreds
        // of potentially large article bodies just to decide whether to redraw cards.
        val previousGames = lastRenderedGames
        val sameGameInstances = previousGames != null &&
            previousGames.size == games.size &&
            previousGames.indices.all { previousGames[it] === games[it] }
        val signature = RenderSignature(
            selected = selected,
            selectedGenre = selectedGenre,
            selectedYear = selectedYear,
            sortMode = sortMode,
            filterLabels = (0 until filters.childCount).map { filters.getChildAt(it).let { child ->
                (child as? TextView)?.text?.toString().orEmpty()
            } },
            colors = listOf(bg, panel, red, white, gray),
            darkMode = isDarkMode(),
            gameLabel = tr("لعبة")
        )
        // Skip rebuilding the entire carousel when a caller requests a render with
        // unchanged data and presentation state. This prevents card/cover flicker
        // and avoids re-issuing image and engagement requests on every refresh.
        if (lastRenderedContainer === gameList &&
            sameGameInstances &&
            lastRenderSignature == signature &&
            gameList.childCount > 0
        ) return

        lastRenderedContainer = gameList
        lastRenderedGames = games.toList()
        lastRenderSignature = signature
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

        val gridMode = selected in setOf("PS2", "PS3", "PS4", "PS3_ARABIC", "FAVORITES")
        val sectioned = selected == "ALL" || selected == "BLOG"
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
            val sectionTitle = when (platform) {
                "PS3_ARABIC" -> tr("PS3 Arabic")
                else -> platform
            }
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
            if (selected == "ALL") {
                val openSection = TextView(activity).apply {
                    text = tr("فتح")
                    textSize = 10f
                    typeface = android.graphics.Typeface.create(
                        "sans-serif-medium", android.graphics.Typeface.BOLD
                    )
                    gravity = Gravity.CENTER
                    setTextColor(if (isDarkMode()) Color.rgb(225, 228, 234) else Color.rgb(43, 47, 55))
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    background = GradientDrawable().apply {
                        setColor(if (isDarkMode()) Color.argb(105, 35, 38, 45) else Color.argb(115, 222, 226, 232))
                        cornerRadius = dp(11).toFloat()
                        setStroke(dp(1), if (isDarkMode()) Color.argb(145, 130, 138, 151) else Color.argb(155, 160, 168, 181))
                    }
                    isClickable = true
                    isFocusable = true
                    contentDescription = tr("فتح قسم") + " " + sectionTitle
                    setOnClickListener { openCategory(platform, sectionTitle) }
                }
                heading.addView(openSection, LinearLayout.LayoutParams(-2, -2).apply {
                    marginEnd = dp(7)
                })
            }
            heading.addView(sectionCount)
            if (!gridMode) gameList.addView(heading, LinearLayout.LayoutParams(-1, -2))

            val grid = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(2), 0, dp(8))
            }
            var currentGridRow: LinearLayout? = null
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
                // Keep only a lightweight slot in the layout until this card approaches the viewport.
                val slot = FrameLayout(activity).apply {
                    minimumHeight = dp(if (gridMode) 300 else 258)
                }
                val buildCard = {
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
                            if (isDarkMode()) Color.argb(86, 76, 145, 235)
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
                        setColor(Color.rgb(76, 145, 235))
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
                if (languageLabel.isNotEmpty() && GameLibraryManager.normalizePlatform(game.platform) != null) {
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
                            setColor(Color.rgb(76, 145, 235))
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
                    runWhenVisible(image) {
                        loadCover(image, game.coverLocalPath, game.coverUrl)
                    }
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
                card.addView(imageFrame, LinearLayout.LayoutParams(-1, dp(if (gridMode) 208 else 164)))

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
                // Reserve the same metadata row on every card. Without this, cards
                // with no genre/year become shorter and their rating/view metrics
                // no longer line up across the horizontal carousel.
                card.addView(makeText(meta.ifBlank { " " }, 9f, gray, false).apply {
                    minLines = 1
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(dp(3), 0, dp(3), dp(4))
                })

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
                        Color.rgb(76, 145, 235)
                    ),
                    LinearLayout.LayoutParams(0, -2, 1f)
                )
                card.addView(engagement)
                runWhenVisible(engagement) {
                    loadEngagementSummary(game) { views, average, count ->
                        activity.runOnUiThread {
                            if (!engagement.isAttachedToWindow) return@runOnUiThread
                            ratingValue.text = if (count == 0L) "—"
                            else String.format(Locale.US, "%.1f", average)
                            viewsValue.text = compactCount(views)
                        }
                    }
                }

                slot.addView(card, FrameLayout.LayoutParams(-1, -2))
                }
                runWhenVisible(slot) { buildCard() }

                if (gridMode) {
                    if (currentGridRow == null || currentGridRow!!.childCount == 2) {
                        currentGridRow = LinearLayout(activity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.TOP
                        }
                        grid.addView(currentGridRow, LinearLayout.LayoutParams(-1, -2))
                    }
                    currentGridRow!!.addView(
                        slot,
                        LinearLayout.LayoutParams(0, -2, 1f).apply {
                            marginStart = dp(4)
                            marginEnd = dp(4)
                            bottomMargin = dp(10)
                        }
                    )
                } else {
                    carousel.addView(
                        slot,
                        LinearLayout.LayoutParams(dp(158), -2).apply {
                            marginStart = dp(5)
                            marginEnd = dp(5)
                        }
                    )
                }
            }
            if (gridMode) {
                gameList.addView(grid, LinearLayout.LayoutParams(-1, -2))
            } else {
                horizontal.addView(carousel, FrameLayout.LayoutParams(-2, -2))
                gameList.addView(horizontal, LinearLayout.LayoutParams(-1, -2))
            }
        }
    }

    private fun compactCount(value: Long): String = when {
        value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}
