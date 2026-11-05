package com.marcorx6.library.ui.blog

import com.marcorx6.library.ui.*

import android.widget.EditText
import android.widget.ImageView
import androidx.activity.ComponentActivity
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.marcorx6.library.data.Game
import com.marcorx6.library.network.BloggerFeedClient
import com.marcorx6.library.data.GameLibraryStore
import java.util.concurrent.ExecutorService

/**
 * Owns Blogger access, synchronization and post composition.
 * Google account and UI state remain supplied through narrow callbacks.
 */
internal class BloggerRuntimeFeature(
    activity: ComponentActivity,
    services: BloggerServices,
    private val executor: ExecutorService,
    currentAccount: () -> GoogleSignInAccount?,
    currentBlogId: () -> String,
    setAdmin: (Boolean) -> Unit,
    isAdminSession: () -> Boolean,
    private val feedUrl: String = "https://marcorx6.blogspot.com/feeds/posts/default?alt=json&max-results=100",
    private val postToUi: (() -> Unit) -> Unit,
    refreshUi: () -> Unit,
    private val merge: (List<Game>) -> Pair<Int, Int>,
    setBlogId: (String) -> Unit,
    refreshAccess: () -> Unit,
    private val renderGames: () -> Unit,
    gameLibraryStore: GameLibraryStore,
    dp: (Int) -> Int,
    bg: () -> Int,
    isDarkMode: () -> Boolean,
    panel: () -> Int,
    white: () -> Int,
    gray: () -> Int,
    red: () -> Int,
    makeText: (String, Float, Int, Boolean) -> android.widget.TextView,
    makeInput: (String) -> android.widget.EditText,
    rounded: (Int, Int) -> android.graphics.drawable.GradientDrawable,
    accentForeground: () -> Int,
    executeIo: (() -> Unit) -> Unit,
    styleAlertDialog: (android.app.AlertDialog) -> Unit,
    applyUnifiedSkin: (android.view.View) -> Unit,
    setCoverFields: (EditText, ImageView) -> Unit,
    pendingCoverLocalPath: () -> String,
    setPendingCoverLocalPath: (String) -> Unit,
    pendingCoverPublicUrl: () -> String,
    setPendingCoverPublicUrl: (String) -> Unit,
    coverUploadInProgress: () -> Boolean,
    loadCover: (ImageView, String, String) -> Unit,
    chooseCover: () -> Unit,
    deleteCoverFileIfUnused: (String) -> Unit,
    resetCoverUploadFailed: () -> Unit,
    saveGames: () -> Unit,
    translate: (String) -> String
) {
    private var syncing = false

    val isSyncing: Boolean get() = syncing

    private val bloggerAccessFeature by lazy {
        BloggerAccessFeature(
            context = activity,
            services = services,
            executor = executor,
            currentAccount = currentAccount,
            currentBlogId = currentBlogId,
            setAdmin = setAdmin,
            postToUi = postToUi,
            refreshUi = refreshUi,
            translate = translate
        )
    }

    private val blogSyncFeature by lazy {
        BlogSyncFeature(
            context = activity,
            executor = executor,
            isSyncing = { syncing },
            setSyncing = { syncing = it },
            feedUrl = feedUrl,
            postToUi = postToUi,
            merge = merge,
            setBlogId = setBlogId,
            refreshAccess = refreshAccess,
            renderGames = renderGames,
            translate = translate
        )
    }

    private val bloggerPostFeature by lazy {
        BloggerPostFeature(
            activity = activity,
            services = services,
            gameLibraryStore = gameLibraryStore,
            isAdminSession = isAdminSession,
            bloggerBlogId = currentBlogId,
            dp = dp,
            isDarkMode = isDarkMode,
            bg = bg,
            panel = panel,
            white = white,
            gray = gray,
            red = red,
            makeText = makeText,
            makeInput = makeInput,
            rounded = rounded,
            accentForeground = accentForeground,
            executeIo = executeIo,
            postToUi = postToUi,
            styleAlertDialog = styleAlertDialog,
            applyUnifiedSkin = applyUnifiedSkin,
            setCoverFields = setCoverFields,
            pendingCoverLocalPath = pendingCoverLocalPath,
            setPendingCoverLocalPath = setPendingCoverLocalPath,
            pendingCoverPublicUrl = pendingCoverPublicUrl,
            setPendingCoverPublicUrl = setPendingCoverPublicUrl,
            coverUploadInProgress = coverUploadInProgress,
            loadCover = loadCover,
            chooseCover = chooseCover,
            deleteCoverFileIfUnused = deleteCoverFileIfUnused,
            resetCoverUploadFailed = resetCoverUploadFailed,
            saveGames = saveGames,
            renderGames = renderGames,
            translate = translate
        )
    }

    /**
     * Refreshes the public feed in the background and returns the newest cached game
     * record for an in-app recovery flow. The Blogger page itself is never displayed.
     */
    fun refreshGame(sourcePostUrl: String, onComplete: (Game?) -> Unit) {
        executor.execute {
            val refreshed = runCatching {
                BloggerFeedClient(feedUrl).fetch().games.firstOrNull { game ->
                    game.sourcePostUrl == sourcePostUrl || game.url == sourcePostUrl
                }
            }.getOrNull()
            postToUi {
                if (refreshed != null) {
                    merge(listOf(refreshed))
                    renderGames()
                }
                onComplete(refreshed)
            }
        }
    }

    fun refreshAccess() = bloggerAccessFeature.refresh()
    fun sync(showResult: Boolean) = blogSyncFeature.sync(showResult)
    fun showComposer(existing: Game? = null) = bloggerPostFeature.showComposer(existing)
    fun delete(game: Game) = bloggerPostFeature.delete(game)
}
