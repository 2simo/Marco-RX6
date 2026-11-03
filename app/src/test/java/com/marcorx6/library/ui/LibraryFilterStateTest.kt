package com.marcorx6.library.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFilterStateTest {
    @Test
    fun applyStoresGenreAndYearTogether() {
        val state = LibraryFilterState()
        state.apply("Action", "2024")

        assertEquals("Action", state.genre)
        assertEquals("2024", state.year)
    }

    @Test
    fun clearRemovesBothAdvancedFilters() {
        val state = LibraryFilterState()
        state.apply("Action", "2024")
        state.clear()

        assertEquals("", state.genre)
        assertEquals("", state.year)
    }
}
