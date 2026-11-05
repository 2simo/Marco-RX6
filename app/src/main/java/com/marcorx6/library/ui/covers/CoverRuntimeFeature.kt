package com.marcorx6.library.ui.covers

import com.marcorx6.library.ui.*

import android.content.Context
import android.widget.ImageView
import com.marcorx6.library.data.CoverRepository
import com.marcorx6.library.data.Game
import com.marcorx6.library.network.BloggerFeedParser
import java.io.File

/** Owns the cover cache lifecycle and the UI-facing cover loading operations. */
internal class CoverRuntimeFeature(
    context: Context,
    private val filesDir: File,
    private val games: () -> List<Game>
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val repository = CoverRepository(appContext)
    private val displayController = CoverDisplayController(
        normalizeUrl = BloggerFeedParser::normalizeBloggerImageUrl,
        resolveKey = { localPath, imageUrl ->
            CoverKeyResolver.resolve(localPath, imageUrl, repository::stableCacheName)
        },
        loadBitmap = { localPath, imageUrl, callback ->
            repository.load(localPath, imageUrl, callback)
        }
    )

    fun load(image: ImageView, localPath: String, imageUrl: String) =
        displayController.load(image, localPath, imageUrl)

    fun stableCacheName(value: String): String = repository.stableCacheName(value)

    fun deleteIfUnused(path: String) {
        CoverFileCleanup.deleteIfUnused(
            File(filesDir, "covers"),
            path,
            games().map { it.coverLocalPath }
        )
    }

    override fun close() = repository.close()
}
