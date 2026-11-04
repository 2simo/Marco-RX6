package com.marcorx6.library.ui

import android.graphics.Color

/** Central accent color mapping used by the existing appearance settings. */
internal object AccentPalette {
    fun color(name: String?): Int = when (name) {
        "blue" -> Color.rgb(224, 126, 54)
        "green" -> Color.rgb(35, 170, 125)
        "gold" -> Color.rgb(205, 157, 70)
        "rose" -> Color.rgb(215, 90, 135)
        else -> Color.rgb(224, 126, 54)
    }

    fun foreground(): Int = Color.WHITE
}
