package com.marcorx6.library.ui

import android.app.Activity
import android.widget.LinearLayout
import android.widget.ScrollView
import com.marcorx6.library.data.Game

internal class GameDetailsController(
    private val activity: Activity,
    private val bg: () -> Int,
    private val dp: (Int) -> Int,
    private val topBarFactory: GameDetailsTopBarFactory,
    private val identityViewFactory: GameDetailsIdentityViewFactory,
    private val downloadLinksViewFactory: GameDownloadLinksViewFactory,
    private val applyMotionPreferences: (android.view.View) -> Unit,
    private val applyUnifiedSkin: (android.view.View) -> Unit
) {
    fun show(game: Game) {
        val reader = android.app.Dialog(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg())
        }

        val topBar = topBarFactory.create(game) { reader.dismiss() }
        root.addView(topBar)

        // Keep the cover, information card, and download buttons scrollable on
        // shorter screens while the title bar remains visible.
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
            setMargins(dp(14), dp(12), dp(14), dp(12))
        })

        // The game page is intentionally limited to the cover + one information card
        // + native download buttons. Blogger HTML is used only to extract metadata
        // and direct download links; the article body is not displayed here.
        val bloggerDownloadButtons = downloadLinksViewFactory.createFromHtml(game.bloggerHtml, game.name, game.sourcePostUrl.ifBlank { game.url })
        val downloadButtons = bloggerDownloadButtons
            ?: downloadLinksViewFactory.create(game.downloadLinks, game.name, game.sourcePostUrl.ifBlank { game.url })
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
