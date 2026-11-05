package com.marcorx6.library.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.FrameLayout
import com.marcorx6.library.data.Game
import java.io.File

internal class GameDetailsController(
    private val activity: Activity,
    private val games: () -> List<Game>,
    private val bg: () -> Int,
    private val dp: (Int) -> Int,
    private val isDarkMode: () -> Boolean,
    private val fontFamilyName: () -> String,
    private val fontScale: () -> Float,
    private val enqueueGameDownload: (String, String, String, String, String, String) -> Unit,
    private val topBarFactory: GameDetailsTopBarFactory,
    private val identityViewFactory: GameDetailsIdentityViewFactory,
    private val similarGamesViewFactory: GameSimilarGamesViewFactory,
    private val articleWebViewFactory: GameArticleWebViewFactory,
    private val downloadLinksViewFactory: GameDownloadLinksViewFactory,
    private val bottomBarFactory: GameDetailsBottomBarFactory,
    private val offlineArchiveController: GameOfflineArchiveController,
    private val filesDir: File,
    private val stableCacheName: (String) -> String,
    private val isAdmin: () -> Boolean,
    private val postToUi: (() -> Unit) -> Unit,
    private val openGameLink: (String) -> Unit,
    private val showGameDialog: (Game) -> Unit,
    private val deleteGame: (Game) -> Unit,
    private val applyMotionPreferences: (android.view.View) -> Unit,
    private val applyUnifiedSkin: (android.view.View) -> Unit,
    private val rounded: (Int, Int) -> android.graphics.drawable.GradientDrawable,
    private val translate: (String) -> String
) {
    fun show(game: Game) {
        val reader = android.app.Dialog(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg())
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safeAreas = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(0, safeAreas.top, 0, safeAreas.bottom)
            insets
        }

        val topBar = topBarFactory.create(game) { reader.dismiss() }
        root.addView(topBar)

        // Keep the details content in a real vertical scroll container. The header
        // and bottom actions remain fixed while long metadata and article content
        // can be reached on shorter screens.
        val contentScroll = ScrollView(activity).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        contentScroll.addView(content, FrameLayout.LayoutParams(-1, -2))
        root.addView(contentScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val identity = identityViewFactory.create(game)
        content.addView(identity, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(14), dp(12), dp(14), dp(12))
        })

        val articleWebView = articleWebViewFactory.create(
            backgroundColor = bg(),
            gameUrl = game.url,
            gameName = game.name,
            bloggerHtml = game.bloggerHtml.ifBlank { "<h2>${game.name}</h2><p>${game.description}</p>" },
            darkMode = isDarkMode(),
            fontFamily = fontFamilyName(),
            fontSize = (17 * fontScale()).toInt(),
            onDownload = { url, userAgent, contentDisposition, mimeType, pageUrl ->
                enqueueGameDownload(url, game.name, userAgent.orEmpty(), contentDisposition.orEmpty(), mimeType.orEmpty(), pageUrl)
            }
        )
        // Put valid Blogger download links directly beneath the game information card.
        // Ignore template placeholders such as href="#" and "ضع رابط تحميل هنا".
        val bloggerDownloadButtons = downloadLinksViewFactory.createFromHtml(game.bloggerHtml, game.name)
        val downloadButtons = bloggerDownloadButtons
            ?: downloadLinksViewFactory.create(game.downloadLinks, game.name)
        downloadButtons?.let { buttons ->
            content.addView(buttons, LinearLayout.LayoutParams(-1, -2))
        }

        // Start collapsed; GameArticleWebViewFactory measures the rendered HTML
        // and expands this view to the actual article height after page load.
        content.addView(articleWebView, LinearLayout.LayoutParams(-1, dp(1)))

        similarGamesViewFactory.create(game, games()) { item ->
            reader.dismiss()
            show(item)
        }?.let { similar ->
            content.addView(similar, LinearLayout.LayoutParams(-1, -2))
        }

        val archiveFolder = File(filesDir, "reader_cache").apply { mkdirs() }
        val archiveFile = File(archiveFolder, stableCacheName(game.url.ifBlank { game.name }) + ".mht")

        val bottom = bottomBarFactory.create(
            offlineLabel = offlineArchiveController.buttonLabel(archiveFile, translate),
            isAdmin = isAdmin(),
            onOffline = {
                offlineArchiveController.openOrSave(
                    webView = articleWebView,
                    archiveFile = archiveFile,
                    postToUi = postToUi
                )
            },
            onShare = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, game.name)
                    putExtra(Intent.EXTRA_TEXT, game.name + "\n" + game.url)
                }
                activity.startActivity(Intent.createChooser(send, translate("مشاركة مقال من MARCO RX6")))
            },
            onComments = { if (game.url.isNotBlank()) openGameLink(game.url) },
            onEdit = { reader.dismiss(); showGameDialog(game) },
            onDelete = { reader.dismiss(); deleteGame(game) }
        )
        root.addView(bottom)
        applyMotionPreferences(root)
        applyUnifiedSkin(root)
        reader.setContentView(root)
        reader.window?.setBackgroundDrawableResource(android.R.color.transparent)
        reader.show()
        reader.window?.let { readerWindow ->
            WindowCompat.setDecorFitsSystemWindows(readerWindow, false)
            readerWindow.statusBarColor = Color.TRANSPARENT
            readerWindow.navigationBarColor = Color.TRANSPARENT
            WindowInsetsControllerCompat(readerWindow, readerWindow.decorView).apply {
                isAppearanceLightStatusBars = !isDarkMode()
                isAppearanceLightNavigationBars = !isDarkMode()
            }
            readerWindow.setLayout(-1, -1)
            ViewCompat.requestApplyInsets(readerWindow.decorView)
        }
        reader.window?.setBackgroundDrawable(rounded(if (isDarkMode()) Color.rgb(7, 13, 25) else Color.rgb(245, 247, 251), 0))
        reader.setOnDismissListener { articleWebView.stopLoading(); articleWebView.destroy() }
    }

}
