package com.marcorx6.library.ui

import android.app.Activity
import android.widget.LinearLayout
import android.widget.ScrollView
import com.marcorx6.library.data.Game
import com.marcorx6.library.ui.details.GameRatingViewFactory

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
        // Keep view recording intact; its counter is presented on library cards, not here.
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
            setPadding(dp(14), dp(12), dp(14), dp(18))
        }
        contentScroll.addView(content, android.widget.FrameLayout.LayoutParams(-1, -2))
        root.addView(contentScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        // Game identity and information, followed by the original download actions.
        content.addView(identityViewFactory.create(game), LinearLayout.LayoutParams(-1, -2))

        val sourceUrl = game.sourcePostUrl.ifBlank { game.url }
        val downloadButtons = downloadLinksViewFactory.createFromHtml(
            game.bloggerHtml, game.name, sourceUrl
        ) ?: downloadLinksViewFactory.create(game.downloadLinks, game.name, sourceUrl)
        downloadButtons?.let { buttons ->
            content.addView(buttons, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(10)
            })
        }

        // Keep user ratings and related games available in the details page.
        content.addView(ratingViewFactory.create(game), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(10)
        })
        similarGamesViewFactory.create(game, games()) { selected ->
            reader.dismiss()
            show(selected)
        }?.let { similar ->
            content.addView(similar, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(10)
            })
        }

        applyMotionPreferences(root)
        applyUnifiedSkin(root)
        reader.setContentView(root)
        reader.window?.setBackgroundDrawableResource(android.R.color.transparent)
        reader.show()
        reader.window?.setLayout(-1, -1)
    }
}
