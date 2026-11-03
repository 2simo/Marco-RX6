package com.marcorx6.library.ui

import androidx.activity.ComponentActivity
import com.marcorx6.library.data.Game

/** Owns advanced filter dialog setup and applies selections to library state. */
internal class AdvancedFiltersFeature(
    activity: ComponentActivity,
    private val state: LibraryFilterState,
    private val games: () -> List<Game>,
    dp: (Int) -> Int,
    red: () -> Int,
    makeText: (String, Float, Int, Boolean) -> android.widget.TextView,
    styleDialog: (android.app.AlertDialog) -> Unit,
    renderGames: () -> Unit
) {
    private val controller = AdvancedFiltersController(
        activity = activity,
        dp = dp,
        red = red(),
        makeText = makeText,
        styleDialog = styleDialog,
        onApply = { genre, year ->
            state.apply(genre, year)
            renderGames()
        },
        onClear = {
            state.clear()
            renderGames()
        }
    )

    fun show() {
        val library = games()
        controller.show(
            genres = library.map { it.genre.trim() }.filter { it.isNotBlank() }.distinct().sorted(),
            years = library.map { it.releaseYear.trim() }.filter { it.isNotBlank() }.distinct().sortedDescending(),
            selectedGenre = state.genre,
            selectedYear = state.year
        )
    }
}
