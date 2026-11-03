package com.marcorx6.library.network

import com.marcorx6.library.data.Game
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class BloggerFeedResult(
    val blogId: String,
    val games: List<Game>
)

/**
 * Downloads and parses the public Blogger feed without depending on Android UI state.
 */
internal class BloggerFeedClient(
    private val feedUrl: String
) {
    fun fetch(): BloggerFeedResult {
        val connection = URL(feedUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("User-Agent", "MarcoRX6-Android/3.0")
        connection.setRequestProperty("Accept", "application/json")

        val body = try {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }

        val feed = JSONObject(body).optJSONObject("feed") ?: JSONObject()
        val feedId = feed.optJSONObject("id")
            ?.optString(36.toChar().toString() + "t", "")
            .orEmpty()
        val blogId = Regex("blog-(\\d+)").find(feedId)
            ?.groupValues?.getOrNull(1).orEmpty()

        val entries = feed.optJSONArray("entry") ?: JSONArray()
        val games = buildList {
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                BloggerFeedParser.parseBlogEntry(entry)?.let(::add)
            }
        }
        return BloggerFeedResult(blogId = blogId, games = games)
    }
}
