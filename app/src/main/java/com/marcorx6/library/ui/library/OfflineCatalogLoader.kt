package com.marcorx6.library.ui

import android.content.Context
import com.marcorx6.library.data.Game
import com.marcorx6.library.network.BloggerFeedParser
import org.json.JSONArray

internal object OfflineCatalogLoader {
    fun load(context: Context, games: MutableList<Game>, saveGames: () -> Unit) {
        val raw = context.assets.open("offline_catalog.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val entries = JSONArray(raw)
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            BloggerFeedParser.parseBlogEntry(entry)?.let { games.add(it) }
        }
        if (games.isNotEmpty()) saveGames()
    }
}
