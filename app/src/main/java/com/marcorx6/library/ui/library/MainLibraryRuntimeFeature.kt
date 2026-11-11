package com.marcorx6.library.ui.library

import com.marcorx6.library.ui.*

import android.content.Context
import android.view.View
import androidx.activity.ComponentActivity
import com.marcorx6.library.data.Game

/**
 * Owns the main library feature graph: screen composition, rendering, filters and view state.
 * Activity is supplied as an Android host, while actions are injected as callbacks.
 */
internal class MainLibraryRuntimeFeature(
    private val activity: ComponentActivity,
    private val context: Context,
    private val setContentView: (View) -> Unit,
    private val games: MutableList<Game>,
    private val darkMode: () -> Boolean,
    private val currentLanguage: () -> String,
    private val dp: (Int) -> Int,
    private val translate: (String) -> String,
    private val rounded: (Int, Int) -> android.graphics.drawable.GradientDrawable,
    private val createText: (String, Float, Int, Boolean) -> android.widget.TextView,
    private var bg: Int,
    private var panel: Int,
    private var red: Int,
    private var white: Int,
    private var gray: Int,
    private val adminSession: () -> Boolean,
    private val accentForeground: () -> Int,
    private val loadCover: (android.widget.ImageView, String, String) -> Unit,
    private val showDetails: (Game) -> Unit,
    private val styleDialog: (android.app.AlertDialog) -> Unit,
    private val toggleDarkMode: () -> Unit,
    private val showAppearanceSettings: () -> Unit,
    private val showGameDialog: () -> Unit,
    private val showProfile: () -> Unit,
    private val showDownloads: () -> Unit,
    private val applyTypography: (View) -> Unit,
    private val applyUnifiedSkin: (View) -> Unit,
    private val applyMotionPreferences: (View) -> Unit,
    private val showNotifications: () -> Unit,
    private val showMessages: () -> Unit,
    private val loadEngagementSummary: (Game, (Long, Double, Long) -> Unit) -> Unit,
) {
    val viewState = MainLibraryViewState()
    val filterState = LibraryFilterState()

    private val gameLibraryFeature by lazy {
        GameLibraryFeature(
            activity = activity,
            dp = dp,
            rounded = rounded,
            makeText = createText,
            isDarkMode = darkMode,
            accentForeground = accentForeground,
            translate = translate,
            loadCover = loadCover,
            showDetails = showDetails,
            gameList = { viewState.gameList },
            filters = { viewState.filters },
            countText = { viewState.countText },
            games = { games },
            selected = { viewState.selected },
            selectedGenre = { filterState.genre },
            selectedYear = { filterState.year },
            sortMode = { viewState.sortMode },
            bg = { bg },
            panel = { panel },
            red = { red },
            white = { white },
            gray = { gray },
            loadEngagementSummary = loadEngagementSummary,
            openCategory = { category, title -> mainLibraryFeature.openCategoryFromSection(category, title) }
        )
    }

    private val advancedFiltersFeature by lazy {
        AdvancedFiltersFeature(
            activity = activity,
            state = filterState,
            games = { games },
            dp = dp,
            red = { red },
            makeText = createText,
            styleDialog = styleDialog,
            translate = translate,
            renderGames = ::renderGames
        )
    }

    private val mainLibraryFeature by lazy {
        MainLibraryFeature(
            context = context,
            setContentView = setContentView,
            darkMode = darkMode,
            currentLanguage = currentLanguage,
            dp = dp,
            translate = translate,
            rounded = rounded,
            createText = createText,
            games = { games },
            bg = bg,
            panel = panel,
            red = red,
            white = white,
            gray = gray,
            adminSession = adminSession,
            getSelected = { viewState.selected },
            setSelected = { viewState.selected = it },
            getSortMode = { viewState.sortMode },
            setSortMode = { viewState.sortMode = it },
            setFilters = { viewState.filters = it },
            setCountText = { viewState.countText = it },
            setGameList = { viewState.gameList = it },
            renderGames = ::renderGames,
            toggleDarkMode = toggleDarkMode,
            showAppearanceSettings = showAppearanceSettings,
            showAdvancedFilters = ::showAdvancedFilters,
            showGameDialog = showGameDialog,
            showProfile = showProfile,
            showDownloads = showDownloads,
            loadCover = loadCover,
            showDetails = showDetails,
            applyTypography = applyTypography,
            applyUnifiedSkin = applyUnifiedSkin,
            applyMotionPreferences = applyMotionPreferences,
            showNotifications = showNotifications,
            showMessages = showMessages
        )
    }

    fun initializePreferredPlatform() {
        viewState.selected = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getString("preferred_platform", "ALL")
            ?.takeIf { it in listOf("PS2", "PS3", "PS4") } ?: "ALL"
    }

    /** Update cached constructor colors without rebuilding or navigating away from the active screen. */
    fun refreshPalette(bg: Int, panel: Int, red: Int, white: Int, gray: Int) {
        this.bg = bg
        this.panel = panel
        this.red = red
        this.white = white
        this.gray = gray
        mainLibraryFeature.refreshPalette(bg, panel, red, white, gray)
    }

    fun create() = mainLibraryFeature.create()
    fun dispose() = mainLibraryFeature.dispose()
    fun renderGames() {
        // MainLibraryInterfaceController requests an initial render while building
        // category sections, before the list/filter/count views are attached.
        // Skip that early request; create() performs the real render after wiring them.
        if (!viewState.isReadyForRendering()) return
        gameLibraryFeature.render()
    }
    fun showAdvancedFilters() = advancedFiltersFeature.show()
}
