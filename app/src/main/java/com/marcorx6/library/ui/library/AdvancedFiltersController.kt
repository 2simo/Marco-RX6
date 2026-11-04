package com.marcorx6.library.ui

import android.app.Activity
import android.app.AlertDialog
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner

/** Builds the existing advanced genre/year filter dialog. */
internal class AdvancedFiltersController(
    private val activity: Activity,
    private val dp: (Int) -> Int,
    private val red: Int,
    private val makeText: (String, Float, Int, Boolean) -> android.widget.TextView,
    private val styleDialog: (AlertDialog) -> Unit,
    private val translate: (String) -> String,
    private val onApply: (String, String) -> Unit,
    private val onClear: () -> Unit
) {
    fun show(
        genres: List<String>,
        years: List<String>,
        selectedGenre: String,
        selectedYear: String
    ) {
        val genreValues = listOf(translate("كل الأنواع")) + genres
        val yearValues = listOf(translate("كل السنوات")) + years
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        box.addView(makeText(translate("نوع اللعبة"), 12f, red, true))
        val genreSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, genreValues)
            setSelection(genreValues.indexOf(selectedGenre.takeIf { it.isNotBlank() } ?: "كل الأنواع").coerceAtLeast(0))
        }
        box.addView(genreSpinner)
        box.addView(makeText(translate("سنة الإصدار"), 12f, red, true).apply { setPadding(0, dp(12), 0, 0) })
        val yearSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, yearValues)
            setSelection(yearValues.indexOf(selectedYear.takeIf { it.isNotBlank() } ?: "كل السنوات").coerceAtLeast(0))
        }
        box.addView(yearSpinner)
        AlertDialog.Builder(activity)
            .setTitle(translate("الفلاتر المتقدمة"))
            .setView(box)
            .setPositiveButton(translate("تطبيق")) { _, _ ->
                onApply(
                    genres.getOrNull(genreSpinner.selectedItemPosition - 1).orEmpty(),
                    years.getOrNull(yearSpinner.selectedItemPosition - 1).orEmpty()
                )
            }
            .setNeutralButton(translate("مسح الفلاتر")) { _, _ -> onClear() }
            .setNegativeButton(translate("إغلاق"), null)
            .show().also(styleDialog)
    }
}
