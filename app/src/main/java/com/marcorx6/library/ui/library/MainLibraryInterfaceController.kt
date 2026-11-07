package com.marcorx6.library.ui.library

import com.marcorx6.library.R
import com.marcorx6.library.data.Game
import com.marcorx6.library.data.GameLibraryManager

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
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
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
    private val loadCover: (ImageView, String, String) -> Unit,
    private val showDetails: (Game) -> Unit,
    private val applyTypography: (View) -> Unit,
    private val applyUnifiedSkin: (View) -> Unit,
    private val applyMotionPreferences: (View) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var searchRenderRunnable: Runnable? = null
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

    private fun loadCoverWhenVisible(image: ImageView, localPath: String, imageUrl: String) {
        var requested = false
        var observer: ViewTreeObserver? = null
        lateinit var checkVisibility: () -> Unit
        val scrollListener = ViewTreeObserver.OnScrollChangedListener { checkVisibility() }

        fun removeObserver() {
            observer?.takeIf { it.isAlive }?.removeOnScrollChangedListener(scrollListener)
            observer = null
        }

        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                observer = view.viewTreeObserver
                if (observer?.isAlive == true) observer?.addOnScrollChangedListener(scrollListener)
                view.post { checkVisibility() }
            }

            override fun onViewDetachedFromWindow(view: View) {
                removeObserver()
            }
        }

        checkVisibility = {
            if (!requested && image.isAttachedToWindow) {
                val visible = Rect()
                if (image.getGlobalVisibleRect(visible) && visible.width() > 0 && visible.height() > 0) {
                    requested = true
                    removeObserver()
                    image.removeOnAttachStateChangeListener(attachListener)
                    loadCover(image, localPath, imageUrl)
                }
            }
        }

        image.addOnAttachStateChangeListener(attachListener)
        if (image.isAttachedToWindow) {
            observer = image.viewTreeObserver
            if (observer?.isAlive == true) observer?.addOnScrollChangedListener(scrollListener)
            image.post { checkVisibility() }
        }
    }

    fun dispose() {
        searchRenderRunnable?.let(mainHandler::removeCallbacks)
        searchRenderRunnable = null
        mainHandler.removeCallbacksAndMessages(null)
    }


    private fun showSmartSearch() {
        val ink = bg
        val accent = Color.rgb(190, 198, 210)
        val searchBaseTop = dp(18)
        val searchBaseBottom = dp(18)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = if (currentLanguage() == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            textDirection = if (currentLanguage() == "ar") View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
            setPadding(dp(16), searchBaseTop, dp(16), searchBaseBottom)
            setBackgroundColor(ink)
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleBlock = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        titleBlock.addView(makeText(tr("MARCO  /  البحث الذكي"), 9f, accent, true))
        titleBlock.addView(makeText(tr("البحث الذكي"), 24f, white, true))
        header.addView(titleBlock, LinearLayout.LayoutParams(0, -2, 1f))
        val back = TextView(context).apply {
            text = tr("رجوع")
            textSize = 12f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(dp(15), dp(10), dp(15), dp(10))
            background = rounded(panel, 16)
            setOnClickListener { create() }
        }
        header.addView(back)
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })

        val searchBox = EditText(context).apply {
            hint = tr("اكتب اسم اللعبة أو المنصة أو النوع أو السنة...")
            textSize = 14f
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(gray)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_action_search, 0, 0, 0)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(accent)
            compoundDrawablePadding = dp(10)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        root.addView(searchBox, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(12) })
        val count = makeText(tr("ابدأ بكتابة اسم اللعبة للبحث"), 11f, gray, false)
        root.addView(count, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        val results = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        root.addView(results, LinearLayout.LayoutParams(-1, -2))

        fun renderResults(query: String) {
            results.removeAllViews()
            val matches = GameLibraryManager.filterAndSort(games(), "ALL", query, "", "", "LATEST")
            count.text = if (query.isBlank()) tr("جميع الألعاب") + " • " + matches.size
                else matches.size.toString() + " " + tr("نتيجة")
            if (matches.isEmpty()) {
                results.addView(makeText(tr("لم يتم العثور على ألعاب مطابقة"), 14f, gray, true).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(12), dp(48), dp(12), dp(48))
                })
                return
            }
            matches.forEach { game ->
                val slot = FrameLayout(context).apply { minimumHeight = dp(110) }
                val buildCard = {
                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        if (isDarkMode()) intArrayOf(Color.argb(225, 35, 38, 45), Color.argb(210, 15, 17, 22))
                        else intArrayOf(Color.argb(245, 255, 255, 255), Color.argb(230, 222, 226, 232))).apply {
                        cornerRadius = dp(17).toFloat()
                        setStroke(dp(1), if (isDarkMode()) Color.argb(150, 130, 138, 151) else Color.argb(160, 160, 168, 181))
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { showDetails(game) }
                }
                val cover = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = rounded(ink, 12)
                }
                if (game.coverUrl.isNotBlank() || game.coverLocalPath.isNotBlank()) {
                    loadCoverWhenVisible(cover, game.coverLocalPath, game.coverUrl)
                } else {
                    cover.setImageResource(android.R.drawable.ic_menu_gallery)
                    cover.scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
                val coverFrame = FrameLayout(context).apply {
                    background = rounded(ink, 12)
                    clipToOutline = true
                    addView(cover, FrameLayout.LayoutParams(-1, -1))
                }
                val languageLabel = GameLanguageBadge.resolve(game)
                if (GameLibraryManager.normalizePlatform(game.platform) != null && languageLabel.isNotBlank()) {
                    val languageBadge = TextView(context).apply {
                        text = languageLabel
                        textSize = 8f
                        setTextColor(Color.WHITE)
                        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                        gravity = Gravity.CENTER
                        setPadding(dp(6), dp(3), dp(6), dp(3))
                        background = GradientDrawable().apply {
                            setColor(Color.argb(224, 35, 38, 45))
                            cornerRadius = dp(7).toFloat()
                            setStroke(dp(1), Color.argb(210, 145, 153, 166))
                        }
                    }
                    coverFrame.addView(
                        languageBadge,
                        FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
                            rightMargin = dp(4)
                            bottomMargin = dp(4)
                        }
                    )
                }
                card.addView(coverFrame, LinearLayout.LayoutParams(dp(68), dp(88)))
                val details = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, dp(6), 0)
                }
                details.addView(makeText(game.name, 14f, white, true).apply { maxLines = 2 })
                val metadata = listOf(game.platform, game.genre, game.releaseYear)
                    .filter { it.isNotBlank() }.joinToString("  •  ")
                details.addView(makeText(metadata, 10f, gray, false).apply {
                    maxLines = 2
                    setPadding(0, dp(5), 0, 0)
                })
                card.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
                slot.addView(card, FrameLayout.LayoutParams(-1, -2))
                }
                runWhenVisible(slot) { buildCard() }
                results.addView(slot, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(9)
                })
            }
        }
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Debounce rapid typing so filtering and rebuilding result cards happen
                // only after the user pauses, rather than once for every keystroke.
                searchRenderRunnable?.let(mainHandler::removeCallbacks)
                val query = s?.toString().orEmpty()
                val pendingRender = Runnable {
                    if (searchBox.isAttachedToWindow) renderResults(query)
                }
                searchRenderRunnable = pendingRender
                mainHandler.postDelayed(pendingRender, 180L)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        renderResults("")
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(root, ViewGroup.LayoutParams(-1, -2))
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val safeTop = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.statusBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            ).top
            val safeBottom = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.navigationBars() or
                    androidx.core.view.WindowInsetsCompat.Type.mandatorySystemGestures()
            ).bottom
            root.setPadding(dp(16), searchBaseTop + safeTop, dp(16), searchBaseBottom + safeBottom)
            insets
        }
        applyTypography(scroll)
        setContentView(scroll)
        androidx.core.view.ViewCompat.requestApplyInsets(scroll)
        applyUnifiedSkin(scroll)
        applyMotionPreferences(scroll)
        searchBox.requestFocus()
    }

    private fun showFullScreenSections() {
        val ink = bg
        val silver = if (isDarkMode()) Color.rgb(225, 228, 234) else Color.rgb(43, 47, 55)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = if (currentLanguage() == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            setPadding(dp(16), dp(12), dp(16), dp(20))
            setBackgroundColor(ink)
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = TextView(context).apply {
            text = tr("رجوع")
            textSize = 13f
            setTextColor(silver)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(11), dp(16), dp(11))
            background = rounded(panel, 14)
            isClickable = true
            isFocusable = true
            setOnClickListener { create() }
        }
        header.addView(back)
        header.addView(makeText(tr("الأقسام"), 23f, silver, true), LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(14)
        })
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(22) })
        root.addView(makeText(tr("اختر القسم الذي تريد تصفحه"), 13f, gray, false),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val available = listOf(
            "PS4" to "PS4",
            "PS3" to "PS3",
            "PS2" to "PS2",
            "PS3_ARABIC" to tr("PS3 Arabic"),
            "FAVORITES" to tr("المفضلة")
        ).mapNotNull { (key, label) ->
            val count = GameLibraryManager.filterAndSort(games(), key, "", "", "", sortMode).size
            if (count > 0) Triple(key, label, count) else null
        }
        val cards = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        available.forEach { (key, label, count) ->
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(17), dp(17), dp(17), dp(17))
                background = rounded(panel, 18).apply {
                    setStroke(dp(1), if (isDarkMode()) Color.argb(135, 130, 138, 151) else Color.argb(145, 160, 168, 181))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { showFullScreenCategory(key, label) }
            }
            val info = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            info.addView(makeText(label, 17f, silver, true))
            info.addView(makeText("$count " + tr("لعبة"), 11f, gray, false).apply {
                setPadding(0, dp(5), 0, 0)
            })
            item.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            item.addView(makeText("‹", 27f, silver, true))
            cards.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
        }
        if (available.isEmpty()) {
            cards.addView(makeText(tr("لا توجد أقسام تحتوي على ألعاب حالياً"), 14f, gray, false).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(45), dp(12), dp(45))
            })
        }
        root.addView(cards, LinearLayout.LayoutParams(-1, -2))
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(root, ViewGroup.LayoutParams(-1, -2))
        }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val safeTop = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()).top
            val safeBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.mandatorySystemGestures()).bottom
            root.setPadding(dp(16), dp(12) + safeTop, dp(16), dp(20) + safeBottom)
            insets
        }
        applyTypography(scroll)
        setContentView(scroll)
        ViewCompat.requestApplyInsets(scroll)
        applyUnifiedSkin(scroll)
        applyMotionPreferences(scroll)
    }

    private fun showFullScreenCategory(category: String, title: String) {
        selected = category
        val ink = bg
        val silver = if (isDarkMode()) Color.rgb(225, 228, 234) else Color.rgb(43, 47, 55)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = if (currentLanguage() == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            setPadding(dp(14), dp(10), dp(14), dp(18))
            setBackgroundColor(ink)
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = TextView(context).apply {
            text = tr("رجوع إلى الأقسام")
            textSize = 12f
            setTextColor(silver)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = rounded(panel, 14)
            isClickable = true
            isFocusable = true
            setOnClickListener { selected = "ALL"; showFullScreenSections() }
        }
        header.addView(back)
        header.addView(makeText(title, 20f, silver, true), LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(12)
        })
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(13) })

        val sectionFilters = LinearLayout(context).apply { visibility = View.GONE }
        val sectionCount = makeText("0 " + tr("لعبة"), 11f, gray, true)
        val sectionGames = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(18))
        }
        setFilters(sectionFilters)
        setCountText(sectionCount)
        setGameList(sectionGames)
        renderGames()
        root.addView(sectionCount, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(8)
        })
        root.addView(sectionGames, LinearLayout.LayoutParams(-1, -2))
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            addView(root, ViewGroup.LayoutParams(-1, -2))
        }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val safeTop = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()).top
            val safeBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.mandatorySystemGestures()).bottom
            root.setPadding(dp(14), dp(10) + safeTop, dp(14), dp(18) + safeBottom)
            insets
        }
        applyTypography(scroll)
        setContentView(scroll)
        ViewCompat.requestApplyInsets(scroll)
        applyUnifiedSkin(scroll)
        applyMotionPreferences(scroll)
    }

    fun create() {
        // Blog posts are included in ALL; normalize any previously saved Blog filter.
        if (selected in setOf("BLOG", "PS2_ARABIC", "PS2_PORT_PS4", "PS2_PORT_PS3")) selected = "ALL"
        activeDockTab = "HOME"
        // MARCO visual system: charcoal surfaces with brushed-silver accents.
        val silver = if (isDarkMode()) Color.rgb(225, 228, 234) else Color.rgb(43, 47, 55)
        val muted = gray
        val silverAccent = red
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
            val safeTop = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            ).top
            val safeBottom = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or
                    WindowInsetsCompat.Type.mandatorySystemGestures()
            ).bottom
            view.setPadding(dp(12), dp(8) + safeTop, dp(12), dp(104) + safeBottom)
            insets
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(3), dp(2), dp(17))
        }
        val brand = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(makeText(tr("MARCO  /  مركز الألعاب الرسمي"), 9f, if (isDarkMode()) Color.rgb(190, 198, 210) else Color.rgb(130, 138, 151), true).apply { letterSpacing = 0.08f })
        brand.addView(makeText("مكتبة الألعاب", 21f, silver, true).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.01f
        })
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        fun headerAction(iconRes: Int, label: String, action: () -> Unit) = ImageView(context).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageTintList = android.content.res.ColorStateList.valueOf(if (isDarkMode()) Color.rgb(225, 228, 234) else Color.rgb(130, 138, 151))
            setPadding(dp(13), dp(13), dp(13), dp(13))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (isDarkMode()) intArrayOf(Color.argb(205, 34, 37, 44), Color.argb(180, 15, 17, 22))
                else intArrayOf(Color.argb(220, 255, 255, 255), Color.argb(190, 222, 226, 232))).apply {
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), if (isDarkMode()) Color.rgb(130, 138, 151) else Color.rgb(160, 168, 181))
            }
            minimumWidth = dp(48); minimumHeight = dp(48)
            contentDescription = label
            isClickable = true
            isFocusable = true
            if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(92, 190, 198, 210)),
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


        // Featured cover-flow sits above platform filters and uses only real library entries.
        val popularByPlatform = listOf("PS4", "PS3", "PS2").flatMap { platform ->
            games().filter { GameLibraryManager.normalizePlatform(it.platform) == platform }
                .sortedWith(compareByDescending<Game> { it.isFavorite }.thenBy { it.name.lowercase() })
                .take(3)
        }
        if (popularByPlatform.isNotEmpty()) {
            val popularHeader = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(8), dp(2), dp(10))
            }
            popularHeader.addView(makeText(tr("الألعاب الأكثر شعبية"), 20f, silver, true).apply {
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
            })
            root.addView(popularHeader, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(2)
            })

            val popularScroll = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                clipChildren = false
                clipToPadding = false
                overScrollMode = View.OVER_SCROLL_NEVER
                setPadding(dp(12), dp(8), dp(12), dp(12))
            }
            val popularTrack = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val popularCards = mutableListOf<View>()
            popularByPlatform.forEachIndexed { index, game ->
                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(dp(7), dp(7), dp(7), dp(9))
                    background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        intArrayOf(Color.argb(150, 37, 40, 47), Color.argb(90, 16, 18, 23))).apply {
                        cornerRadius = dp(20).toFloat()
                        setStroke(dp(1), Color.argb(110, 145, 153, 166))
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { showDetails(game) }
                }
                val cover = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = rounded(ink, 15)
                    clipToOutline = true
                }
                if (game.coverUrl.isNotBlank() || game.coverLocalPath.isNotBlank()) {
                    loadCover(cover, game.coverLocalPath, game.coverUrl)
                } else {
                    cover.setImageResource(android.R.drawable.ic_menu_gallery)
                    cover.scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
                val coverFrame = FrameLayout(context).apply {
                    background = rounded(ink, 15)
                    clipToOutline = true
                    addView(cover, FrameLayout.LayoutParams(-1, -1))
                }
                val languageLabel = GameLanguageBadge.resolve(game)
                if (languageLabel.isNotBlank()) {
                    val languageBadge = TextView(context).apply {
                        text = languageLabel
                        textSize = 11f
                        setTextColor(Color.WHITE)
                        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                        gravity = Gravity.CENTER
                        setPadding(dp(12), dp(7), dp(12), dp(7))
                        background = GradientDrawable().apply {
                            setColor(Color.argb(232, 35, 38, 45))
                            cornerRadius = dp(10).toFloat()
                            setStroke(dp(1), Color.argb(225, 165, 175, 190))
                        }
                        elevation = dp(5).toFloat()
                    }
                    coverFrame.addView(
                        languageBadge,
                        FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                            bottomMargin = dp(12)
                        }
                    )
                }
                card.addView(coverFrame, LinearLayout.LayoutParams(dp(218), dp(276)))
                card.addView(makeText(game.name, 13f, silver, true).apply {
                    gravity = Gravity.CENTER
                    maxLines = 2
                    setPadding(dp(5), dp(8), dp(5), dp(2))
                }, LinearLayout.LayoutParams(dp(218), -2))
                card.addView(makeText(GameLibraryManager.normalizePlatform(game.platform) ?: game.platform,
                    10f, muted, true).apply { gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(-1, -2))
                popularTrack.addView(card, LinearLayout.LayoutParams(dp(224), -2).apply {
                    // Keep cards separated; the centered card is the primary featured game.
                    marginStart = dp(6)
                    marginEnd = dp(6)
                })
                popularCards.add(card)
            }
            popularScroll.addView(popularTrack)
            var popularSnapRunnable: Runnable? = null
            fun updatePopularFocus() {
                val viewportCenter = popularScroll.scrollX +
                    (popularScroll.width / 2f) - popularScroll.paddingLeft
                val focusRange = (popularScroll.width * 0.72f).coerceAtLeast(1f)
                popularCards.forEach { card ->
                    val cardCenter = card.left + card.width / 2f
                    val distance = kotlin.math.abs(viewportCenter - cardCenter)
                    val focus = (1f - distance / focusRange).coerceIn(0f, 1f)
                    card.alpha = 0.68f + (focus * 0.32f)
                    val scale = 0.88f + (focus * 0.12f)
                    card.scaleX = scale
                    card.scaleY = scale
                    card.translationZ = dp((focus * 10f).toInt()).toFloat()
                }
            }
            fun snapPopularToNearest() {
                if (popularCards.isEmpty() || popularScroll.width == 0) return
                val viewportCenter = popularScroll.scrollX +
                    (popularScroll.width / 2f) - popularScroll.paddingLeft
                val nearest = popularCards.minByOrNull { card ->
                    kotlin.math.abs((card.left + card.width / 2f) - viewportCenter)
                } ?: return
                val target = nearest.left + nearest.width / 2 -
                    (popularScroll.width / 2 - popularScroll.paddingLeft)
                val maxScroll = (popularTrack.width + popularScroll.paddingLeft +
                    popularScroll.paddingRight - popularScroll.width).coerceAtLeast(0)
                popularScroll.smoothScrollTo(target.coerceIn(0, maxScroll), 0)
            }
            popularScroll.setOnScrollChangeListener { _, _, _, _, _ ->
                updatePopularFocus()
                popularSnapRunnable?.let(mainHandler::removeCallbacks)
                val pendingSnap = Runnable { snapPopularToNearest() }
                popularSnapRunnable = pendingSnap
                // Wait until the finger/fling stops, then settle the nearest cover exactly in the center.
                mainHandler.postDelayed(pendingSnap, 150L)
            }
            root.addView(popularScroll, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
            })
            popularScroll.post {
                if (popularCards.isEmpty()) return@post
                val sideInset = ((popularScroll.width - popularCards.first().width) / 2)
                    .coerceAtLeast(dp(12))
                popularScroll.setPadding(sideInset, dp(8), sideInset, dp(12))
                // Start on a middle entry so neighboring covers are visible on BOTH sides.
                val initial = popularCards[popularCards.size / 2]
                val initialTarget = initial.left + initial.width / 2 -
                    (popularScroll.width / 2 - popularScroll.paddingLeft)
                val maxScroll = (popularTrack.width + popularScroll.paddingLeft +
                    popularScroll.paddingRight - popularScroll.width).coerceAtLeast(0)
                popularScroll.scrollTo(initialTarget.coerceIn(0, maxScroll), 0)
                updatePopularFocus()
            }
        }

        val filterScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        filters = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf("ALL", "PS4", "PS3", "PS2", "PS3_ARABIC", "FAVORITES").forEach { category ->
            val chip = TextView(context).apply {
                text = when (category) {
                    "ALL" -> tr("الكل")
                    "PS4" -> "PS4"
                    "PS3" -> "PS3"
                    "PS2" -> "PS2"
                    "PS3_ARABIC" -> tr("PS3 Arabic")
                    "FAVORITES" -> tr("المفضلة")
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
                    if (isDarkMode()) intArrayOf(Color.argb(210, 35, 38, 45), Color.argb(192, 15, 17, 22))
                    else intArrayOf(Color.argb(228, 255, 255, 255), Color.argb(211, 222, 226, 232))
                ).apply {
                    cornerRadius = dp(18).toFloat()
                    setStroke(dp(1), if (isDarkMode()) Color.argb(145, 130, 138, 151) else Color.argb(155, 160, 168, 181))
                }
                setTextColor(muted)
                isClickable = true
                isFocusable = true
                minimumHeight = dp(56)
                contentDescription = text.toString()
                if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.argb(75, 190, 198, 210)),
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
        titles.addView(makeText(tr("MARCO  /  الألعاب"), 8f, silverAccent, true).apply { letterSpacing = 0.14f })
        titles.addView(makeText(tr("اكتشف الألعاب"), 21f, silver, true))
        section.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        countText = makeText("0 " + tr("لعبة"), 10f, muted, true)
        section.addView(countText)
        if (selected == "ALL") {
            val openSections = TextView(context).apply {
                text = "▦  " + tr("الأقسام")
                textSize = 11f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(silver)
                setPadding(dp(11), dp(9), dp(11), dp(9))
                background = rounded(glass, 13).apply {
                    setStroke(dp(1), if (isDarkMode()) Color.argb(155, 130, 138, 151) else Color.argb(165, 160, 168, 181))
                }
                isClickable = true
                isFocusable = true
                contentDescription = tr("عرض جميع الأقسام بملء الشاشة")
                setOnClickListener { showFullScreenSections() }
            }
            section.addView(openSections, LinearLayout.LayoutParams(-2, dp(44)).apply {
                marginStart = dp(8)
            })
        }
        val sort = ImageView(context).apply {
            setImageResource(R.drawable.ic_action_sort)
            imageTintList = android.content.res.ColorStateList.valueOf(silver)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                if (isDarkMode()) intArrayOf(Color.argb(210, 35, 38, 45), Color.argb(192, 15, 17, 22))
                else intArrayOf(Color.argb(228, 255, 255, 255), Color.argb(211, 222, 226, 232))
            ).apply {
                cornerRadius = dp(13).toFloat()
                setStroke(dp(1), if (isDarkMode()) Color.argb(145, 130, 138, 151) else Color.argb(155, 160, 168, 181))
            }
            isClickable = true
            isFocusable = true
            if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(78, 190, 198, 210)),
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
                if (isDarkMode()) intArrayOf(Color.argb(210, 35, 38, 45), Color.argb(192, 15, 17, 22))
                else intArrayOf(Color.argb(228, 255, 255, 255), Color.argb(211, 222, 226, 232))
            ).apply {
                cornerRadius = dp(13).toFloat()
                setStroke(dp(1), if (isDarkMode()) Color.argb(145, 130, 138, 151) else Color.argb(155, 160, 168, 181))
            }
            isClickable = true
            isFocusable = true
            if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(78, 190, 198, 210)),
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
                setTextColor(Color.rgb(20, 23, 29))
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.rgb(190, 198, 210), Color.rgb(105, 113, 126))).apply {
                    cornerRadius = dp(15).toFloat()
                    setStroke(dp(1), Color.argb(190, 210, 216, 225))
                }
                isClickable = true
                isFocusable = true
                if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.argb(100, 225, 230, 237)),
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
                intArrayOf(Color.argb(232, 35, 38, 45), Color.argb(220, 18, 20, 25), Color.argb(210, 8, 9, 12))).apply {
                cornerRadius = dp(23).toFloat()
                setStroke(dp(1), Color.rgb(130, 138, 151))
            } else GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(245, 245, 247, 250), Color.argb(230, 215, 220, 228), Color.argb(220, 190, 198, 210))).apply {
                cornerRadius = dp(23).toFloat()
                setStroke(dp(1), Color.rgb(160, 168, 181))
            }
            elevation = dp(14).toFloat()
            setPadding(dp(7), dp(7), dp(7), dp(7))
        }
        if (activeDockTab !in setOf("HOME", "SEARCH", "FAVORITES", "DOWNLOADS", "PROFILE")) activeDockTab = "HOME"
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
                        if (isDarkMode()) intArrayOf(Color.rgb(190, 198, 210), Color.rgb(105, 113, 126))
                        else intArrayOf(Color.rgb(190, 198, 210), Color.rgb(105, 113, 126))
                    } else {
                        if (isDarkMode()) intArrayOf(Color.argb(70, 34, 37, 44), Color.argb(45, 15, 17, 22))
                        else intArrayOf(Color.argb(75, 255, 255, 255), Color.argb(45, 222, 226, 232))
                    }
                ).apply {
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), if (active) Color.argb(185, 145, 153, 166)
                    else Color.TRANSPARENT)
                }
                val iconTint = if (active) Color.WHITE
                    else if (isDarkMode()) Color.rgb(225, 228, 234) else Color.rgb(91, 98, 110)
                (item.getChildAt(0) as? ImageView)?.imageTintList =
                    android.content.res.ColorStateList.valueOf(iconTint)
                (item.getChildAt(1) as? TextView)?.setTextColor(iconTint)
                item.animate().cancel()
                item.scaleX = 1f
                item.scaleY = 1f
            }
        }
        fun dockItem(key: String, iconRes: Int, label: String, action: () -> Unit) {
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(4), dp(4), dp(3))
                minimumWidth = dp(48)
                minimumHeight = dp(58)
                isClickable = true
                isFocusable = true
                if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.argb(88, 190, 198, 210)),
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
                    if (isDarkMode()) silver else Color.rgb(91, 98, 110)
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(21), dp(21)))
            item.addView(TextView(context).apply {
                text = label
                textSize = 9f
                maxLines = 1
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(if (isDarkMode()) silver else Color.rgb(91, 98, 110))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
            dockItems[key] = item
            dockLabels[key] = label
            dock.addView(item, LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                marginStart = dp(3); marginEnd = dp(3)
            })
        }
        dockItem("HOME", R.drawable.ic_nav_home, tr("الرئيسية")) {
            selected = "ALL"; renderGames(); pageScroll.smoothScrollTo(0, 0)
        }
        dockItem("SEARCH", R.drawable.ic_action_search, tr("بحث")) { showSmartSearch() }
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

        // Hide the floating bottom dock while the user scrolls down and reveal it
        // while scrolling up. Translation keeps the content layout stable (no jump).
        var lastDockScrollY = 0
        var dockHidden = false
        fun setDockVisible(visible: Boolean) {
            if (dockHidden == !visible) return
            dockHidden = !visible
            dock.animate().cancel()
            if (visible) {
                dock.isClickable = true
                dock.isFocusable = true
                dock.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(190L)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            } else {
                dock.isClickable = false
                dock.isFocusable = false
                dock.animate()
                    .translationY((dock.height + dockParams.bottomMargin + dp(12)).toFloat())
                    .alpha(0f)
                    .setDuration(190L)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }
        }
        pageScroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val delta = scrollY - lastDockScrollY
            if (scrollY <= dp(2)) {
                setDockVisible(true)
            } else if (delta >= dp(4)) {
                setDockVisible(false)
            } else if (delta <= -dp(4)) {
                setDockVisible(true)
            }
            lastDockScrollY = scrollY
        }

        // Apply the shared glass/ripple interaction layer after every screen control,
        // including the floating navigation dock, has been assembled.
        applyUnifiedSkin(shell)
        ViewCompat.setOnApplyWindowInsetsListener(shell) { _, insets ->
            val safeTop = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            ).top
            val safeBottom = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or
                    WindowInsetsCompat.Type.mandatorySystemGestures()
            ).bottom
            dockParams.bottomMargin = dp(12) + safeBottom
            dock.layoutParams = dockParams
            // Keep content below Android's status bar on edge-to-edge devices.
            val effectiveSafeTop = safeTop.coerceAtLeast(dp(24))
            pageScroll.setPadding(0, effectiveSafeTop, 0, dp(84) + safeBottom)
            insets
        }
        setContentView(shell)
        applyMotionPreferences(shell)
        ViewCompat.requestApplyInsets(shell)
        renderGames()
    }
}
