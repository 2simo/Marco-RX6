package com.marcorx6.library.data

import java.util.Locale

internal data class GameMergeResult(
    val added: Int,
    val updated: Int,
    val warnings: List<Game>
)

/**
 * Pure library operations. It never owns Android views or persistence.
 */
internal object GameLibraryManager {
    fun merge(current: MutableList<Game>, imported: List<Game>): GameMergeResult {
        var added = 0
        var updated = 0
        val warnings = mutableListOf<Game>()

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
                    playStatus = old.playStatus,
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
                    if (hasPlatformMismatch(incoming)) warnings.add(incoming)
                }
            }
        }
        return GameMergeResult(added, updated, warnings)
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
                "WANT_TO_PLAY" -> item.playStatus == "WANT_TO_PLAY"
                "PLAYING" -> item.playStatus == "PLAYING"
                "COMPLETED" -> item.playStatus == "COMPLETED"
                "BLOG" -> item.blogImported
                else -> item.platform == category
            }
            val searchMatch = item.name.contains(query, ignoreCase = true) ||
                item.platform.contains(query, ignoreCase = true) ||
                item.description.contains(query, ignoreCase = true)
            val genreMatch = genre.isBlank() || item.genre.equals(genre, ignoreCase = true)
            val yearMatch = year.isBlank() || item.releaseYear == year
            categoryMatch && searchMatch && genreMatch && yearMatch
        }
        return when (sortMode) {
            "A-Z" -> filtered.sortedBy { it.name.lowercase(Locale.ROOT) }
            "FAVORITES" -> filtered.sortedByDescending { it.isFavorite }
            "NEW" -> filtered.reversed()
            else -> filtered
        }
    }

    private fun hasPlatformMismatch(game: Game): Boolean {
        val regex = Regex(
            "(?i)(?:نظام\\s*التشغيل|operating\\s*system)" +
                "\\s*[:：\\-]?\\s*(PlayStation\\s*[1-4]|PS[1-4]|PSP)"
        )
        val found = regex.find(game.description)?.groupValues?.getOrNull(1) ?: return false
        val fieldPlatform = when (found.replace(" ", "").uppercase(Locale.ROOT)) {
            "PLAYSTATION1", "PS1" -> "PS1"
            "PLAYSTATION2", "PS2" -> "PS2"
            "PLAYSTATION3", "PS3" -> "PS3"
            "PLAYSTATION4", "PS4" -> "PS4"
            "PSP" -> "PSP"
            else -> return false
        }
        return fieldPlatform != game.platform
    }
}
