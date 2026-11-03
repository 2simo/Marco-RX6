package com.marcorx6.library.ui

import com.marcorx6.library.data.Game
import com.marcorx6.library.data.GameRepository

internal class GamePersistenceController(
    private val repository: GameRepository,
    private val games: MutableList<Game>,
    private val loadOfflineCatalog: () -> Unit
) {
    fun save() {
        repository.save(games)
    }

    fun load() {
        games.clear()
        try {
            val savedGames = repository.load()
            if (savedGames != null) {
                games.addAll(savedGames)
                return
            }
            loadOfflineCatalog()
        } catch (_: Exception) {
            // Recover the bundled starter catalog if persisted data is malformed.
            games.clear()
            try {
                loadOfflineCatalog()
            } catch (_: Exception) {
                games.clear()
            }
        }
    }
}
