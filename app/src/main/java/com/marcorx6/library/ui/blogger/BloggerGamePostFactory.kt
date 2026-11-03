package com.marcorx6.library.ui

import com.marcorx6.library.network.BloggerArticleBuilder
import com.marcorx6.library.network.BloggerFeedParser

import com.marcorx6.library.data.Game

internal class BloggerGamePostFactory {
    fun create(
        existing: Game?,
        title: String,
        labels: List<String>,
        description: String,
        coverLink: String,
        relatedLink: String,
        genre: String,
        releaseYear: String,
        pendingCoverLocalPath: String,
        pendingCoverPublicUrl: String
    ): Game {
        val publishedCoverLink = BloggerFeedParser.normalizeBloggerImageUrl(coverLink)
        val normalizedGenre = genre.trim()
        val normalizedYear = releaseYear.trim()
        val htmlContent = BloggerArticleBuilder.build(
            title,
            labels.joinToString("، "),
            normalizedGenre,
            normalizedYear,
            description,
            publishedCoverLink,
            relatedLink
        )
        return Game(
            name = title,
            platform = labels.firstOrNull() ?: "",
            description = description,
            url = relatedLink.ifBlank { existing?.url ?: "" },
            coverUrl = publishedCoverLink,
            coverLocalPath = if (
                coverLink.isBlank() ||
                coverLink == pendingCoverPublicUrl ||
                (existing != null && coverLink == existing.coverUrl)
            ) pendingCoverLocalPath else "",
            sourcePostUrl = existing?.sourcePostUrl ?: "",
            downloadLinks = existing?.downloadLinks ?: "",
            blogImported = true,
            isFavorite = existing?.isFavorite ?: false,
            bloggerPostId = existing?.bloggerPostId ?: "",
            bloggerHtml = htmlContent,
            playStatus = existing?.playStatus ?: "",
            genre = normalizedGenre,
            releaseYear = normalizedYear
        )
    }
}
