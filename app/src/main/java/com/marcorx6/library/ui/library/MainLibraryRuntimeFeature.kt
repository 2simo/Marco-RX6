package com.marcorx6.library.ui

import android.content.Context
import android.view.View
import androidx.activity.ComponentActivity
import com.marcorx6.library.data.Game
import com.marcorx6.library.ui.library.MainLibraryViewState

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
    private val bg: Int,
    private val panel: Int,
    private val red: Int,
    private val white: Int,
    private val gray: Int,
    private val adminSession: () -> Boolean,
    private val accentForeground: () -> Int,
    private val loadCover: (android.widget.ImageView, String, String) -> Unit,
    private val showDetails: (Game) -> Unit,
    private val styleDialog: (android.app.AlertDialog) -> Unit,
    private val showNotificationSettings: () -> Unit,
    private val showAppearanceSettings: () -> Unit,
    private val showGameDialog: () -> Unit,
    private val showProfile: () -> Unit,
    private val showSideMenu: () -> Unit,
    private val applyTypography: (View) -> Unit,
    private val applyUnifiedSkin: (View) -> Unit,
    private val applyMotionPreferences: (View) -> Unit
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
            search = { viewState.search },
            games = { games },
            selected = { viewState.selected },
            selectedGenre = { filterState.genre },
            selectedYear = { filterState.year },
            sortMode = { viewState.sortMode },
            bg = { bg },
            panel = { panel },
            red = { red },
            white = { white },
            gray = { gray }
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
            setSearch = { viewState.search = it },
            setFilters = { viewState.filters = it },
            setCountText = { viewState.countText = it },
            setGameList = { viewState.gameList = it },
            renderGames = ::renderGames,
            showNotificationSettings = showNotificationSettings,
            showAppearanceSettings = showAppearanceSettings,
            showAdvancedFilters = ::showAdvancedFilters,
            showGameDialog = showGameDialog,
            showProfile = showProfile,
            showSideMenu = showSideMenu,
            applyTypography = applyTypography,
            applyUnifiedSkin = applyUnifiedSkin,
            applyMotionPreferences = applyMotionPreferences
        )
    }

    fun create() = mainLibraryFeature.create()
    fun dispose() = mainLibraryFeature.dispose()
    fun renderGames() = gameLibraryFeature.render()
    fun showAdvancedFilters() = advancedFiltersFeature.show()
}
