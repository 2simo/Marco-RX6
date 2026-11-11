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
        Option("black_silver", "أسود فضي", Color.rgb(197, 206, 219)),
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

    /** Return an accent-hued foreground that meets WCAG AA against the app's neutral surface. */
    fun readableAccentText(id: String = DEFAULT_ID, darkMode: Boolean): Int {
        val accent = color(id)
        val referenceSurface = if (darkMode) Color.rgb(25, 28, 34) else Color.rgb(246, 248, 251)
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.04045) normalized / 12.92
            else Math.pow((normalized + 0.055) / 1.055, 2.4)
        }
        fun luminance(value: Int): Double =
            0.2126 * channel(Color.red(value)) +
                0.7152 * channel(Color.green(value)) +
                0.0722 * channel(Color.blue(value))
        fun contrast(first: Int, second: Int): Double {
            val one = luminance(first)
            val two = luminance(second)
            return (maxOf(one, two) + 0.05) / (minOf(one, two) + 0.05)
        }
        if (contrast(accent, referenceSurface) >= 4.5) return accent
        val target = if (darkMode) Color.WHITE else Color.BLACK
        var low = 0f
        var high = 1f
        repeat(18) {
            val amount = (low + high) / 2f
            val candidate = Color.rgb(
                (Color.red(accent) * (1f - amount) + Color.red(target) * amount).toInt(),
                (Color.green(accent) * (1f - amount) + Color.green(target) * amount).toInt(),
                (Color.blue(accent) * (1f - amount) + Color.blue(target) * amount).toInt()
            )
            if (contrast(candidate, referenceSurface) >= 4.5) high = amount else low = amount
        }
        return Color.rgb(
            (Color.red(accent) * (1f - high) + Color.red(target) * high).toInt(),
            (Color.green(accent) * (1f - high) + Color.green(target) * high).toInt(),
            (Color.blue(accent) * (1f - high) + Color.blue(target) * high).toInt()
        )
    }

    /** Blend a neutral surface with the selected primary color without turning it into a flat accent block. */
    fun surface(base: Int, accent: Int, amount: Float, accentId: String? = null): Int {
        // Black & Silver keeps black/white as the dominant surface. Silver is
        // reserved for restrained highlights instead of tinting every surface blue-grey.
        if (accentId == "black_silver" || (accentId == null && accent == Color.rgb(197, 206, 219))) {
            val neutral = if (Color.red(base) < 128) Color.rgb(27, 29, 34)
                else Color.rgb(239, 242, 247)
            val neutralAmount = (amount * 0.18f).coerceIn(0f, 0.035f)
            return Color.rgb(
                (Color.red(base) * (1f - neutralAmount) + Color.red(neutral) * neutralAmount).toInt(),
                (Color.green(base) * (1f - neutralAmount) + Color.green(neutral) * neutralAmount).toInt(),
                (Color.blue(base) * (1f - neutralAmount) + Color.blue(neutral) * neutralAmount).toInt()
            )
        }
        val t = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(base) * (1f - t) + Color.red(accent) * t).toInt().coerceIn(0, 255),
            (Color.green(base) * (1f - t) + Color.green(accent) * t).toInt().coerceIn(0, 255),
            (Color.blue(base) * (1f - t) + Color.blue(accent) * t).toInt().coerceIn(0, 255)
        )
    }

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
