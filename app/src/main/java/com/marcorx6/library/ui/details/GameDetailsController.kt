package com.marcorx6.library.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.view.ViewCompat
import android.view.WindowInsetsControllerCompat
import android.view.WindowInsets
import android.view.WindowInsets.Type
import android.widget.LinearLayout
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
    private val rounded: (Int, Int) -> android.graphics.drawable.GradientDrawable
) {
    fun show(game: Game) {
        val reader = android.app.Dialog(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safeAreas = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(0, safeAreas.top, 0, safeAreas.bottom)
            insets
        }

        val topBar = gameDetailsTopBarFactory.create(game) { reader.dismiss() }
        root.addView(topBar)

        val identity = gameDetailsIdentityViewFactory.create(game)
        root.addView(identity, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(14), dp(12), dp(14), dp(12))
        })

        gameSimilarGamesViewFactory.create(game, games) { item ->
            reader.dismiss()
            showDetails(item)
        }?.let(root::addView)

        val articleWebView = gameArticleWebViewFactory.create(
            backgroundColor = bg,
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
        root.addView(articleWebView, LinearLayout.LayoutParams(-1, 0, 1f))
        gameDownloadLinksViewFactory.create(game.downloadLinks, game.name)?.let(root::addView)

        val archiveFolder = File(filesDir, "reader_cache").apply { mkdirs() }
        val archiveFile = File(archiveFolder, coverRepository.stableCacheName(game.url.ifBlank { game.name }) + ".mht")

        val bottom = gameDetailsBottomBarFactory.create(
            offlineLabel = gameOfflineArchiveController.buttonLabel(archiveFile),
            isAdmin = isAdminSession,
            onOffline = {
                gameOfflineArchiveController.openOrSave(
                    webView = articleWebView,
                    archiveFile = archiveFile,
                    postToUi = ::postToUi
                )
            },
            onShare = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, game.name)
                    putExtra(Intent.EXTRA_TEXT, game.name + "\n" + game.url)
                }
                startActivity(Intent.createChooser(send, "مشاركة مقال من MARCO RX6"))
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
