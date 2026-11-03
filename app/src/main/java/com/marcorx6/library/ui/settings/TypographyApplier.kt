package com.marcorx6.library.ui

import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/** Applies the selected typography recursively without owning Activity state. */
internal object TypographyApplier {
    fun apply(view: View, fontFamily: String, fontScale: Float, scaledDensity: Float) {
        if (view is TextView) {
            val style = view.typeface?.style ?: Typeface.NORMAL
            view.setTypeface(Typeface.create(fontFamily, style))
            view.textSize = view.textSize / scaledDensity * fontScale
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                apply(view.getChildAt(i), fontFamily, fontScale, scaledDensity)
            }
        }
    }
}
