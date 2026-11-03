package com.marcorx6.library.ui.activity

import com.marcorx6.library.data.Game
import com.marcorx6.library.ui.CoverUploadState
import com.marcorx6.library.ui.LibraryFilterState

/**
 * Mutable state shared by activity-level features.
 * UI and integration features receive narrow accessors rather than owning this state.
 */
internal class MainActivityRuntimeState {
    val games: MutableList<Game> = mutableListOf()
    val libraryFilterState = LibraryFilterState()
    val coverUploadState = CoverUploadState()
    var isSyncing: Boolean = false
}
