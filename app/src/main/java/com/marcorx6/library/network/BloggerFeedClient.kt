package com.marcorx6.library.network

import com.marcorx6.library.data.Game
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal data class BloggerFeedResult(
    val blogId: String,
    val games: List<Game>
)

/**
 * Downloads the main Blogger feed and the dedicated OS-label feeds used by the
 * four independent MARCO RX6 sections. Label feeds are important because the
 * requested sections may contain posts outside the first page of the main feed.
 */
internal class BloggerFeedClient(
    private val feedUrl: String
) {
    private data class SectionFeed(
        val label: String,
        val platform: String,
        val fallbackLabels: List<String> = emptyList()
    )

    private val dedicatedSections = listOf(
        SectionFeed("ps3arabic", "PS3_ARABIC", listOf("PS3 Arabic")),
        SectionFeed("ps2arabic", "PS2_ARABIC", listOf("PS2 ARABIC", "PS2 Arabic")),
        SectionFeed("ps2ps4", "PS2_PORT_PS4", listOf("PS2 PORT PS4")),
        SectionFeed("ps2ps3", "PS2_PORT_PS3", listOf("PS2 PORT PS3"))
    )

    fun fetch(): BloggerFeedResult {
        val main = fetchFeed(feedUrl)
        val merged = LinkedHashMap<String, Game>()

        main.games.forEach { game ->
            val key = game.sourcePostUrl.ifBlank { game.url }
            if (key.isNotBlank()) merged[key] = game
        }

        dedicatedSections.forEach { section ->
            // First try the exact OS label used by the Blogger section URL.
            // If that label is not present, try the human-readable Blogger label.
            val labelsToTry = listOf(section.label) + section.fallbackLabels
            var sectionGames = emptyList<Game>()
            for (label in labelsToTry) {
                val result = runCatching { fetchFeed(labelFeedUrl(label)) }.getOrNull()
                if (result != null && result.games.isNotEmpty()) {
                    sectionGames = result.games
                    break
                }
            }
            sectionGames.forEach { game ->
                val key = game.sourcePostUrl.ifBlank { game.url }
                if (key.isNotBlank()) {
                    // The dedicated feed itself is authoritative for this section,
                    // even when a post also carries a broad PS2/PS3 category.
                    merged[key] = game.copy(platform = section.platform)
                }
            }
        }

        return BloggerFeedResult(
            blogId = main.blogId,
            games = merged.values.toList()
        )
    }

    private fun labelFeedUrl(label: String): String {
        val base = feedUrl.substringBefore('?').trimEnd('/')
        val encodedLabel = URLEncoder.encode(label, "UTF-8").replace("+", "%20")
        return "$base/-/$encodedLabel?alt=json&max-results=100"
    }

    private fun fetchFeed(url: String): BloggerFeedResult {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("User-Agent", "MarcoRX6-Android/3.0")
        connection.setRequestProperty("Accept", "application/json")

        val body = try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            if (status !in 200..299) {
                throw IllegalStateException("Blogger feed request failed: HTTP $status")
            }
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
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
