package com.marcorx6.library.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local persistence for the user's MARCO RX6 game library.
 * The JSON keys are kept compatible with backups and existing installations.
 */
internal class GameRepository(context: Context) {
    private val preferences = context.getSharedPreferences("games_library", Context.MODE_PRIVATE)

    fun save(games: List<Game>) {
        val array = JSONArray()
        games.forEach { game ->
            array.put(
                JSONObject()
                    .put("name", game.name)
                    .put("platform", game.platform)
                    .put("description", game.description)
                    .put("url", game.url)
                    .put("coverUrl", game.coverUrl)
                    .put("coverLocalPath", game.coverLocalPath)
                    .put("sourcePostUrl", game.sourcePostUrl)
                    .put("downloadLinks", game.downloadLinks)
                    .put("blogImported", game.blogImported)
                    .put("platformManuallyCorrected", game.platformManuallyCorrected)
                    .put("isFavorite", game.isFavorite)
                    .put("bloggerPostId", game.bloggerPostId)
                    .put("bloggerHtml", game.bloggerHtml)
                    .put("genre", game.genre)
                    .put("releaseYear", game.releaseYear)
                    .put("packageSize", game.packageSize)
                    .put("version", game.version)
                    .put("fileFormat", game.fileFormat)
                    .put("language", game.language)
                    .put("password", game.password)
                    .put("rights", game.rights)
            )
        }
        preferences.edit().putString("games", array.toString()).apply()
    }

    fun load(): List<Game>? {
        val saved = preferences.getString("games", null)
        if (saved.isNullOrBlank()) return null
        val array = JSONArray(saved)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    Game(
                        name = item.optString("name"),
                        platform = item.optString("platform"),
                        description = item.optString("description"),
                        url = item.optString("url"),
                        coverUrl = item.optString("coverUrl", ""),
                        coverLocalPath = item.optString("coverLocalPath", ""),
                        sourcePostUrl = item.optString("sourcePostUrl", ""),
                        downloadLinks = item.optString("downloadLinks", ""),
                        blogImported = item.optBoolean("blogImported", false),
                        platformManuallyCorrected = item.optBoolean("platformManuallyCorrected", false),
                        isFavorite = item.optBoolean("isFavorite", false),
                        bloggerPostId = item.optString("bloggerPostId", ""),
                        bloggerHtml = item.optString("bloggerHtml", ""),
                        genre = item.optString("genre", ""),
                        releaseYear = item.optString("releaseYear", ""),
                        packageSize = item.optString("packageSize", ""),
                        version = item.optString("version", ""),
                        fileFormat = item.optString("fileFormat", ""),
                        language = item.optString("language", ""),
                        password = item.optString("password", ""),
                        rights = item.optString("rights", "")
                    )
                )
            }
        }
    }
}
