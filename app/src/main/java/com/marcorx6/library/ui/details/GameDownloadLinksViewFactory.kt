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
    private val enqueueDownload: (url: String, label: String) -> Unit
) {
    fun create(downloadLinks: String, gameName: String): View? {
        val downloads = try { JSONArray(downloadLinks) } catch (_: Exception) { JSONArray() }
        if (downloads.length() == 0) return null

        val downloadScroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(panel())
        }
        val downloadRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
        }
        for (index in 0 until downloads.length()) {
            val item = downloads.optJSONObject(index) ?: continue
            val target = item.optString("url")
            if (!target.startsWith("https://")) continue
            val label = item.optString("label").ifBlank { "تحميل ${index + 1}" }
            downloadRow.addView(TextView(activity).apply {
                text = "⬇  $label"
                textSize = 12f
                setTextColor(white())
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                background = rounded(android.graphics.Color.rgb(35, 132, 232), 11)
                setPadding(dp(13), dp(12), dp(13), dp(12))
                setOnClickListener { enqueueDownload(target, item.optString("label").ifBlank { gameName }) }
            }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
        }
        if (downloadRow.childCount == 0) return null
        downloadScroll.addView(downloadRow)
        return downloadScroll
    }
}
