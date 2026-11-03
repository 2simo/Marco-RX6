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
    gameList: () -> LinearLayout,
    filters: () -> LinearLayout,
    countText: () -> TextView,
    search: () -> EditText,
    games: () -> List<Game>,
    selected: () -> String,
    selectedGenre: () -> String,
    selectedYear: () -> String,
    sortMode: () -> String,
    bg: () -> Int,
    panel: () -> Int,
    red: () -> Int,
    white: () -> Int,
    gray: () -> Int
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
        showDetails = showDetails
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
