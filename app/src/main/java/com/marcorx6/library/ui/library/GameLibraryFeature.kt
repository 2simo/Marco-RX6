package com.marcorx6.library.ui

import androidx.activity.ComponentActivity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.marcorx6.library.data.Game

/** Owns game list rendering and reads the current library UI state at render time. */
internal class GameLibraryFeature(
    activity: ComponentActivity,
    dp: (Int) -> Int,
    rounded: (Int, Int) -> android.graphics.drawable.Drawable,
    makeText: (String, Float, Int, Boolean) -> TextView,
    isDarkMode: () -> Boolean,
    accentForeground: () -> Int,
    translate: (String) -> String,
    loadCover: (android.widget.ImageView, String, String) -> Unit,
    showDetails: (Game) -> Unit,
    private val gameList: () -> LinearLayout,
    private val filters: () -> LinearLayout,
    private val countText: () -> TextView,
    private val search: () -> EditText,
    private val games: () -> List<Game>,
    private val selected: () -> String,
    private val selectedGenre: () -> String,
    private val selectedYear: () -> String,
    private val sortMode: () -> String,
    private val bg: () -> Int,
    private val panel: () -> Int,
    private val red: () -> Int,
    private val white: () -> Int,
    private val gray: () -> Int
) {
    private val renderer = GameLibraryRenderer(
        activity = activity,
        dp = dp,
        rounded = rounded,
        makeText = makeText,
        isDarkMode = isDarkMode,
        accentForeground = accentForeground,
        tr = translate,
        loadCover = loadCover,
        showDetails = showDetails,
        loadEngagementSummary = loadEngagementSummary
    )

    fun render() = renderer.render(
        gameList = gameList(),
        filters = filters(),
        countText = countText(),
        search = search(),
        games = games(),
        selected = selected(),
        selectedGenre = selectedGenre(),
        selectedYear = selectedYear(),
        sortMode = sortMode(),
        bg = bg(),
        panel = panel(),
        red = red(),
        white = white(),
        gray = gray()
    )
}
