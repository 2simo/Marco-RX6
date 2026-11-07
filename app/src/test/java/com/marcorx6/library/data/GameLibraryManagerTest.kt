package com.marcorx6.library.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLibraryManagerTest {
    private fun game(name: String, platform: String, favorite: Boolean = false, imported: Boolean = true) =
        Game(name, platform, "Description", "https://example.com/$name",
            sourcePostUrl = if (imported) "https://example.com/$name" else "",
            blogImported = imported, isFavorite = favorite)

    @Test fun mergePreservesManualCorrectionAndFavoriteState() {
        val current = mutableListOf(game("Game A", "PS2", true).copy(
            platformManuallyCorrected = true))
        val result = GameLibraryManager.merge(current, listOf(game("Game A", "PS4")))
        assertEquals(0, result.added)
        assertEquals(1, result.updated)
        assertEquals("PS2", current.single().platform)
        assertTrue(current.single().platformManuallyCorrected)
        assertTrue(current.single().isFavorite)
    }

    @Test fun mergeSkipsDuplicateManualEntryAndAddsNewPost() {
        val current = mutableListOf(game("Local Game", "PS3", imported = false))
        val result = GameLibraryManager.merge(current, listOf(
            game("Local Game", "PS3"), game("New Game", "PS4")))
        assertEquals(1, result.added)
        assertEquals(0, result.updated)
        assertEquals(2, current.size)
    }

    @Test fun legacyPlatformsRemainInAllLibraryButNotCurrentPlatformFilter() {
        val legacy = game("Legacy PSP", "PSP")
        val current = game("Current PS3", "PS3", true)
        val games = listOf(legacy, current)
        assertEquals(2, GameLibraryManager.filterAndSort(games, "ALL", "", "", "", "الأحدث").size)
        assertEquals(listOf(current), GameLibraryManager.filterAndSort(games, "PS3", "", "", "", "الأحدث"))
        assertEquals(listOf(current), GameLibraryManager.filterAndSort(games, "FAVORITES", "", "", "", "الأحدث"))
    }

    @Test fun searchGenreYearAndSortWorkTogether() {
        val alpha = game("Alpha", "PS2").copy(genre = "Action", releaseYear = "2020")
        val beta = game("Beta", "PS4").copy(genre = "RPG", releaseYear = "2022")
        assertEquals(listOf(alpha), GameLibraryManager.filterAndSort(
            listOf(beta, alpha), "ALL", "Alpha", "Action", "2020", "A-Z"))
    }

    @Test fun searchFindsGamesByGenreAndReleaseYear() {
        val action = game("Alpha", "PS2").copy(genre = "Action", releaseYear = "2020")
        val rpg = game("Beta", "PS4").copy(genre = "RPG", releaseYear = "2022")
        assertEquals(listOf(action), GameLibraryManager.filterAndSort(
            listOf(rpg, action), "ALL", "action", "", "", "LATEST"))
        assertEquals(listOf(rpg), GameLibraryManager.filterAndSort(
            listOf(action, rpg), "ALL", "2022", "", "", "LATEST"))
    }

    @Test fun platformFilterIgnoresLetterCaseAndSurroundingSpaces() {
        val ps2 = game("Alpha", "PS2")
        assertEquals(listOf(ps2), GameLibraryManager.filterAndSort(
            listOf(ps2), " ps2 ", "", "", "", "LATEST"))
    }

    @Test fun searchAndAdvancedFiltersTrimUserInput() {
        val alpha = game("Alpha", "PS2").copy(genre = "Action", releaseYear = "2020")
        assertEquals(listOf(alpha), GameLibraryManager.filterAndSort(
            listOf(alpha), " ps2 ", " Alpha ", " Action ", " 2020 ", "A-Z"))
    }

    @Test fun favoritesSortPlacesFavoriteGamesFirst() {
        val regular = game("Regular", "PS2")
        val favorite = game("Favorite", "PS3", favorite = true)
        assertEquals(listOf(favorite, regular), GameLibraryManager.filterAndSort(
            listOf(regular, favorite), "ALL", "", "", "", "FAVORITES"))
    }
}
