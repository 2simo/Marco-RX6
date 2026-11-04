package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray

internal class GameDownloadLinksViewFactory(
    private val activity: Activity,
    private val dp: (Int) -> Int,
    private val rounded: (Int, Int) -> Drawable,
    private val panel: () -> Int,
    private val red: () -> Int,
    private val white: () -> Int,
    private val enqueueDownload: (url: String, label: String) -> Unit,
    private val translate: (String) -> String
) {
    fun create(downloadLinks: String, gameName: String): View? {
        val downloads = try { JSONArray(downloadLinks) } catch (_: Exception) { JSONArray() }
        if (downloads.length() == 0) return null

        val downloadScroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        val downloadRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
        }
        for (index in 0 until downloads.length()) {
            val item = downloads.optJSONObject(index) ?: continue
            val target = item.optString("url")
            if (!target.startsWith("https://")) continue
            val label = item.optString("label").ifBlank { "${translate("تحميل")} ${index + 1}" }
            downloadRow.addView(TextView(activity).apply {
                text = "⬇  $label"
                contentDescription = label
                textSize = 12f
                setTextColor(white())
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.argb(235, 49, 153, 255), Color.argb(215, 17, 91, 184))
                ).apply {
                    cornerRadius = dp(13).toFloat()
                    setStroke(dp(1), Color.argb(145, 129, 202, 255))
                }
                if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.argb(100, 205, 235, 255)),
                    null,
                    GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(13).toFloat() }
                )
                elevation = dp(2).toFloat()
                setPadding(dp(13), dp(12), dp(13), dp(12))
                setOnClickListener { enqueueDownload(target, item.optString("label").ifBlank { gameName }) }
            }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
        }
        if (downloadRow.childCount == 0) return null
        downloadScroll.addView(downloadRow)
        return downloadScroll
    }
}
