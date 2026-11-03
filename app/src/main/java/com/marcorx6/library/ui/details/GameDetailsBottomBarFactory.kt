package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.widget.LinearLayout
import android.widget.TextView

internal class GameDetailsBottomBarFactory(
    private val activity: Activity,
    private val dp: (Int) -> Int,
    private val rounded: (Int, Int) -> Drawable,
    private val isDarkMode: () -> Boolean,
    private val white: () -> Int,
    private val panel: () -> Int,
    private val translate: (String) -> String
) {
    fun create(
        offlineLabel: String,
        isAdmin: Boolean,
        onOffline: () -> Unit,
        onShare: () -> Unit,
        onComments: () -> Unit,
        onEdit: () -> Unit,
        onDelete: () -> Unit
    ): LinearLayout {
        val bottom = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setBackgroundColor(panel())
        }
        fun readerAction(label: String, action: () -> Unit) = TextView(activity).apply {
            text = label
            textSize = 12f
            setTextColor(if (isDarkMode()) white() else Color.rgb(48, 57, 77))
            gravity = android.view.Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            background = rounded(
                if (isDarkMode()) Color.rgb(48, 53, 70) else Color.rgb(230, 235, 245),
                11
            )
            setPadding(dp(12), dp(12), dp(12), dp(12))
            minHeight = dp(48)
            setOnClickListener { action() }
        }

        bottom.addView(readerAction(offlineLabel, onOffline), LinearLayout.LayoutParams(0, -2, 1.2f).apply {
            rightMargin = dp(6)
        })
        bottom.addView(readerAction(translate("مشاركة"), onShare), LinearLayout.LayoutParams(0, -2, 1f))
        bottom.addView(readerAction(translate("التعليقات"), onComments), LinearLayout.LayoutParams(0, -2, 1f).apply {
            leftMargin = dp(6)
        })
        if (isAdmin) {
            bottom.addView(readerAction("تعديل", onEdit), LinearLayout.LayoutParams(-2, -2).apply {
                leftMargin = dp(8)
            })
            bottom.addView(readerAction("حذف", onDelete), LinearLayout.LayoutParams(-2, -2).apply {
                leftMargin = dp(8)
            })
        }
        return bottom
    }
}
