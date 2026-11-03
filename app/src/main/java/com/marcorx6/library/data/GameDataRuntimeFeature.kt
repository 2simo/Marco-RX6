package com.marcorx6.library.data

import com.marcorx6.library.ui.*

import androidx.activity.ComponentActivity

/**
 * Owns local game persistence, library mutations and offline catalog recovery.
 */
internal class GameDataRuntimeFeature(
    private val activity: ComponentActivity,
) {
    val games: MutableList<Game> = mutableListOf()

    private val gameRepository by lazy { GameRepository(activity.applicationContext) }

    private val persistenceController by lazy {
        GamePersistenceController(gameRepository, games) { loadOfflineCatalog() }
    }

    val libraryStore by lazy {
        GameLibraryStore(games) { saveGames() }
    }

    fun saveGames() = persistenceController.save()
    fun loadGames() = persistenceController.load()

    fun loadOfflineCatalog() {
        OfflineCatalogLoader.load(activity, games) { saveGames() }
    }
}
