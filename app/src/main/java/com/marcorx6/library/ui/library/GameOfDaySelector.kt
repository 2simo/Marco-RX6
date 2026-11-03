package com.marcorx6.library.ui

import java.util.Calendar

/** Stable daily selection: the same catalog and calendar day yield the same game. */
internal object GameOfDaySelector {
    fun <T> select(items: List<T>, calendar: Calendar): T? {
        if (items.isEmpty()) return null
        val dayKey = calendar.get(Calendar.YEAR) * 366 + calendar.get(Calendar.DAY_OF_YEAR)
        return items[Math.floorMod(dayKey, items.size)]
    }
}
