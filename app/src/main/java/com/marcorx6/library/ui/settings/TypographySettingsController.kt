package com.marcorx6.library.ui

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.app.AlertDialog

internal class TypographySettingsController(
    private val activity: Activity,
    private val preferences: SharedPreferences,
    private val dp: (Int) -> Int,
    private val makeText: (String, Float, Int, Boolean) -> TextView,
    private val panelColor: () -> Int,
    private val redColor: () -> Int,
    private val whiteColor: () -> Int,
    private val grayColor: () -> Int,
    private val roundedBackground: (Int, Int) -> Drawable,
    private val themedSpinner: (List<String>, Int) -> Spinner,
    private val recreateInterface: () -> Unit,
    private val styleDialog: (AlertDialog) -> Unit,
    private val translate: (String) -> String
) {
    fun show() {
        val accentBlue = Color.rgb(42, 145, 255)
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(8))
        }
        val preview = makeText(translate("MARCO RX6  •  معاينة شكل النص\nقراءة أوضح وتجربة مريحة"), 17f, whiteColor(), true)
        preview.setPadding(dp(14), dp(14), dp(14), dp(14))
        preview.background = roundedBackground(panelColor(), 13)
        box.addView(preview, LinearLayout.LayoutParams(-1, -2))
        box.addView(makeText(translate("حجم الخط"), 13f, accentBlue, true).apply {
            setPadding(0, dp(18), 0, dp(5))
        })
        val scale = TypographyPreferences.scale(preferences)
        val size = SeekBar(activity).apply {
            max = 8
            progress = ((scale - 0.85f) / 0.05f).toInt().coerceIn(0, 8)
        }
        size.progressTintList = android.content.res.ColorStateList.valueOf(accentBlue)
        size.thumbTintList = android.content.res.ColorStateList.valueOf(accentBlue)
        box.addView(size)
        val sizeLabel = makeText(translate("حجم النص") + ": " + (scale * 100).toInt() + "%", 11f, grayColor(), false)
        box.addView(sizeLabel)
        box.addView(makeText(translate("نوع الخط"), 13f, accentBlue, true).apply {
            setPadding(0, dp(16), 0, dp(5))
        })
        val family = themedSpinner(
            listOf(translate("واضح (افتراضي)"), translate("كلاسيكي"), translate("أحادي المسافة")),
            when (preferences.getString("family", "sans")) {
                "serif" -> 1
                "mono" -> 2
                else -> 0
            }
        )
        box.addView(family)
        size.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val currentScale = 0.85f + progress * 0.05f
                sizeLabel.text = translate("حجم النص") + ": " + (currentScale * 100).toInt() + "%"
                preview.textSize = 17f * currentScale
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        AlertDialog.Builder(activity).setTitle(translate("تخصيص الخط")).setView(box)
            .setNegativeButton(translate("إلغاء"), null)
            .setPositiveButton(translate("حفظ")) { _, _ ->
                val familyValue = when (family.selectedItemPosition) {
                    1 -> "serif"
                    2 -> "mono"
                    else -> "sans"
                }
                preferences.edit()
                    .putFloat("scale", 0.85f + size.progress * 0.05f)
                    .putString("family", familyValue)
                    .apply()
                recreateInterface()
                Toast.makeText(activity, translate("تم حفظ إعدادات الخط"), Toast.LENGTH_SHORT).show()
            }.show().also(styleDialog)
    }
}
