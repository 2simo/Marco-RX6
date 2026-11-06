package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.marcorx6.library.data.Game

internal class GameDetailsController(
    private val activity: Activity,
    private val games: () -> List<Game>,
    private val bg: () -> Int,
    private val dp: (Int) -> Int,
    private val topBarFactory: GameDetailsTopBarFactory,
    private val identityViewFactory: GameDetailsIdentityViewFactory,
    private val ratingViewFactory: GameRatingViewFactory,
    private val recordViewOnce: (Game) -> Unit,
    private val similarGamesViewFactory: GameSimilarGamesViewFactory,
    private val downloadLinksViewFactory: GameDownloadLinksViewFactory,
    private val isAdmin: () -> Boolean,
    private val showGameDialog: (Game) -> Unit,
    private val deleteGame: (Game) -> Unit,
    private val applyMotionPreferences: (android.view.View) -> Unit,
    private val applyUnifiedSkin: (android.view.View) -> Unit,
    private val translate: (String) -> String
) {
    fun show(game: Game) {
        recordViewOnce(game)
        val reader = android.app.Dialog(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg())
        }

        root.addView(topBarFactory.create(game) { reader.dismiss() })

        val contentScroll = ScrollView(activity).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        contentScroll.addView(content, android.widget.FrameLayout.LayoutParams(-1, -2))
        root.addView(contentScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val identity = identityViewFactory.create(game)
        content.addView(identity, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(14), dp(12), dp(14), dp(8))
        })
        content.addView(ratingViewFactory.create(game), LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(14), dp(0), dp(14), dp(12))
        })

        // Similar titles are displayed as image-cover cards immediately after
        // the game's information card, without restoring the removed summary.
        similarGamesViewFactory.create(game, games()) { selected ->
            reader.dismiss()
            show(selected)
        }?.let { suggestions ->
            content.addView(suggestions, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            })
        }

        // Management actions are visible only in an authenticated admin session.
        if (isAdmin()) {
            val adminActions = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(14), dp(5), dp(14), dp(10))
            }
            fun adminButton(label: String, action: () -> Unit, color: Int): TextView =
                TextView(activity).apply {
                    text = translate(label)
                    textSize = 14f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setTypeface(null, Typeface.BOLD)
                    setPadding(dp(12), dp(13), dp(12), dp(13))
                    background = GradientDrawable().apply {
                        setColor(color)
                        cornerRadius = dp(12).toFloat()
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { action() }
                }
            adminActions.addView(
                adminButton("تعديل", { reader.dismiss(); showGameDialog(game) }, Color.rgb(35, 126, 218)),
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) }
            )
            adminActions.addView(
                adminButton("حذف", { reader.dismiss(); deleteGame(game) }, Color.rgb(190, 55, 67)),
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) }
            )
            content.addView(adminActions, LinearLayout.LayoutParams(-1, -2))
        }

        val sourceUrl = game.sourcePostUrl.ifBlank { game.url }
        val bloggerDownloadButtons = downloadLinksViewFactory.createFromHtml(
            game.bloggerHtml, game.name, sourceUrl
        )
        val downloadButtons = bloggerDownloadButtons
            ?: downloadLinksViewFactory.create(game.downloadLinks, game.name, sourceUrl)
        downloadButtons?.let { buttons ->
            content.addView(buttons, LinearLayout.LayoutParams(-1, -2))
        }

        applyMotionPreferences(root)
        applyUnifiedSkin(root)
        reader.setContentView(root)
        reader.window?.setBackgroundDrawableResource(android.R.color.transparent)
        reader.show()
        reader.window?.setLayout(-1, -1)
    }
}
