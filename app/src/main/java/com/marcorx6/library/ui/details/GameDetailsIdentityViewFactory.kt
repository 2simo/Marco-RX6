package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.Html
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.marcorx6.library.data.Game

internal class GameDetailsIdentityViewFactory(
    private val activity: Activity,
    private val dp: (Int) -> Int,
    private val rounded: (Int, Int) -> Drawable,
    private val makeText: (String, Float, Int, Boolean) -> TextView,
    private val isDarkMode: () -> Boolean,
    private val white: () -> Int,
    private val gray: () -> Int,
    private val loadCover: (ImageView, String, String) -> Unit,
    private val watchlistViewFactory: GameWatchlistViewFactory
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
            scaleType = ImageView.ScaleType.CENTER_CROP
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
        root.addView(cover, LinearLayout.LayoutParams(-1, dp(184)))

        root.addView(makeText(game.name, 21f, primary, true).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(2), dp(13), dp(2), dp(5))
        })
        val platformChip = TextView(activity).apply {
            text = game.platform.uppercase()
            textSize = 11f
            setTextColor(blue)
            setPadding(dp(11), dp(5), dp(11), dp(5))
            background = GradientDrawable().apply {
                setColor(if (dark) Color.rgb(24, 57, 88) else Color.rgb(222, 239, 255))
                cornerRadius = dp(30).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(57, 126, 187) else Color.rgb(153, 202, 246))
            }
        }
        root.addView(platformChip, LinearLayout.LayoutParams(-2, -2).apply {
            bottomMargin = dp(13)
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
            infoCard.addView(makeText("معلومات اللعبة", 16f, white(), true).apply {
                setPadding(0, 0, 0, dp(10))
            })
            details.chunked(2).forEach { pair ->
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                pair.forEach { item ->
                    val cell = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(9), dp(8), dp(7), dp(8))
                        background = GradientDrawable().apply {
                            setColor(if (dark) Color.rgb(35, 54, 77) else Color.WHITE)
                            cornerRadius = dp(11).toFloat()
                        }
                    }
                    cell.addView(makeText(item.first, 10f, muted, false))
                    cell.addView(makeText(item.second, 14f, primary, true).apply {
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setPadding(0, dp(3), 0, 0)
                    })
                    row.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply {
                        if (row.childCount > 0) marginStart = dp(7)
                    })
                }
                if (pair.size == 1) row.addView(LinearLayout(activity), LinearLayout.LayoutParams(0, 1, 1f))
                infoCard.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(7)
                })
            }
            root.addView(infoCard, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(12)
            })
        }

        val watchlistButton = watchlistViewFactory.create(game)
        root.addView(watchlistButton, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(2)
        })
        return root
    }

    private fun extractGameDetails(html: String): List<Pair<String, String>> {
        if (html.isBlank()) return emptyList()
        val normalizedHtml = html
            .replace(Regex("(?i)<br\\s*/?>"), "\\n")
            .replace(Regex("(?i)</(p|div|li|tr|h[1-6]|blockquote)\\s*>"), "\\n")
            .replace(Regex("(?i)<li\\b[^>]*>"), "\\n")
        val plain = Html.fromHtml(normalizedHtml, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace('\u00a0', ' ')
            .replace(Regex("[ \\t]+"), " ")
        val fields = listOf(
            "الحجم" to listOf("الحجم", "حجم", "size"),
            "النسخة" to listOf("النسخة", "version", "title id", "cusa"),
            "الصيغة" to listOf("الصيغة", "format"),
            "اللغة" to listOf("اللغة", "language", "languages"),
            "كلمة السر" to listOf("كلمة السر", "password", "pass"),
            "الحقوق" to listOf("الحقوق", "credits", "rights")
        )
        return fields.mapNotNull { (label, aliases) ->
            val alternatives = aliases.joinToString("|") { Regex.escape(it) }
            val match = Regex(
                "(?im)(?:^|[\\n\\r|•])\\s*(?:$alternatives)\\s*[:：-]\\s*([^\\n\\r|•]*)"
            ).find(plain) ?: return@mapNotNull null
            val value = match.groupValues[1].trim().trimEnd('.', ',', ';')
            if (value.isBlank()) null else label to value
        }
    }
}
