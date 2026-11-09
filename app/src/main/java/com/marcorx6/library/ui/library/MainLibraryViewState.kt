package com.marcorx6.library.ui.library

import android.widget.LinearLayout
import android.widget.TextView

/**
 * Owns the mutable view references and navigation/sort selection for the main library screen.
 * Activity remains the lifecycle host; library features receive values and callbacks.
 */
internal class MainLibraryViewState {
    lateinit var gameList: LinearLayout
    lateinit var filters: LinearLayout
    lateinit var countText: TextView

    /** Rendering can be requested while the screen tree is still being assembled. */
    fun isReadyForRendering(): Boolean =
        this::gameList.isInitialized && this::filters.isInitialized && this::countText.isInitialized

    var selected: String = "ALL"
    var sortMode: String = "الأحدث"
}
