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

    var selected: String = "ALL"
    var sortMode: String = "الأحدث"
}
