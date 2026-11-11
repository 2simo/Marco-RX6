package com.marcorx6.library.ui

import android.graphics.Color

/**
 * MARCO primary accent choices. Silver remains the default brand accent;
 * the selected accent is used for interactive controls, highlights and states.
 */
internal object AccentPalette {
    const val PREFERENCE_KEY = "accent"
    const val DEFAULT_ID = "silver"

    data class Option(val id: String, val label: String, val color: Int) 

    val options = listOf(
        Option("silver", "فضي", Color.rgb(190, 198, 210)),
        Option("blue", "أزرق", Color.rgb(55, 132, 255)),
        Option("purple", "بنفسجي", Color.rgb(145, 92, 255)),
        Option("red", "أحمر", Color.rgb(235, 70, 80)),
        Option("green", "أخضر", Color.rgb(32, 183, 122)),
        Option("gold", "ذهبي", Color.rgb(214, 168, 75)),
        Option("cyan", "سماوي", Color.rgb(32, 191, 208)),
        Option("pink", "وردي", Color.rgb(232, 93, 158))
    )

    fun color(id: String = DEFAULT_ID): Int =
        options.firstOrNull { it.id == id }?.color ?: options.first { it.id == DEFAULT_ID }.color

    fun foreground(id: String = DEFAULT_ID): Int = foregroundForColor(color(id))

    fun foregroundForColor(color: Int): Int {
        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.04045) value / 12.92
            else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        val luminance = 0.2126 * linear(Color.red(color)) +
            0.7152 * linear(Color.green(color)) +
            0.0722 * linear(Color.blue(color))
        // WCAG's 0.179 crossover gives the stronger black/white contrast.
        return if (luminance > 0.179) Color.rgb(20, 23, 29) else Color.WHITE
    }
}
