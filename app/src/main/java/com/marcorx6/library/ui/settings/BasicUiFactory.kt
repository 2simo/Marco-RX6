package com.marcorx6.library.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.TextView

/**
 * Small context-bound factories for common programmatic UI primitives.
 * Keeps the Activity-facing helpers stable while moving construction details out.
 */
internal class BasicUiFactory(private val context: Context) {

    fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    fun makeText(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
    }
}
