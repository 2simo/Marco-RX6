package com.marcorx6.library.ui

import android.app.AlertDialog

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import com.marcorx6.library.network.BloggerFeedParser

internal class GameDownloadLinksViewFactory(
    private val activity: Activity,
    private val dp: (Int) -> Int,
    private val rounded: (Int, Int) -> Drawable,
    private val panel: () -> Int,
    private val red: () -> Int,
    private val white: () -> Int,
    private val enqueueDownload: (url: String, label: String, sourcePostUrl: String) -> Unit,
    private val translate: (String) -> String
) {
    fun createFromHtml(bloggerHtml: String, gameName: String, sourcePostUrl: String): View? {
        if (bloggerHtml.isBlank()) return null
        // Reuse the strict Blogger link classifier so article/social/navigation links
        // are not incorrectly rendered as download buttons.
        return create(BloggerFeedParser.extractDownloadLinks(bloggerHtml), gameName, sourcePostUrl)
    }

    fun create(downloadLinks: String, gameName: String, sourcePostUrl: String): View? {
        val downloads = try { JSONArray(downloadLinks) } catch (_: Exception) { JSONArray() }
        if (downloads.length() == 0) return null

        val downloadSection = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        downloadSection.addView(TextView(activity).apply {
            text = "روابط التحميل"
            textSize = 16f
            setTextColor(white())
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        }, LinearLayout.LayoutParams(-1, -2))
        val downloadRow = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        for (index in 0 until downloads.length()) {
            val item = downloads.optJSONObject(index) ?: continue
            val target = item.optString("url").trim()
            if (!(target.startsWith("https://", ignoreCase = true) || target.startsWith("http://", ignoreCase = true))) continue
            // Older imported records may still contain a cover URL in downloadLinks.
            // Filter common image resources here as a second line of defence.
            val targetPath = target.substringBefore('?').substringBefore('#').lowercase(java.util.Locale.ROOT)
            if (Regex("\\.(?:jpe?g|png|gif|webp|bmp|svg|avif|jfif)$").containsMatchIn(targetPath)) continue
            val label = item.optString("label").ifBlank { "${translate("تحميل")} ${index + 1}" }
            downloadRow.addView(TextView(activity).apply {
                text = "  $label"
                setCompoundDrawablesRelativeWithIntrinsicBounds(com.marcorx6.library.R.drawable.ic_action_download, 0, 0, 0)
                compoundDrawablePadding = dp(8)
                contentDescription = label
                textSize = 14f
                setTextColor(com.marcorx6.library.ui.AccentPalette.foregroundForColor(red()))
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(red(), Color.rgb((Color.red(red()) * 0.72f).toInt(), (Color.green(red()) * 0.72f).toInt(), (Color.blue(red()) * 0.72f).toInt()))
                ).apply {
                    cornerRadius = dp(13).toFloat()
                    setStroke(dp(1), Color.argb(145, Color.red(red()), Color.green(red()), Color.blue(red())))
                }
                if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.argb(100, Color.red(red()), Color.green(red()), Color.blue(red()))),
                    null,
                    GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(13).toFloat() }
                )
                elevation = dp(2).toFloat()
                minHeight = dp(50)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setOnClickListener {
                    val downloadTitle = item.optString("label").ifBlank { gameName }
                    val host = android.net.Uri.parse(target).host.orEmpty()
                    AlertDialog.Builder(activity)
                        .setTitle(translate("تأكيد التنزيل"))
                        .setMessage(
                            translate("هل تريد بدء تنزيل هذا الملف؟") + "\\n\\n" +
                                translate("الملف") + ": " + downloadTitle + "\\n" +
                                translate("المصدر") + ": " + host
                        )
                        .setNegativeButton(translate("إلغاء"), null)
                        .setPositiveButton(translate("بدء التنزيل")) { _, _ ->
                            enqueueDownload(target, downloadTitle, sourcePostUrl)
                        }
                        .show()
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        }
        if (downloadRow.childCount == 0) return null
        downloadSection.addView(downloadRow, LinearLayout.LayoutParams(-1, -2))
        return downloadSection
    }
}
