package com.marcorx6.library.ui

import android.content.Context
import com.marcorx6.library.data.Game
import com.marcorx6.library.network.BloggerFeedClient
import java.util.concurrent.Executor

/** Composes Blogger feed retrieval, merge, access refresh, and library refresh. */
internal class BlogSyncFeature(
    context: Context,
    executor: Executor,
    isSyncing: () -> Boolean,
    setSyncing: (Boolean) -> Unit,
    feedUrl: String,
    postToUi: (() -> Unit) -> Unit,
    merge: (List<Game>) -> Pair<Int, Int>,
    setBlogId: (String) -> Unit,
    refreshAccess: () -> Unit,
    renderGames: () -> Unit
) {
    private val controller = BlogSyncController(
        context = context,
        executor = executor,
        isSyncing = isSyncing,
        setSyncing = setSyncing,
        fetch = {
            val result = BloggerFeedClient(feedUrl).fetch()
            result.blogId to result.games
        },
        postToUi = postToUi,
        merge = merge,
        setBlogId = setBlogId,
        refreshAccess = refreshAccess,
        renderGames = renderGames
    )

    fun sync(showResult: Boolean) = controller.sync(showResult)
}
