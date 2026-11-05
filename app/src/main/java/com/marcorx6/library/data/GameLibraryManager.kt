package com.marcorx6.library.data

import java.util.Locale

internal data class GameMergeResult(
    val added: Int,
    val updated: Int,
)

/**
 * Pure library operations. It never owns Android views or persistence.
 */
internal object GameLibraryManager {
    fun merge(current: MutableList<Game>, imported: List<Game>): GameMergeResult {
        var added = 0
        var updated = 0

        imported.forEach { incoming ->
            val byPost = current.indexOfFirst {
                it.sourcePostUrl.isNotBlank() && it.sourcePostUrl == incoming.sourcePostUrl
            }
            if (byPost >= 0) {
                val old = current[byPost]
                current[byPost] = incoming.copy(
                    coverLocalPath = old.coverLocalPath,
                    coverUrl = if (old.coverLocalPath.isNotBlank()) old.coverUrl else incoming.coverUrl,
                    platform = if (old.platformManuallyCorrected) old.platform else incoming.platform,
                    platformManuallyCorrected = old.platformManuallyCorrected,
                    isFavorite = old.isFavorite,
                    genre = old.genre.ifBlank { incoming.genre },
                    releaseYear = old.releaseYear.ifBlank { incoming.releaseYear },
                    bloggerPostId = incoming.bloggerPostId,
                    bloggerHtml = incoming.bloggerHtml
                )
                updated++
            } else {
                val manualDuplicate = current.any {
                    !it.blogImported && it.name.equals(incoming.name, ignoreCase = true)
                }
                if (!manualDuplicate) {
                    current.add(incoming)
                    added++
                }
            }
        }
        return GameMergeResult(added, updated)
    }

    fun filterAndSort(
        games: List<Game>,
        category: String,
        query: String,
        genre: String,
        year: String,
        sortMode: String
    ): List<Game> {
        val filtered = games.filter { item ->
            val categoryMatch = when (category) {
                "ALL" -> true
                "FAVORITES" -> item.isFavorite
                "BLOG" -> item.blogImported
                else -> item.platform == category
            }
            val normalizedQuery = query.trim()
            val searchMatch = normalizedQuery.isBlank() ||
                item.name.contains(normalizedQuery, ignoreCase = true) ||
                item.platform.contains(normalizedQuery, ignoreCase = true) ||
                item.description.contains(normalizedQuery, ignoreCase = true) ||
                item.genre.contains(normalizedQuery, ignoreCase = true) ||
                item.releaseYear.contains(normalizedQuery, ignoreCase = true)
            val genreMatch = genre.isBlank() || item.genre.trim().equals(genre.trim(), ignoreCase = true)
            val yearMatch = year.isBlank() || item.releaseYear.trim() == year.trim()
            categoryMatch && searchMatch && genreMatch && yearMatch
        }
        return when (sortMode) {
            "A-Z" -> filtered.sortedBy { it.name.lowercase(Locale.ROOT) }
            "FAVORITES" -> filtered.sortedByDescending { it.isFavorite }
            "NEW" -> filtered.reversed()
            else -> filtered
        }
    }

}
