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
                    .put("playStatus", game.playStatus)
                    .put("genre", game.genre)
                    .put("releaseYear", game.releaseYear)
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
                        item.optString("name"),
                        item.optString("platform"),
                        item.optString("description"),
                        item.optString("url"),
                        item.optString("coverUrl", ""),
                        item.optString("coverLocalPath", ""),
                        item.optString("sourcePostUrl", ""),
                        item.optString("downloadLinks", ""),
                        item.optBoolean("blogImported", false),
                        item.optBoolean("platformManuallyCorrected", false),
                        item.optBoolean("isFavorite", false),
                        item.optString("bloggerPostId", ""),
                        item.optString("bloggerHtml", ""),
                        item.optString("playStatus", ""),
                        item.optString("genre", ""),
                        item.optString("releaseYear", "")
                    )
                )
            }
        }
    }
}
