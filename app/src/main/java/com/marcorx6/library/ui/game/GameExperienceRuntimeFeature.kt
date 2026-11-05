package com.marcorx6.library.ui.game

import com.marcorx6.library.ui.*

import androidx.activity.ComponentActivity
import com.marcorx6.library.data.Game
import java.io.File

/**
 * Owns game details and game-of-the-day presentation as one game experience feature.
 */
internal class GameExperienceRuntimeFeature(
    private val activity: ComponentActivity,
    private val games: MutableList<Game>,
    private val bg: () -> Int,
    private val dp: (Int) -> Int,
    private val rounded: (Int, Int) -> android.graphics.drawable.GradientDrawable,
    private val makeText: (String, Float, Int, Boolean) -> android.widget.TextView,
    private val isDarkMode: () -> Boolean,
    private val fontFamilyName: () -> String,
    private val fontScale: () -> Float,
    private val enqueueGameDownload: (String, String, String, String, String, String) -> Unit,
    private val white: () -> Int,
    private val red: () -> Int,
    private val gray: () -> Int,
    private val panel: () -> Int,
    private val translate: (String) -> String,
    private val targetLanguage: () -> String,
    private val loadCover: (android.widget.ImageView, String, String) -> Unit,
    private val saveGames: () -> Unit,
    private val renderGames: () -> Unit,
    private val styleAlertDialog: (android.app.AlertDialog) -> Unit,
    private val filesDir: File,
    private val stableCacheName: (String) -> String,
    private val isAdmin: () -> Boolean,
    private val postToUi: (() -> Unit) -> Unit,
    private val openGameLink: (String) -> Unit,
    private val showGameDialog: (Game?) -> Unit,
    private val deleteGame: (Game) -> Unit,
    private val applyMotionPreferences: (android.view.View) -> Unit,
    private val applyUnifiedSkin: (android.view.View) -> Unit,
    private val showDetailsForDay: (Game) -> Unit
) {
    private val gameDetailsFeature by lazy {
        GameDetailsFeature(
            activity = activity,
            games = games,
            bg = bg,
            dp = dp,
            rounded = rounded,
            makeText = makeText,
            isDarkMode = isDarkMode,
            fontFamilyName = fontFamilyName,
            fontScale = fontScale,
            enqueueGameDownload = enqueueGameDownload,
            white = white,
            red = red,
            gray = gray,
            panel = panel,
            tr = translate,
            loadCover = loadCover,
            saveGames = saveGames,
            renderGames = renderGames,
            styleAlertDialog = styleAlertDialog,
            filesDir = filesDir,
            stableCacheName = stableCacheName,
            isAdmin = isAdmin,
            postToUi = postToUi,
            openGameLink = openGameLink,
            showGameDialog = showGameDialog,
            deleteGame = deleteGame,
            applyMotionPreferences = applyMotionPreferences,
            applyUnifiedSkin = applyUnifiedSkin
        )
    }

    private val gameOfTheDayController by lazy {
        GameOfTheDayController(
            activity = activity,
            games = { games },
            dp = dp,
            rounded = rounded,
            panel = panel,
            white = white,
            red = red,
            gray = gray,
            makeText = makeText,
            loadCover = loadCover,
            showDetails = showDetailsForDay,
            openGameLink = openGameLink,
            styleAlertDialog = styleAlertDialog,
            translate = translate
        )
    }

    fun showDetails(game: Game) = gameDetailsFeature.show(game)
    fun showGameOfTheDay() = gameOfTheDayController.show()
}
