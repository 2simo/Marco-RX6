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
        val gameLabel: String,
        // Compact fingerprint detects in-place metadata edits without allocating
        // several temporary lists and joined strings for every game on each render.
        val filterDataFingerprint: Long
    )

    private fun fingerprintFilterData(games: List<Game>): Long {
        var hash = -0x340d631b7bdddcdbL // FNV-1a 64-bit offset basis
        fun mix(value: Int) {
            hash = (hash xor value.toLong()) * 0x100000001b3L
        }
        games.forEach { game ->
            mix(game.name.hashCode())
            mix(game.platform.hashCode())
            mix(game.genre.hashCode())
            mix(game.releaseYear.hashCode())
            mix(if (game.isFavorite) 1 else 0)
            mix(if (game.blogImported) 1 else 0)
        }
        return hash
    }

    /**
     * Start expensive image/network and Firestore work only when the corresponding
     * row actually intersects the visible screen. Off-screen cards keep placeholders.
     */
    private fun runWhenVisible(view: View, action: () -> Unit) {
        VisibleViewWorkScheduler.watch(view, action)
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
            gameLabel = tr("لعبة"),
            filterDataFingerprint = fingerprintFilterData(games)
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
                else intArrayOf(
                    AccentPalette.surface(
                        if (isDarkMode()) Color.rgb(35, 38, 45) else Color.rgb(255, 255, 255),
                        red,
                        if (isDarkMode()) 0.24f else 0.13f
                    ),
                    AccentPalette.surface(
                        if (isDarkMode()) Color.rgb(15, 17, 22) else Color.rgb(222, 226, 232),
                        red,
                        if (isDarkMode()) 0.20f else 0.17f
                    )
                )
            ).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(
                    dp(1),
                    if (active) red
                    else AccentPalette.surface(
                        if (isDarkMode()) Color.rgb(130, 138, 151) else Color.rgb(160, 168, 181),
                        red,
                        if (isDarkMode()) 0.48f else 0.42f
                    )
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
                    setColor(if (isDarkMode())
                        AccentPalette.surface(Color.rgb(35, 38, 45), red, 0.16f)
                    else AccentPalette.surface(Color.rgb(222, 226, 232), red, 0.09f))
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), if (isDarkMode())
                        AccentPalette.surface(Color.rgb(130, 138, 151), red, 0.36f)
                    else AccentPalette.surface(Color.rgb(160, 168, 181), red, 0.30f))
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
                        setColor(if (isDarkMode())
                            AccentPalette.surface(Color.rgb(35, 38, 45), red, 0.16f)
                        else AccentPalette.surface(Color.rgb(222, 226, 232), red, 0.09f))
                        cornerRadius = dp(11).toFloat()
                        setStroke(dp(1), if (isDarkMode())
                            AccentPalette.surface(Color.rgb(130, 138, 151), red, 0.40f)
                        else AccentPalette.surface(Color.rgb(160, 168, 181), red, 0.34f))
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
                        if (isDarkMode()) intArrayOf(
                            AccentPalette.surface(Color.rgb(35, 38, 45), red, 0.13f),
                            AccentPalette.surface(Color.rgb(15, 17, 22), red, 0.10f)
                        ) else intArrayOf(
                            AccentPalette.surface(Color.rgb(250, 251, 253), red, 0.07f),
                            AccentPalette.surface(Color.rgb(238, 241, 246), red, 0.10f)
                        )
                    ).apply {
                        cornerRadius = dp(18).toFloat()
                        setStroke(
                            dp(1),
                            if (isDarkMode()) AccentPalette.surface(Color.rgb(130, 138, 151), red, 0.38f)
                            else AccentPalette.surface(Color.rgb(160, 168, 181), red, 0.34f)
                        )
                    }
                    background = android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(
                            Color.argb(if (isDarkMode()) 86 else 62, Color.red(red), Color.green(red), Color.blue(red))
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
                    setTextColor(accentForeground())
                    typeface = android.graphics.Typeface.create(
                        "sans-serif-medium", android.graphics.Typeface.BOLD
                    )
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    background = GradientDrawable().apply {
                        setColor(red)
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
                        setTextColor(accentForeground())
                        typeface = android.graphics.Typeface.create(
                            "sans-serif-medium", android.graphics.Typeface.BOLD
                        )
                        gravity = Gravity.CENTER
                        setPadding(dp(8), dp(4), dp(8), dp(4))
                        background = GradientDrawable(
                            GradientDrawable.Orientation.TL_BR,
                            intArrayOf(
                                red,
                                Color.rgb((Color.red(red) * 0.72f).toInt(), (Color.green(red) * 0.72f).toInt(), (Color.blue(red) * 0.72f).toInt())
                            )
                        ).apply {
                            cornerRadius = dp(9).toFloat()
                            setStroke(dp(1), if (isDarkMode()) Color.argb(210, 145, 153, 166)
                                else Color.rgb(132, 143, 158))
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
                        imageTintList = android.content.res.ColorStateList.valueOf(accentForeground())
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        setPadding(dp(6), dp(6), dp(6), dp(6))
                        background = GradientDrawable().apply {
                            setColor(red)
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
                    setColor(
                        if (isDarkMode()) AccentPalette.surface(Color.rgb(8, 9, 12), red, 0.08f)
                        else AccentPalette.surface(Color.rgb(242, 244, 247), red, 0.05f)
                    )
                    cornerRadius = dp(13).toFloat()
                    setStroke(
                        dp(1),
                        if (isDarkMode()) AccentPalette.surface(Color.rgb(130, 138, 151), red, 0.32f)
                        else AccentPalette.surface(Color.rgb(160, 168, 181), red, 0.28f)
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
                        red
                    ),
                    LinearLayout.LayoutParams(0, -2, 1f)
                )
                engagement.addView(
                    metricItem(
                        com.marcorx6.library.R.drawable.ic_engagement_views,
                        tr("المشاهدات"),
                        viewsValue,
                        red
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
                    val row = currentGridRow?.takeUnless { it.childCount == 2 }
                        ?: LinearLayout(activity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.TOP
                        }.also { newRow ->
                            currentGridRow = newRow
                            grid.addView(newRow, LinearLayout.LayoutParams(-1, -2))
                        }
                    row.addView(
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
                // In the ALL library view, each game card participates in a subtle
                // 3D cover-flow as its position changes inside the horizontal rail.
                if (selected == "ALL") {
                    horizontal.clipChildren = false
                    horizontal.clipToPadding = false
                    carousel.clipChildren = false
                    carousel.clipToPadding = false

                    // Coalesce scroll callbacks to one update per display frame and
                    // transform only cards near the viewport. Updating every card on
                    // every scroll event made long libraries do O(all cards) UI work.
                    var depthUpdatePosted = false
                    val updateDepthOnNextFrame = Runnable {
                        depthUpdatePosted = false
                        if (horizontal.width <= 0 || carousel.childCount == 0) return@Runnable
                        val center = horizontal.scrollX + horizontal.width / 2f
                        val slotStep = dp(168).coerceAtLeast(1)
                        val firstVisible = ((horizontal.scrollX / slotStep) - 2)
                            .coerceAtLeast(0)
                        val lastVisible = (((horizontal.scrollX + horizontal.width) / slotStep) + 2)
                            .coerceAtMost(carousel.childCount - 1)
                        for (index in firstVisible..lastVisible) {
                            val slot = carousel.getChildAt(index)
                            if (slot.width <= 0) continue
                            val relative = ((slot.left + slot.width / 2f - center) /
                                slot.width.toFloat()).coerceIn(-1.35f, 1.35f)
                            val depth = kotlin.math.abs(relative).coerceIn(0f, 1f)
                            slot.pivotX = slot.width / 2f
                            slot.pivotY = slot.height * 0.48f
                            slot.cameraDistance = dp(1400).toFloat()
                            slot.rotationY = -relative * 15f
                            slot.rotationX = depth * 1.8f
                            slot.scaleX = 1f - depth * 0.075f
                            slot.scaleY = 1f - depth * 0.075f
                            slot.translationY = depth * dp(5).toFloat() -
                                (1f - depth) * dp(2).toFloat()
                            slot.translationZ = dp(7) * (1f - depth)
                            slot.elevation = (dp(3) + dp(6) * (1f - depth)).toFloat()
                            slot.alpha = 1f - depth * 0.12f
                        }
                    }
                    fun scheduleDepthUpdate() {
                        if (depthUpdatePosted) return
                        depthUpdatePosted = true
                        horizontal.postOnAnimation(updateDepthOnNextFrame)
                    }

                    horizontal.setOnScrollChangeListener { _, _, _, _, _ ->
                        scheduleDepthUpdate()
                    }
                    horizontal.post { scheduleDepthUpdate() }
                    carousel.post { scheduleDepthUpdate() }
                }
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
