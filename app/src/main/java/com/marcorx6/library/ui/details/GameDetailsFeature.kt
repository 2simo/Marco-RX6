package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import android.view.View
import com.marcorx6.library.data.Game
import com.marcorx6.library.ui.details.GameRatingViewFactory
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
    applyUnifiedSkin: (View) -> Unit,
    isRegisteredUser: () -> Boolean,
    loadEngagementSummary: (Game, (Long, Double, Long, Long) -> Unit) -> Unit,
    recordDownload: (Game) -> Unit,
    loadMyRating: (Game, (Int?) -> Unit) -> Unit,
    submitRating: (Game, Int, (Boolean, String?) -> Unit) -> Unit,
    recordViewOnce: (Game) -> Unit
) {
    // The download dialog is created once, while the selected game changes per details screen.
    private var activeGameForDownload: Game? = null

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
        loadDownloads = { target, callback ->
            loadEngagementSummary(target) { _, _, _, downloads -> callback(downloads) }
        },
        translate = tr,
        favoriteToggleFactory = favoriteToggleFactory
    )

    private val downloadLinksViewFactory = GameDownloadLinksViewFactory(
        activity = activity,
        dp = dp,
        rounded = rounded,
        panel = panel,
        red = red,
        white = white,
        enqueueDownload = { url, label, sourcePostUrl ->
            activeGameForDownload?.let(recordDownload)
            enqueueGameDownload(url, label, "", "", "", "", sourcePostUrl)
        },
        translate = tr
    )

    private val ratingViewFactory = GameRatingViewFactory(
        activity = activity,
        dp = dp,
        makeText = makeText,
        isDarkMode = isDarkMode,
        white = white,
        gray = gray,
        translate = tr,
        isRegisteredUser = isRegisteredUser,
        loadMyRating = loadMyRating,
        submitRating = submitRating
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
        ratingViewFactory = ratingViewFactory,
        recordViewOnce = recordViewOnce,
        similarGamesViewFactory = similarGamesViewFactory,
        downloadLinksViewFactory = downloadLinksViewFactory,
        isAdmin = isAdmin,
        showGameDialog = showGameDialog,
        deleteGame = deleteGame,
        applyMotionPreferences = applyMotionPreferences,
        applyUnifiedSkin = applyUnifiedSkin,
        translate = tr,
        isDarkMode = isDarkMode
    )

    fun show(game: Game) {
        activeGameForDownload = game
        controller.show(game)
    }
}
