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
        pendingCoverPublicUrl: String,
        packageSize: String = "",
        version: String = "",
        fileFormat: String = "PKG",
        language: String = "الإنجليزية",
        password: String = "",
        rights: String = "",
        downloadLinksJson: String = ""
    ): Game {
        val publishedCoverLink = BloggerFeedParser.normalizeBloggerImageUrl(coverLink)
        val normalizedGenre = genre.trim()
        val normalizedYear = releaseYear.trim()
        val links = downloadLinksJson.ifBlank {
            if (relatedLink.isBlank()) existing?.downloadLinks.orEmpty()
            else org.json.JSONArray().put(
                org.json.JSONObject().put("label", "تحميل اللعبة").put("url", relatedLink.trim())
            ).toString()
        }
        val primaryUrl = try {
            org.json.JSONArray(links).optJSONObject(0)?.optString("url").orEmpty()
        } catch (_: Exception) { "" }
        val htmlContent = BloggerArticleBuilder.build(
            title = title,
            categories = labels.joinToString("، "),
            genre = normalizedGenre,
            releaseYear = normalizedYear,
            description = description,
            coverUrl = publishedCoverLink,
            downloadUrl = primaryUrl.ifBlank { relatedLink },
            packageSize = packageSize.trim(),
            version = version.trim(),
            fileFormat = fileFormat.trim(),
            language = language.trim(),
            password = password.trim(),
            rights = rights.trim(),
            downloadLinksJson = links
        )
        return Game(
            name = title,
            platform = labels.firstOrNull() ?: "",
            description = description,
            url = primaryUrl.ifBlank { relatedLink.ifBlank { existing?.url.orEmpty() } },
            coverUrl = publishedCoverLink,
            coverLocalPath = if (
                coverLink.isBlank() ||
                coverLink == pendingCoverPublicUrl ||
                (existing != null && coverLink == existing.coverUrl)
            ) pendingCoverLocalPath else "",
            sourcePostUrl = existing?.sourcePostUrl ?: "",
            downloadLinks = links,
            blogImported = true,
            isFavorite = existing?.isFavorite ?: false,
            bloggerPostId = existing?.bloggerPostId ?: "",
            bloggerHtml = htmlContent,
            genre = normalizedGenre,
            releaseYear = normalizedYear,
            packageSize = packageSize.trim(),
            version = version.trim(),
            fileFormat = fileFormat.trim(),
            language = language.trim(),
            password = password.trim(),
            rights = rights.trim()
        )
    }
}
