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
            background = rounded(if (isDarkMode()) Color.argb(210, 9, 30, 51) else Color.argb(220, 255, 255, 255), 18)
        }
        fun readerAction(label: String, action: () -> Unit, primary: Boolean = false) = TextView(activity).apply {
            text = label
            textSize = 12f
            setTextColor(if (primary || isDarkMode()) Color.WHITE else Color.rgb(48, 57, 77))
            gravity = android.view.Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            background = if (primary) android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.argb(232, 42, 145, 255), Color.argb(215, 20, 91, 190))
            ).apply { cornerRadius = dp(11).toFloat() } else rounded(
                if (isDarkMode()) Color.argb(205, 20, 68, 117) else Color.argb(210, 222, 237, 255),
                11
            )
            setPadding(dp(12), dp(12), dp(12), dp(12))
            minHeight = dp(48)
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                foreground = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.argb(78, 83, 177, 255)),
                    null,
                    android.graphics.drawable.ColorDrawable(Color.WHITE)
                )
            }
            setOnClickListener { action() }
        }

        bottom.addView(readerAction(offlineLabel, onOffline, primary = true), LinearLayout.LayoutParams(0, -2, 1.2f).apply {
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
