package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import android.view.View
import com.marcorx6.library.data.Game
import java.io.File

/** Composition root for the game reader, detail controls, and related view factories. */
internal class GameDetailsFeature(
    activity: Activity,
    games: MutableList<Game>,
    bg: () -> Int,
    dp: (Int) -> Int,
    rounded: (Int, Int) -> GradientDrawable,
    makeText: (String, Float, Int, Boolean) -> TextView,
    isDarkMode: () -> Boolean,
    fontFamilyName: () -> String,
    fontScale: () -> Float,
    enqueueGameDownload: (String, String, String, String, String, String, String) -> Unit,
    white: () -> Int,
    red: () -> Int,
    gray: () -> Int,
    panel: () -> Int,
    tr: (String) -> String,
    loadCover: (android.widget.ImageView, String, String) -> Unit,
    saveGames: () -> Unit,
    renderGames: () -> Unit,
    styleAlertDialog: (android.app.AlertDialog) -> Unit,
    filesDir: File,
    stableCacheName: (String) -> String,
    isAdmin: () -> Boolean,
    postToUi: (() -> Unit) -> Unit,
    openGameLink: (String) -> Unit,
    showGameDialog: (Game) -> Unit,
    deleteGame: (Game) -> Unit,
    applyMotionPreferences: (View) -> Unit,
    applyUnifiedSkin: (View) -> Unit
) {
    private val favoriteToggleFactory = GameFavoriteToggleFactory(
        activity = activity,
        games = games,
        dp = dp,
        white = white,
        red = red,
        saveGames = saveGames,
        renderGames = renderGames
    )

    private val topBarFactory = GameDetailsTopBarFactory(
        activity = activity,
        dp = dp,
        makeText = makeText,
        isDarkMode = isDarkMode,
        white = white,
        red = red,
        favoriteToggleFactory = favoriteToggleFactory
    )

    private val identityViewFactory = GameDetailsIdentityViewFactory(
        activity = activity,
        dp = dp,
        makeText = makeText,
        isDarkMode = isDarkMode,
        loadCover = loadCover,
        translate = tr
    )

    private val downloadLinksViewFactory = GameDownloadLinksViewFactory(
        activity = activity,
        dp = dp,
        rounded = rounded,
        panel = panel,
        red = red,
        white = white,
        enqueueDownload = { url, label, sourcePostUrl ->
            enqueueGameDownload(url, label, "", "", "", "", sourcePostUrl)
        },
        translate = tr
    )

    private val similarGamesViewFactory = GameSimilarGamesViewFactory(
        activity = activity,
        dp = dp,
        rounded = rounded,
        makeText = makeText,
        isDarkMode = isDarkMode,
        white = white,
        translate = tr,
        loadCover = loadCover
    )

    private val controller = GameDetailsController(
        activity = activity,
        games = { games },
        bg = bg,
        dp = dp,
        topBarFactory = topBarFactory,
        identityViewFactory = identityViewFactory,
        similarGamesViewFactory = similarGamesViewFactory,
        downloadLinksViewFactory = downloadLinksViewFactory,
        isAdmin = isAdmin,
        showGameDialog = showGameDialog,
        deleteGame = deleteGame,
        applyMotionPreferences = applyMotionPreferences,
        applyUnifiedSkin = applyUnifiedSkin,
        translate = tr
    )

    fun show(game: Game) = controller.show(game)
}
