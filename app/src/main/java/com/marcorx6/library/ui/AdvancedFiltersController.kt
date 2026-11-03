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
    private val onApply: (String, String) -> Unit,
    private val onClear: () -> Unit
) {
    fun show(
        genres: List<String>,
        years: List<String>,
        selectedGenre: String,
        selectedYear: String
    ) {
        val genreValues = listOf("كل الأنواع") + genres
        val yearValues = listOf("كل السنوات") + years
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        box.addView(makeText("نوع اللعبة", 12f, red, true))
        val genreSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, genreValues)
            setSelection(genreValues.indexOf(selectedGenre.takeIf { it.isNotBlank() } ?: "كل الأنواع").coerceAtLeast(0))
        }
        box.addView(genreSpinner)
        box.addView(makeText("سنة الإصدار", 12f, red, true).apply { setPadding(0, dp(12), 0, 0) })
        val yearSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, yearValues)
            setSelection(yearValues.indexOf(selectedYear.takeIf { it.isNotBlank() } ?: "كل السنوات").coerceAtLeast(0))
        }
        box.addView(yearSpinner)
        AlertDialog.Builder(activity)
            .setTitle("الفلاتر المتقدمة")
            .setView(box)
            .setPositiveButton("تطبيق") { _, _ ->
                onApply(
                    genreSpinner.selectedItem?.toString()?.takeUnless { it == "كل الأنواع" }.orEmpty(),
                    yearSpinner.selectedItem?.toString()?.takeUnless { it == "كل السنوات" }.orEmpty()
                )
            }
            .setNeutralButton("مسح الفلاتر") { _, _ -> onClear() }
            .setNegativeButton("إغلاق", null)
            .show().also(styleDialog)
    }
}
