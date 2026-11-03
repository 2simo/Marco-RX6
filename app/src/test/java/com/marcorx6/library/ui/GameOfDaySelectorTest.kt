package com.marcorx6.library.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class GameOfDaySelectorTest {

    @Test
    fun selectsSameItemForSameCalendarDay() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 3, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val games = listOf("A", "B", "C", "D", "E")
        assertEquals(
            GameOfDaySelector.select(games, calendar),
            GameOfDaySelector.select(games, calendar.clone() as Calendar)
        )
    }

    @Test
    fun returnsNullForEmptyCatalog() {
        assertNull(GameOfDaySelector.select(emptyList<String>(), Calendar.getInstance()))
    }

    @Test
    fun selectedItemAlwaysBelongsToCatalogAndChangesWithDay() {
        val games = (1..31).toList()
        val firstDay = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 3, 12, 0, 0)
        }
        val nextDay = (firstDay.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val first = GameOfDaySelector.select(games, firstDay)
        val next = GameOfDaySelector.select(games, nextDay)

        assertTrue(games.contains(first))
        assertTrue(games.contains(next))
        assertNotEquals(first, next)
    }
}
