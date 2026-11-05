package com.marcorx6.library.ui.library

import com.marcorx6.library.R

import com.marcorx6.library.ui.*

import android.content.Context

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

internal class MainLibraryInterfaceController(
    private val context: Context,
    private val setContentView: (View) -> Unit,
    private val darkMode: () -> Boolean,
    private val currentLanguage: () -> String,
    private val dp: (Int) -> Int,
    private val tr: (String) -> String,
    private val rounded: (Int, Int) -> GradientDrawable,
    private val createText: (String, Float, Int, Boolean) -> TextView,
    private val games: () -> List<com.marcorx6.library.data.Game>,
    private val bg: Int,
    private val panel: Int,
    private val red: Int,
    private val white: Int,
    private val gray: Int,
    private val adminSession: () -> Boolean,
    private val getSelected: () -> String,
    private val setSelected: (String) -> Unit,
    private val getSortMode: () -> String,
    private val setSortMode: (String) -> Unit,
    private val setSearch: (EditText) -> Unit,
    private val setFilters: (LinearLayout) -> Unit,
    private val setCountText: (TextView) -> Unit,
    private val setGameList: (LinearLayout) -> Unit,
    private val renderGames: () -> Unit,
    private val toggleDarkMode: () -> Unit,
    private val showAppearanceSettings: () -> Unit,
    private val showAdvancedFilters: () -> Unit,
    private val showGameDialog: () -> Unit,
    private val showProfile: () -> Unit,
    private val showDownloads: () -> Unit,
    private val applyTypography: (View) -> Unit,
    private val applyUnifiedSkin: (View) -> Unit,
    private val applyMotionPreferences: (View) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var searchRenderRunnable: Runnable? = null
    private lateinit var search: AutoCompleteTextView
    private lateinit var filters: LinearLayout
    private lateinit var countText: TextView
    private lateinit var gameList: LinearLayout
    private var selected: String
        get() = getSelected()
        set(value) = setSelected(value)
    private var sortMode: String
        get() = getSortMode()
        set(value) = setSortMode(value)
    private var activeDockTab: String = "HOME"

    private fun isDarkMode(): Boolean = darkMode()

    private fun makeText(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        createText(value, size, color, bold)

    fun dispose() {
        searchRenderRunnable?.let(mainHandler::removeCallbacks)
        searchRenderRunnable = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    fun create() {
        // MARCO RX6 visual system: midnight-blue surfaces with electric-blue accents.
        val silver = if (isDarkMode()) Color.rgb(226, 232, 242) else Color.rgb(43, 52, 68)
        val muted = gray
        val accentBlue = red
        val ink = bg
        val glass = panel
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = if (currentLanguage() == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            textDirection = if (currentLanguage() == "ar") View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
            setPadding(dp(12), dp(8), dp(12), dp(104))
            setBackgroundColor(ink)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safeAreas = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(dp(12), dp(8) + safeAreas.top, dp(12), dp(104) + safeAreas.bottom)
            insets
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(3), dp(2), dp(17))
        }
        val brand = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(makeText("MARCO RX6  /  OFFICIAL GAME HUB", 9f, if (isDarkMode()) Color.rgb(92, 174, 255) else Color.rgb(35, 126, 224), true).apply { letterSpacing = 0.08f })
        brand.addView(makeText("مكتبة الألعاب", 21f, silver, true).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.01f
        })
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        fun headerAction(iconRes: Int, label: String, action: () -> Unit) = ImageView(context).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageTintList = android.content.res.ColorStateList.valueOf(if (isDarkMode()) Color.rgb(190, 222, 255) else Color.rgb(35, 126, 224))
            setPadding(dp(13), dp(13), dp(13), dp(13))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (isDarkMode()) intArrayOf(Color.argb(205, 20, 58, 96), Color.argb(180, 9, 28, 52))
                else intArrayOf(Color.argb(220, 255, 255, 255), Color.argb(190, 218, 237, 255))).apply {
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), if (isDarkMode()) Color.rgb(48, 119, 190) else Color.rgb(173, 207, 239))
            }
            minimumWidth = dp(48); minimumHeight = dp(48)
            contentDescription = label
            isClickable = true
            isFocusable = true
            if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(92, 92, 181, 255)),
                null,
                GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(14).toFloat() }
            )
            setOnClickListener { action() }
        }
        header.addView(headerAction(if (isDarkMode()) R.drawable.ic_action_sun else R.drawable.ic_action_moon, tr(if (isDarkMode()) "الوضع الفاتح" else "الوضع الداكن")) { toggleDarkMode() },
            LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(8) })
        header.addView(headerAction(R.drawable.ic_action_settings, tr("الإعدادات")) { showAppearanceSettings() },
            LinearLayout.LayoutParams(dp(48), dp(48)))

        root.addView(header)


        val welcomePanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(35, 126, 224), Color.rgb(13, 61, 132))).apply {
                cornerRadius = dp(22).toFloat()
            }
            setPadding(dp(18), dp(17), dp(18), dp(17))
            elevation = dp(3).toFloat()
        }
        welcomePanel.addView(makeText(tr("YOUR NEXT GAME STARTS HERE"), 9f, Color.rgb(215, 235, 255), true).apply {
            letterSpacing = 0.12f
        })
        welcomePanel.addView(makeText(tr("اكتشف عالم الألعاب"), 21f, Color.WHITE, true).apply {
            setPadding(0, dp(5), 0, dp(3))
        })
        welcomePanel.addView(makeText(tr("تصفح مكتبتك واختر مغامرتك القادمة"), 11f, Color.rgb(224, 239, 255), false))
        root.addView(welcomePanel, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(15)
        })


        val searchWrap = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (isDarkMode()) intArrayOf(Color.argb(205, 23, 58, 91), Color.argb(185, 8, 24, 43))
                else intArrayOf(Color.argb(225, 255, 255, 255), Color.argb(195, 224, 239, 255))).apply {
                cornerRadius = dp(17).toFloat()
                setStroke(dp(1), if (isDarkMode()) Color.rgb(58, 111, 169) else Color.rgb(174, 205, 239))
            }
            setPadding(dp(14), 0, dp(14), 0)
            elevation = if (isDarkMode()) 0f else dp(2).toFloat()
        }
        searchWrap.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_action_search)
            imageTintList = android.content.res.ColorStateList.valueOf(if (isDarkMode()) silver else Color.rgb(73, 84, 105))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = tr("بحث")
        }, LinearLayout.LayoutParams(dp(24), dp(24)))
        search = AutoCompleteTextView(context).apply {
            hint = tr("ابحث عن الألعاب والمقالات...")
            textSize = 14f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setSingleLine(true)
            threshold = 1
            setTextColor(white)
            setHintTextColor(if (isDarkMode()) gray else Color.rgb(105, 115, 134))
            background = null
            setPadding(dp(10), 0, dp(6), 0)
            setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line,
                games().map { it.name }.distinct().take(80)))
            // Keep autocomplete suggestions aligned with the active glass theme instead
            // of Android's default opaque grey popup.
            setDropDownBackgroundDrawable(GradientDrawable().apply {
                setColor(if (isDarkMode()) Color.rgb(13, 34, 56) else Color.rgb(248, 251, 255))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), if (isDarkMode()) Color.rgb(58, 111, 169) else Color.rgb(174, 205, 239))
            })
            dropDownVerticalOffset = dp(5)
            setOnFocusChangeListener { _, focused ->
                if (focused && text.isNotEmpty()) showDropDown()
            }
        }
        searchWrap.addView(search, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(searchWrap, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(13) })

        val filterScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        filters = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf("ALL", "PS2", "PS3", "PS4", "BLOG", "FAVORITES", "WANT_TO_PLAY", "PLAYING", "COMPLETED").forEach { category ->
            val chip = TextView(context).apply {
                text = when (category) {
                    "ALL" -> tr("الكل")
                    "PS4" -> "PS4"
                    "PS3" -> "PS3"
                    "PS2" -> "PS2"
                    "BLOG" -> tr("المدونة")
                    "FAVORITES" -> tr("المفضلة")
                    "WANT_TO_PLAY" -> tr("أريد لعبها")
                    "PLAYING" -> tr("ألعبها حالياً")
                    "COMPLETED" -> tr("أنهيتها")
                    else -> category
                }
                tag = category
                textSize = 11f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                minimumHeight = dp(48)
                contentDescription = text.toString()
                setPadding(dp(16), dp(10), dp(16), dp(10))
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    if (isDarkMode()) intArrayOf(Color.argb(210, 21, 62, 101), Color.argb(192, 8, 27, 47))
                    else intArrayOf(Color.argb(228, 255, 255, 255), Color.argb(211, 222, 239, 255))
                ).apply {
                    cornerRadius = dp(18).toFloat()
                    setStroke(dp(1), if (isDarkMode()) Color.argb(145, 78, 151, 216) else Color.argb(155, 151, 195, 237))
                }
                setTextColor(muted)
                isClickable = true
                isFocusable = true
                minimumHeight = dp(56)
                contentDescription = text.toString()
                if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.argb(75, 82, 175, 255)),
                    null,
                    GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(18).toFloat() }
                )
                setOnClickListener { selected = category; renderGames() }
            }
            filters.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(7) })
        }
        root.addView(makeText(tr("اختر المنصة"), 11f, red, true).apply {
            setPadding(dp(2), dp(2), dp(2), dp(7))
        })
        filterScroll.addView(filters)
        root.addView(filterScroll, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(17) })

        val section = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titles = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(makeText("MARCO RX6  /  GAMES", 8f, accentBlue, true).apply { letterSpacing = 0.14f })
        titles.addView(makeText(tr("اكتشف الألعاب"), 21f, silver, true))
        section.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        countText = makeText("0 " + tr("لعبة"), 10f, muted, true)
        section.addView(countText)
        val sort = ImageView(context).apply {
            setImageResource(R.drawable.ic_action_sort)
            imageTintList = android.content.res.ColorStateList.valueOf(silver)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                if (isDarkMode()) intArrayOf(Color.argb(210, 21, 62, 101), Color.argb(192, 8, 27, 47))
                else intArrayOf(Color.argb(228, 255, 255, 255), Color.argb(211, 222, 239, 255))
            ).apply {
                cornerRadius = dp(13).toFloat()
                setStroke(dp(1), if (isDarkMode()) Color.argb(145, 78, 151, 216) else Color.argb(155, 151, 195, 237))
            }
            isClickable = true
            isFocusable = true
            if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(78, 82, 175, 255)),
                null,
                GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(13).toFloat() }
            )
            setPadding(dp(12), dp(7), dp(12), dp(7))
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            contentDescription = tr("ترتيب المقالات")
            setOnClickListener {
                sortMode = when (sortMode) { "LATEST" -> "NEW"; "NEW" -> "A-Z"; "A-Z" -> "FAVORITES"; else -> "LATEST" }
                renderGames()
                Toast.makeText(context, tr("الترتيب:") + " " + tr(when (sortMode) {
                    "LATEST" -> "الأحدث"; "NEW" -> "المضاف حديثاً"; "A-Z" -> "أبجدياً"; else -> "المفضلة"
                }), Toast.LENGTH_SHORT).apply {
                    setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, dp(72))
                }.show()
            }
        }
        section.addView(sort, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(9) })
        val advancedFilter = ImageView(context).apply {
            setImageResource(R.drawable.ic_action_filter)
            imageTintList = android.content.res.ColorStateList.valueOf(silver)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                if (isDarkMode()) intArrayOf(Color.argb(210, 21, 62, 101), Color.argb(192, 8, 27, 47))
                else intArrayOf(Color.argb(228, 255, 255, 255), Color.argb(211, 222, 239, 255))
            ).apply {
                cornerRadius = dp(13).toFloat()
                setStroke(dp(1), if (isDarkMode()) Color.argb(145, 78, 151, 216) else Color.argb(155, 151, 195, 237))
            }
            isClickable = true
            isFocusable = true
            if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(78, 82, 175, 255)),
                null,
                GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(13).toFloat() }
            )
            setPadding(dp(12), dp(8), dp(12), dp(8))
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            contentDescription = tr("الفلاتر المتقدمة")
            setOnClickListener { showAdvancedFilters() }
        }
        section.addView(advancedFilter, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(7) })
        root.addView(section, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        gameList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(18))
        }

        // Publish the newly created views to the host before the first render.
        // renderGames() reads these fields, so leaving them unwired causes a lateinit crash.
        setSearch(search)
        setFilters(filters)
        setCountText(countText)
        setGameList(gameList)

        root.addView(gameList, LinearLayout.LayoutParams(-1, -2))

        if (adminSession()) {
            val publish = TextView(context).apply {
                text = tr("نشر")
                setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_action_add, 0, 0, 0)
                compoundDrawablePadding = dp(7)
                if (android.os.Build.VERSION.SDK_INT >= 23) compoundDrawableTintList =
                    android.content.res.ColorStateList.valueOf(Color.WHITE)
                textSize = 11f
                setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.rgb(46, 145, 255), Color.rgb(20, 91, 190))).apply {
                    cornerRadius = dp(15).toFloat()
                    setStroke(dp(1), Color.argb(190, 135, 202, 255))
                }
                isClickable = true
                isFocusable = true
                if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.argb(100, 205, 235, 255)),
                    null,
                    GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(15).toFloat() }
                )
                setPadding(dp(18), dp(13), dp(18), dp(13))
                elevation = dp(3).toFloat()
                setOnClickListener { showGameDialog() }
            }
            root.addView(publish, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
        }

        applyTypography(root)
        val pageScroll = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            // Reserve room for the floating dock so the final game card and actions
            // remain reachable instead of being hidden underneath it.
            setPadding(0, 0, 0, dp(84))
            addView(root, ViewGroup.LayoutParams(-1, -2))
        }
        val shell = FrameLayout(context).apply {
            setBackgroundColor(ink)
            addView(pageScroll, FrameLayout.LayoutParams(-1, -1))
        }
        val dock = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = if (isDarkMode()) GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(218, 18, 55, 94), Color.argb(205, 10, 34, 61), Color.argb(190, 7, 20, 38))).apply {
                cornerRadius = dp(23).toFloat()
                setStroke(dp(1), Color.rgb(47, 117, 188))
            } else GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(225, 255, 255, 255), Color.argb(205, 226, 240, 255))).apply {
                cornerRadius = dp(23).toFloat()
                setStroke(dp(1), Color.rgb(173, 207, 239))
            }
            elevation = dp(14).toFloat()
            setPadding(dp(7), dp(7), dp(7), dp(7))
        }
        if (activeDockTab !in setOf("HOME", "FAVORITES", "DOWNLOADS", "PROFILE")) activeDockTab = "HOME"
        val dockItems = linkedMapOf<String, LinearLayout>()
        val dockLabels = linkedMapOf<String, String>()
        fun renderDockSelection() {
            dockItems.forEach { (tab, item) ->
                val active = tab == activeDockTab
                item.isSelected = active
                item.contentDescription = dockLabels[tab] ?: tab
                item.background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    if (active) {
                        if (isDarkMode()) intArrayOf(Color.rgb(39, 139, 231), Color.rgb(19, 83, 157))
                        else intArrayOf(Color.rgb(56, 150, 244), Color.rgb(31, 111, 205))
                    } else {
                        if (isDarkMode()) intArrayOf(Color.argb(70, 22, 65, 106), Color.argb(45, 8, 27, 47))
                        else intArrayOf(Color.argb(75, 255, 255, 255), Color.argb(45, 220, 238, 255))
                    }
                ).apply {
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), if (active) Color.argb(185, 135, 204, 255)
                    else Color.TRANSPARENT)
                }
                (item.getChildAt(0) as? ImageView)?.imageTintList =
                    android.content.res.ColorStateList.valueOf(
                        if (active) Color.WHITE
                        else if (isDarkMode()) Color.rgb(190, 211, 235) else Color.rgb(62, 91, 130)
                    )
                item.animate().cancel()
                item.scaleX = 1f
                item.scaleY = 1f
            }
        }
        fun dockItem(key: String, iconRes: Int, label: String, action: () -> Unit) {
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(5), dp(5), dp(5))
                minimumWidth = dp(48)
                minimumHeight = dp(48)
                isClickable = true
                isFocusable = true
                if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.argb(88, 93, 184, 255)),
                    null,
                    GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(15).toFloat() }
                )
                setOnClickListener {
                    activeDockTab = key
                    renderDockSelection()
                    action()
                }
            }
            item.addView(ImageView(context).apply {
                setImageResource(iconRes)
                imageTintList = android.content.res.ColorStateList.valueOf(
                    if (isDarkMode()) silver else Color.rgb(62, 91, 130)
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = label
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(25), dp(25)))
            dockItems[key] = item
            dockLabels[key] = label
            dock.addView(item, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(3); marginEnd = dp(3)
            })
        }
        dockItem("HOME", R.drawable.ic_nav_home, tr("الرئيسية")) {
            selected = "ALL"; renderGames(); pageScroll.smoothScrollTo(0, 0)
        }
        dockItem("FAVORITES", R.drawable.ic_nav_favorites, tr("المفضلة")) {
            selected = "FAVORITES"; renderGames(); pageScroll.smoothScrollTo(0, 0)
        }
        dockItem("DOWNLOADS", R.drawable.ic_nav_downloads, tr("التنزيلات")) { showDownloads() }
        dockItem("PROFILE", R.drawable.ic_nav_profile, tr("حسابي")) { showProfile() }
        renderDockSelection()

        val dockParams = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            leftMargin = dp(16); rightMargin = dp(16); bottomMargin = dp(12)
        }
        shell.addView(dock, dockParams)
        // Apply the shared glass/ripple interaction layer after every screen control,
        // including the floating navigation dock, has been assembled.
        applyUnifiedSkin(shell)
        ViewCompat.setOnApplyWindowInsetsListener(shell) { _, insets ->
            val safeAreas = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            dockParams.bottomMargin = dp(12) + safeAreas.bottom
            dock.layoutParams = dockParams
            pageScroll.setPadding(0, safeAreas.top, 0, dp(84) + safeAreas.bottom)
            insets
        }
        setContentView(shell)
        applyMotionPreferences(shell)
        ViewCompat.requestApplyInsets(shell)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchRenderRunnable?.let { mainHandler.removeCallbacks(it) }
                searchRenderRunnable = Runnable { renderGames() }.also {
                    mainHandler.postDelayed(it, 180L)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        renderGames()
    }
}
