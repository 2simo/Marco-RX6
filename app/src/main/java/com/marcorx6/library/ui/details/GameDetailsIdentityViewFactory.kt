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
        val muted = if (dark) Color.rgb(178, 184, 194) else Color.rgb(91, 98, 110)
        val primary = if (dark) Color.rgb(244, 247, 252) else Color.rgb(25, 29, 36)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        // A dedicated identity card: cover first, then the game title.
        val gameCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                if (dark) intArrayOf(Color.rgb(25, 28, 34), Color.rgb(8, 9, 12))
                else intArrayOf(Color.WHITE, Color.rgb(242, 244, 247))
            ).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), if (dark) Color.argb(170, 130, 138, 151) else Color.rgb(160, 168, 181))
            }
            elevation = dp(3).toFloat()
        }

        val cover = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            background = GradientDrawable().apply {
                setColor(if (dark) Color.rgb(35, 38, 45) else Color.rgb(222, 226, 232))
                cornerRadius = dp(15).toFloat()
            }
            clipToOutline = true
            contentDescription = "${translate("غلاف اللعبة")} ${game.name}"
        }
        if (game.coverUrl.isNotBlank() || game.coverLocalPath.isNotBlank()) {
            loadCover(cover, game.coverLocalPath, game.coverUrl)
        } else {
            cover.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        gameCard.addView(cover, LinearLayout.LayoutParams(dp(180), dp(270)).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        })
        gameCard.addView(makeText(game.name, 19f, primary, true).apply {
            gravity = android.view.Gravity.CENTER
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            maxLines = 3
            setPadding(dp(4), dp(13), dp(4), dp(2))
            contentDescription = game.name
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(gameCard, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        })

        val details = extractGameDetails(game)
        if (details.isNotEmpty()) {
            val infoCard = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), dp(12), dp(13), dp(12))
                background = GradientDrawable().apply {
                    setColor(if (dark) Color.rgb(25, 28, 34) else Color.rgb(242, 244, 247))
                    cornerRadius = dp(18).toFloat()
                    setStroke(dp(1), if (dark) Color.rgb(75, 80, 90) else Color.rgb(211, 215, 222))
                }
                elevation = dp(2).toFloat()
            }
            infoCard.addView(makeText(translate("معلومات اللعبة"), 16f, primary, true).apply {
                setPadding(0, 0, 0, dp(10))
            })
            details.forEach { item ->
                val cell = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = GradientDrawable().apply {
                        setColor(if (dark) Color.rgb(43, 47, 55) else Color.WHITE)
                        cornerRadius = dp(11).toFloat()
                    }
                }
                cell.addView(makeText(translate(item.first), 12f, muted, false).apply {
                    textDirection = android.view.View.TEXT_DIRECTION_FIRST_STRONG
                    gravity = android.view.Gravity.START
                }, LinearLayout.LayoutParams(-1, -2))
                cell.addView(makeText(item.second, 15f, primary, true).apply {
                    textDirection = android.view.View.TEXT_DIRECTION_FIRST_STRONG
                    gravity = android.view.Gravity.START
                    textAlignment = android.view.View.TEXT_ALIGNMENT_VIEW_START
                    setPadding(0, dp(4), 0, 0)
                    maxLines = Int.MAX_VALUE
                    ellipsize = null
                    setHorizontallyScrolling(false)
                }, LinearLayout.LayoutParams(-1, -2))
                infoCard.addView(cell, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(8)
                })
            }
            root.addView(infoCard, LinearLayout.LayoutParams(-1, -2))
        } else {
            root.addView(makeText(
                translate("لا تتوفر معلومات إضافية لهذه اللعبة"),
                13f,
                muted,
                false
            ).apply {
                gravity = android.view.Gravity.CENTER
                setPadding(dp(12), dp(12), dp(12), dp(12))
            })
        }

        return root
    }

    private fun extractGameDetails(game: Game): List<Pair<String, String>> {
        val html = game.bloggerHtml
        val stored = linkedMapOf(
            "الحجم" to game.packageSize,
            "النسخة" to game.version,
            "الصيغة" to game.fileFormat,
            "اللغة" to game.language,
            "كلمة السر" to game.password,
            "الحقوق" to game.rights,
            "النوع" to game.genre,
            "سنة الإصدار" to game.releaseYear
        ).filterValues { it.isNotBlank() }.toMutableMap()
        if (html.isBlank()) return stored.map { it.key to it.value }
        val fields = listOf(
            "الحجم" to listOf("الحجم", "حجم", "size"),
            "النسخة" to listOf("النسخة", "version", "title id", "cusa"),
            "الصيغة" to listOf("الصيغة", "format"),
            "اللغة" to listOf("اللغة", "language", "languages"),
            "كلمة السر" to listOf("كلمة السر", "كلمه السر", "password", "pass"),
            "الحقوق" to listOf("الحقوق", "credits", "rights"),
            "النوع" to listOf("النوع", "genre", "category"),
            "سنة الإصدار" to listOf("سنة الإصدار", "سنة الاصدار", "release year", "year")
        )
        fun plainText(value: String): String = Html.fromHtml(
            value, Html.FROM_HTML_MODE_LEGACY
        ).toString().replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()

        val entries = linkedMapOf<String, String>().apply { putAll(stored) }
        Regex("(?is)<li\\b[^>]*>(.*?)</li\\s*>").findAll(html).forEach { item ->
            val text = plainText(item.groupValues[1]).trim(' ', ':', '：', '-', '•')
            fields.forEach { (label, aliases) ->
                val alias = aliases.joinToString("|") { Regex.escape(it) }
                val match = Regex("(?i)^\\s*(?:$alias)\\s*[:：-]\\s*(.*?)\\s*$").find(text)
                val value = match?.groupValues?.get(1)?.trim()?.trimEnd('.', ',', ';')
                if (!value.isNullOrBlank()) entries[label] = value
            }
        }

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
        return fields.mapNotNull { (label, _) ->
            entries[label]?.takeIf { it.isNotBlank() }?.let { value -> label to value }
        }
    }
}
