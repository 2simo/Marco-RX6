package com.marcorx6.library.ui

import com.marcorx6.library.ui.activity.MainActivity

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
    private val activity: MainActivity,
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
    private val showNotificationSettings: () -> Unit,
    private val showAppearanceSettings: () -> Unit,
    private val showAdvancedFilters: () -> Unit,
    private val showGameDialog: () -> Unit,
    private val showProfile: () -> Unit,
    private val showSideMenu: () -> Unit,
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

    private fun isDarkMode(): Boolean = darkMode()

    private fun makeText(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        createText(value, size, color, bold)

    fun dispose() {
        searchRenderRunnable?.let(mainHandler::removeCallbacks)
        searchRenderRunnable = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    fun create() {
        // RX6 GAME HUB: unified midnight-blue surfaces, electric-blue accents and clear game-first hierarchy.
        val silver = if (isDarkMode()) Color.rgb(226, 232, 242) else Color.rgb(43, 52, 68)
        val muted = gray
        val violet = red
        val ink = bg
        val glass = panel
        val root = LinearLayout(activity).apply {
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

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(3), dp(2), dp(17))
        }
        val brand = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(makeText("MARCO RX6  /  OFFICIAL GAME HUB", 9f, if (isDarkMode()) Color.rgb(111, 190, 255) else Color.rgb(30, 116, 204), true).apply { letterSpacing = 0.08f })
        brand.addView(makeText("مكتبة الألعاب", 21f, silver, true).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.01f
        })
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        fun headerAction(icon: String, label: String, action: () -> Unit) = TextView(activity).apply {
            text = icon
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(if (isDarkMode()) Color.rgb(196, 222, 250) else Color.rgb(35, 104, 169))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (isDarkMode()) intArrayOf(Color.rgb(24, 42, 66), Color.rgb(10, 20, 35))
                else intArrayOf(Color.WHITE, Color.rgb(230, 239, 250))).apply {
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), if (isDarkMode()) Color.rgb(47, 91, 139) else Color.rgb(191, 211, 235))
            }
            minWidth = dp(43); minHeight = dp(43)
            contentDescription = label
            setOnClickListener { action() }
        }
        header.addView(headerAction("♧", "الإشعارات") { showNotificationSettings() },
            LinearLayout.LayoutParams(dp(43), dp(43)).apply { marginEnd = dp(8) })
        header.addView(headerAction("⚙", "الإعدادات") { showAppearanceSettings() },
            LinearLayout.LayoutParams(dp(43), dp(43)).apply { marginEnd = dp(8) })
        header.addView(headerAction("♛", "دخول المسؤول / القائمة") { showSideMenu() },
            LinearLayout.LayoutParams(dp(43), dp(43)))
        root.addView(header)

        // Compact game-first layout: the reference has no promotional hero banner.

        val searchWrap = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (isDarkMode()) intArrayOf(Color.rgb(39, 46, 63), Color.rgb(17, 21, 33))
                else intArrayOf(Color.rgb(255, 255, 255), Color.rgb(232, 237, 247))).apply {
                cornerRadius = dp(17).toFloat()
                setStroke(dp(1), if (isDarkMode()) Color.rgb(75, 87, 112) else Color.rgb(184, 193, 211))
            }
            setPadding(dp(14), 0, dp(14), 0)
            elevation = if (isDarkMode()) 0f else dp(2).toFloat()
        }
        searchWrap.addView(makeText("⌕", 26f, if (isDarkMode()) silver else Color.rgb(73, 84, 105), true))
        search = AutoCompleteTextView(activity).apply {
            hint = tr("ابحث عن الألعاب والمقالات...")
            textSize = 14f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setSingleLine(true)
            threshold = 1
            setTextColor(white)
            setHintTextColor(if (isDarkMode()) gray else Color.rgb(105, 115, 134))
            background = null
            setPadding(dp(10), 0, dp(6), 0)
            setAdapter(ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line,
                games().map { it.name }.distinct().take(80)))
            setOnFocusChangeListener { _, focused ->
                if (focused && text.isNotEmpty()) showDropDown()
            }
        }
        searchWrap.addView(search, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(searchWrap, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(13) })

        val filterScroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        filters = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf("ALL", "PS4", "PS3", "PS2", "BLOG", "FAVORITES", "WANT_TO_PLAY", "PLAYING", "COMPLETED").forEach { category ->
            val chip = TextView(activity).apply {
                text = when (category) {
                    "ALL" -> tr("الكل")
                    "PS4" -> "PS4"
                    "PS3" -> "PS3"
                    "PS2" -> "PS2"
                    "BLOG" -> tr("المدونة")
                    "FAVORITES" -> tr("المفضلة")
                    "WANT_TO_PLAY" -> "أريد لعبها"
                    "PLAYING" -> "ألعبها حالياً"
                    "COMPLETED" -> "أنهيتها"
                    else -> category
                }
                tag = category
                textSize = 11f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                minHeight = dp(44)
                setPadding(dp(16), dp(10), dp(16), dp(10))
                background = rounded(glass, 18)
                setTextColor(muted)
                setOnClickListener { selected = category; renderGames() }
            }
            filters.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(7) })
        }
        root.addView(makeText("اختر المنصة", 11f, red, true).apply {
            setPadding(dp(2), dp(2), dp(2), dp(7))
        })
        filterScroll.addView(filters)
        root.addView(filterScroll, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(17) })

        val section = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titles = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(makeText("MARCO RX6  /  GAMES", 8f, violet, true).apply { letterSpacing = 0.14f })
        titles.addView(makeText(tr("اكتشف الألعاب"), 21f, silver, true))
        section.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        countText = makeText("0 " + tr("مقال"), 10f, muted, true)
        section.addView(countText)
        val sort = TextView(activity).apply {
            text = "⇅"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(silver)
            background = rounded(glass, 13)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            minWidth = dp(48)
            minHeight = dp(48)
            contentDescription = tr("ترتيب المقالات")
            setOnClickListener {
                sortMode = when (sortMode) { "LATEST" -> "NEW"; "NEW" -> "A-Z"; "A-Z" -> "FAVORITES"; else -> "LATEST" }
                renderGames()
                Toast.makeText(activity, "الترتيب: " + when (sortMode) {
                    "LATEST" -> "الأحدث"; "NEW" -> "المضاف حديثاً"; "A-Z" -> "أبجدياً"; else -> "المفضلة"
                }, Toast.LENGTH_SHORT).show()
            }
        }
        section.addView(sort, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(9) })
        val advancedFilter = TextView(activity).apply {
            text = "⚙"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(silver)
            background = rounded(glass, 13)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            minWidth = dp(48)
            minHeight = dp(48)
            contentDescription = "الفلاتر المتقدمة"
            setOnClickListener { showAdvancedFilters() }
        }
        section.addView(advancedFilter, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(7) })
        root.addView(section, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        gameList = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(18))
        }

        // Publish the newly created views to MainActivity before the first render.
        // renderGames() reads these fields, so leaving them unwired causes a lateinit crash.
        setSearch(search)
        setFilters(filters)
        setCountText(countText)
        setGameList(gameList)

        root.addView(gameList, LinearLayout.LayoutParams(-1, -2))

        if (adminSession()) {
            val publish = TextView(activity).apply {
                text = tr("＋  نشر")
                textSize = 11f
                setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.rgb(118, 127, 151), Color.rgb(39, 126, 210))).apply {
                    cornerRadius = dp(15).toFloat()
                }
                setPadding(dp(18), dp(13), dp(18), dp(13))
                setOnClickListener { showGameDialog() }
            }
            root.addView(publish, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
        }

        applyTypography(root)
        applyUnifiedSkin(root)
        val pageScroll = ScrollView(activity).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            addView(root, ViewGroup.LayoutParams(-1, -2))
        }
        val shell = FrameLayout(activity).apply {
            setBackgroundColor(ink)
            addView(pageScroll, FrameLayout.LayoutParams(-1, -1))
        }
        val dock = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = if (isDarkMode()) GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(19, 39, 65), Color.rgb(10, 23, 41), Color.rgb(7, 15, 28))).apply {
                cornerRadius = dp(23).toFloat()
                setStroke(dp(1), Color.rgb(43, 105, 164))
            } else GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(255, 255, 255), Color.rgb(241, 244, 250))).apply {
                cornerRadius = dp(23).toFloat()
                setStroke(dp(1), Color.rgb(190, 199, 214))
            }
            elevation = dp(14).toFloat()
            setPadding(dp(7), dp(7), dp(7), dp(7))
        }
        fun dockItem(icon: String, label: String, action: () -> Unit) {
            val item = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(5), dp(5), dp(5))
                background = rounded(if (isDarkMode()) Color.rgb(22, 43, 68) else Color.rgb(232, 237, 246), 15)
                setOnClickListener { action() }
            }
            item.addView(makeText(icon, 15f, if (isDarkMode()) silver else Color.rgb(43, 123, 194), true).apply {
                gravity = Gravity.CENTER
            })
            item.addView(makeText(label, 10f, if (isDarkMode()) silver else Color.rgb(43, 51, 68), true).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(dp(2), dp(3), dp(2), dp(2))
            })
            dock.addView(item, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(3); marginEnd = dp(3)
            })
        }
        dockItem("⌂", tr("الرئيسية")) { selected = "ALL"; renderGames(); pageScroll.smoothScrollTo(0, 0) }
        dockItem("♡", tr("المفضلة")) { selected = "FAVORITES"; renderGames(); pageScroll.smoothScrollTo(0, 0) }
        dockItem("◷", "متابعتي") { selected = "WANT_TO_PLAY"; renderGames(); pageScroll.smoothScrollTo(0, 0) }
        dockItem("⌕", "بحث") { search.requestFocus(); pageScroll.smoothScrollTo(0, 0); (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT) }
        dockItem("♙", tr("حسابي")) { showProfile() }
        val dockParams = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            leftMargin = dp(16); rightMargin = dp(16); bottomMargin = dp(12)
        }
        shell.addView(dock, dockParams)
        ViewCompat.setOnApplyWindowInsetsListener(shell) { _, insets ->
            val safeAreas = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout()
            )
            dockParams.bottomMargin = dp(12) + safeAreas.bottom
            dock.layoutParams = dockParams
            insets
        }
        activity.setContentView(shell)
        applyMotionPreferences(shell)
        root.requestApplyInsets()
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
