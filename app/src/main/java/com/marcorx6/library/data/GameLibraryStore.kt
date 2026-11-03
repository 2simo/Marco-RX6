package com.marcorx6.library.data

/**
 * Coordinates local library mutations and persists each completed change.
 * The UI remains responsible for rendering and user interaction.
 */
internal class GameLibraryStore(
    private val games: MutableList<Game>,
    private val persist: () -> Unit
) {
    fun replaceAll(replacement: List<Game>) {
        games.clear()
        games.addAll(replacement)
        persist()
    }

    fun upsert(game: Game, existing: Game? = null) {
        val index = existing?.let(games::indexOf) ?: -1
        if (index >= 0) {
            games[index] = game
        } else {
            games.add(0, game)
        }
        persist()
    }

    fun correctPlatform(sourcePostUrl: String, platform: String): Boolean {
        val index = games.indexOfFirst { it.sourcePostUrl == sourcePostUrl }
        if (index < 0) return false
        games[index] = games[index].copy(
            platform = platform,
            platformManuallyCorrected = true
        )
        persist()
        return true
    }

    fun remove(game: Game): Boolean {
        val removed = games.remove(game)
        if (removed) persist()
        return removed
    }

    fun mergeImported(imported: List<Game>): GameMergeResult {
        val result = GameLibraryManager.merge(games, imported)
        persist()
        return result
    }
}
