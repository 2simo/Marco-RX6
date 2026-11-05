package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Html
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.marcorx6.library.data.Game

internal class GameDetailsIdentityViewFactory(
    private val activity: Activity,
    private val dp: (Int) -> Int,
    private val makeText: (String, Float, Int, Boolean) -> TextView,
    private val isDarkMode: () -> Boolean,
    private val loadCover: (ImageView, String, String) -> Unit,
    private val translate: (String) -> String
) {
    fun create(game: Game): LinearLayout {
        val dark = isDarkMode()
        val blue = if (dark) Color.rgb(99, 185, 255) else Color.rgb(22, 125, 224)
        val muted = if (dark) Color.rgb(170, 190, 214) else Color.rgb(91, 108, 130)
        val primary = if (dark) Color.rgb(244, 247, 252) else Color.rgb(32, 38, 56)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(13), dp(13), dp(13))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                if (dark) intArrayOf(Color.rgb(16, 28, 45), Color.rgb(12, 21, 35))
                else intArrayOf(Color.WHITE, Color.rgb(235, 244, 255))
            ).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), if (dark) Color.argb(170, 72, 145, 211) else Color.rgb(190, 215, 243))
            }
            elevation = dp(4).toFloat()
        }

        val cover = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            maxHeight = dp(270)
            background = GradientDrawable().apply {
                setColor(if (dark) Color.rgb(13, 34, 57) else Color.rgb(225, 240, 255))
                cornerRadius = dp(15).toFloat()
            }
            clipToOutline = true
            contentDescription = game.name
        }
        if (game.coverUrl.isNotBlank() || game.coverLocalPath.isNotBlank()) {
            loadCover(cover, game.coverLocalPath, game.coverUrl)
        } else {
            cover.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        root.addView(cover, LinearLayout.LayoutParams(dp(180), dp(270)).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        })

        val details = extractGameDetails(game.bloggerHtml)
        if (details.isNotEmpty()) {
            val infoCard = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), dp(12), dp(13), dp(12))
                background = GradientDrawable().apply {
                    setColor(if (dark) Color.rgb(27, 43, 63) else Color.rgb(244, 248, 253))
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), if (dark) Color.rgb(57, 83, 112) else Color.rgb(213, 226, 241))
                }
            }
            infoCard.addView(makeText(translate("معلومات اللعبة"), 16f, primary, true).apply {
                setPadding(0, 0, 0, dp(10))
            })
            details.forEach { item ->
                val cell = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = GradientDrawable().apply {
                        setColor(if (dark) Color.rgb(35, 54, 77) else Color.WHITE)
                        cornerRadius = dp(11).toFloat()
                    }
                }
                cell.addView(makeText(translate(item.first), 12f, muted, false).apply {
                    textDirection = android.view.View.TEXT_DIRECTION_FIRST_STRONG
                    gravity = android.view.Gravity.START
                    includeFontPadding = true
                }, LinearLayout.LayoutParams(-1, -2))
                cell.addView(makeText(item.second, 15f, primary, true).apply {
                    textDirection = android.view.View.TEXT_DIRECTION_FIRST_STRONG
                    gravity = android.view.Gravity.START
                    textAlignment = android.view.View.TEXT_ALIGNMENT_VIEW_START
                    includeFontPadding = true
                    setPadding(0, dp(4), 0, 0)
                    // Keep the complete value visible, even when it needs several lines.
                    maxLines = Int.MAX_VALUE
                    ellipsize = null
                    setHorizontallyScrolling(false)
                }, LinearLayout.LayoutParams(-1, -2))
                infoCard.addView(cell, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(8)
                })
            }
            root.addView(infoCard, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(12)
            })
        }

        return root
    }

    private fun extractGameDetails(html: String): List<Pair<String, String>> {
        if (html.isBlank()) return emptyList()
        val fields = listOf(
            "الحجم" to listOf("الحجم", "حجم", "size"),
            "النسخة" to listOf("النسخة", "version", "title id", "cusa"),
            "الصيغة" to listOf("الصيغة", "format"),
            "اللغة" to listOf("اللغة", "language", "languages"),
            "كلمة السر" to listOf("كلمة السر", "كلمه السر", "password", "pass"),
            "الحقوق" to listOf("الحقوق", "credits", "rights")
        )
        fun plainText(value: String): String = Html.fromHtml(
            value, Html.FROM_HTML_MODE_LEGACY
        ).toString().replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()

        // Blogger commonly puts every field in its own <li>. Parse each item
        // independently so a missing newline cannot merge the next field into it.
        val entries = linkedMapOf<String, String>()
        Regex("(?is)<li\\b[^>]*>(.*?)</li\\s*>").findAll(html).forEach { item ->
            val text = plainText(item.groupValues[1]).trim(' ', ':', '：', '-', '•')
            fields.forEach { (label, aliases) ->
                val alias = aliases.joinToString("|") { Regex.escape(it) }
                val match = Regex("(?i)^\\s*(?:$alias)\\s*[:：-]\\s*(.*?)\\s*$").find(text)
                val value = match?.groupValues?.get(1)?.trim()?.trimEnd('.', ',', ';')
                if (!value.isNullOrBlank()) entries[label] = value
            }
        }

        // Fallback for articles that use paragraphs or plain text rather than list items.
        if (entries.size < fields.size) {
            val plain = plainText(
                html.replace(Regex("(?i)<br\\s*/?>"), "\\n")
                    .replace(Regex("(?i)</(p|div|tr|h[1-6]|blockquote)\\s*>"), "\\n")
            )
            fields.forEach { (label, aliases) ->
                if (entries[label].isNullOrBlank()) {
                    val alias = aliases.joinToString("|") { Regex.escape(it) }
                    val match = Regex("(?i)(?:^|[\\n\\r])\\s*(?:$alias)\\s*[:：-]\\s*([^\\n\\r]+)").find(plain)
                    match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.let {
                        entries[label] = it.trimEnd('.', ',', ';')
                    }
                }
            }
        }
        return fields.map { (label, _) -> label to (entries[label]?.takeIf { it.isNotBlank() } ?: "غير محدد") }
    }
}
