package com.marcorx6.library.data

import java.util.Locale

internal data class GameMergeResult(
    val added: Int,
    val updated: Int,
    val warnings: List<Pair<Game, String>>
)

/**
 * Pure library operations. It never owns Android views or persistence.
 */
internal object GameLibraryManager {
    fun merge(current: MutableList<Game>, imported: List<Game>): GameMergeResult {
        var added = 0
        var updated = 0
        val warnings = mutableListOf<Pair<Game, String>>()

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
                    hasPlatformMismatch(incoming)?.let { warnings.add(incoming to it) }
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

    private fun hasPlatformMismatch(game: Game): String? {
        val regex = Regex(
            "(?i)(?:نظام\\s*التشغيل|operating\\s*system)" +
                "\\s*[:：\\-]?\\s*(PlayStation\\s*[1-4]|PS[1-4]|PSP)"
        )
        val found = regex.find(game.description)?.groupValues?.getOrNull(1) ?: return null
        val normalized = found.lowercase(Locale.ROOT).replace(" ", "").replace("-", "")
        val fieldPlatform = when {
            normalized == "psp" || normalized.contains("playstationportable") -> "PSP"
            normalized == "ps1" || normalized.contains("playstation1") || normalized.contains("playstationone") -> "PS1"
            normalized == "ps2" || normalized.contains("playstation2") -> "PS2"
            normalized == "ps3" || normalized.contains("playstation3") -> "PS3"
            normalized == "ps4" || normalized.contains("playstation4") -> "PS4"
            else -> return null
        }
        return fieldPlatform.takeIf { it != game.platform }
    }
}
