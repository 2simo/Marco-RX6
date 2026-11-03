package com.marcorx6.library.ui

/** Persistent-in-activity selections for the library's advanced filters. */
internal class LibraryFilterState {
    var genre: String = ""
        private set
    var year: String = ""
        private set

    fun apply(genre: String, year: String) {
        this.genre = genre
        this.year = year
    }

    fun clear() {
        genre = ""
        year = ""
    }
}
