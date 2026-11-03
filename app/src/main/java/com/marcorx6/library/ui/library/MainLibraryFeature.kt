package com.marcorx6.library.ui

import android.content.Context
import android.view.View

import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.marcorx6.library.data.Game

/** Owns main library screen composition and its view lifecycle. */
internal class MainLibraryFeature(
    context: Context,
    setContentView: (View) -> Unit,
    darkMode: () -> Boolean,
    currentLanguage: () -> String,
    dp: (Int) -> Int,
    translate: (String) -> String,
    rounded: (Int, Int) -> android.graphics.drawable.GradientDrawable,
    createText: (String, Float, Int, Boolean) -> TextView,
    games: () -> List<Game>,
    bg: Int,
    panel: Int,
    red: Int,
    white: Int,
    gray: Int,
    adminSession: () -> Boolean,
    getSelected: () -> String,
    setSelected: (String) -> Unit,
    getSortMode: () -> String,
    setSortMode: (String) -> Unit,
    setSearch: (EditText) -> Unit,
    setFilters: (LinearLayout) -> Unit,
    setCountText: (TextView) -> Unit,
    setGameList: (LinearLayout) -> Unit,
    renderGames: () -> Unit,
    showNotificationSettings: () -> Unit,
    showAppearanceSettings: () -> Unit,
    showAdvancedFilters: () -> Unit,
    showGameDialog: () -> Unit,
    showProfile: () -> Unit,
    showSideMenu: () -> Unit,
    applyTypography: (android.view.View) -> Unit,
    applyUnifiedSkin: (android.view.View) -> Unit,
    applyMotionPreferences: (android.view.View) -> Unit
) {
    private val controller = MainLibraryInterfaceController(
        context = context,
        setContentView = setContentView,
        darkMode = darkMode,
        currentLanguage = currentLanguage,
        dp = dp,
        tr = translate,
        rounded = rounded,
        createText = createText,
        games = games,
        bg = bg,
        panel = panel,
        red = red,
        white = white,
        gray = gray,
        adminSession = adminSession,
        getSelected = getSelected,
        setSelected = setSelected,
        getSortMode = getSortMode,
        setSortMode = setSortMode,
        setSearch = setSearch,
        setFilters = setFilters,
        setCountText = setCountText,
        setGameList = setGameList,
        renderGames = renderGames,
        showNotificationSettings = showNotificationSettings,
        showAppearanceSettings = showAppearanceSettings,
        showAdvancedFilters = showAdvancedFilters,
        showGameDialog = showGameDialog,
        showProfile = showProfile,
        showSideMenu = showSideMenu,
        applyTypography = applyTypography,
        applyUnifiedSkin = applyUnifiedSkin,
        applyMotionPreferences = applyMotionPreferences
    )

    fun create() = controller.create()
    fun dispose() = controller.dispose()
}
