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
    fun createFromHtml(bloggerHtml: String, gameName: String): View? {
        if (bloggerHtml.isBlank()) return null
        val parsed = JSONArray()
        val anchorPattern = Regex("(?is)<a\\b([^>]*)>(.*?)</a\\s*>")
        anchorPattern.findAll(bloggerHtml).forEachIndexed { index, match ->
            val attributes = match.groupValues[1]
            val hrefMatch = Regex("(?is)\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
                .find(attributes)
            val rawHref = hrefMatch?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
                ?.trim().orEmpty()
            val href = android.text.Html.fromHtml(rawHref, android.text.Html.FROM_HTML_MODE_LEGACY)
                .toString().trim()
            val label = android.text.Html.fromHtml(
                match.groupValues[2], android.text.Html.FROM_HTML_MODE_LEGACY
            ).toString().replace(Regex("\\s+"), " ").trim()
            val normalizedLabel = label.lowercase()
            if (!(href.startsWith("https://", ignoreCase = true) || href.startsWith("http://", ignoreCase = true)) ||
                href == "#" || href.endsWith("/#") || label.isBlank() ||
                normalizedLabel.contains("ضع رابط") ||
                normalizedLabel.contains("ضع الرابط") ||
                normalizedLabel.contains("paste link") ||
                normalizedLabel.contains("insert link")) return@forEachIndexed
            parsed.put(org.json.JSONObject().put("url", href).put(
                "label", label.ifBlank { "${translate("تحميل")} ${index + 1}" }
            ))
        }
        return create(parsed.toString(), gameName)
    }

    fun create(downloadLinks: String, gameName: String): View? {
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
            val label = item.optString("label").ifBlank { "${translate("تحميل")} ${index + 1}" }
            downloadRow.addView(TextView(activity).apply {
                text = "⬇  $label"
                contentDescription = label
                textSize = 14f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.rgb(49, 153, 255), Color.rgb(17, 91, 184))
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
                            enqueueDownload(target, downloadTitle)
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
