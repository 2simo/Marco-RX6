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
 * independent MARCO RX6 sections. Each feed is paginated so posts beyond the
 * first 100 entries are not silently omitted.
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
        SectionFeed(
            "بلايستيشن 3 معربة",
            "PS3_ARABIC",
            listOf("بلايستيشن 3 معربة", "ps3arabic", "PS3 Arabic")
        ),
        SectionFeed(
            "بلاي ستيشن 2 معربة",
            "PS2_ARABIC",
            listOf("PlayStation 2 — PlayStation 2 معربة", "ps2arabic", "PS2 ARABIC", "PS2 Arabic")
        ),
        SectionFeed(
            "PlayStation 2 — PlayStation 2 PS3",
            "PS2_PORT_PS3",
            listOf("ps2ps3", "PS2 PORT PS3")
        ),
        SectionFeed(
            "PlayStation 2 — PlayStation 2 PS4",
            "PS2_PORT_PS4",
            listOf("ps2ps4", "PS2 PORT PS4")
        )
    )

    fun fetch(): BloggerFeedResult {
        val main = fetchAllPages(feedUrl)
        val merged = LinkedHashMap<String, Game>()

        main.games.forEach { game ->
            val key = game.sourcePostUrl.ifBlank { game.url }
            if (key.isNotBlank()) merged[key] = game
        }

        dedicatedSections.forEach { section ->
            // Try the exact Blogger label first, then known alternate spellings.
            val labelsToTry = (listOf(section.label) + section.fallbackLabels).distinct()
            var sectionGames = emptyList<Game>()
            for (label in labelsToTry) {
                val result = runCatching { fetchAllPages(labelFeedUrl(label)) }.getOrNull()
                if (result != null && result.games.isNotEmpty()) {
                    sectionGames = result.games
                    break
                }
            }
            sectionGames.forEach { game ->
                val key = game.sourcePostUrl.ifBlank { game.url }
                if (key.isNotBlank()) {
                    // The dedicated feed is authoritative even when a post also
                    // carries a broad PS2/PS3 category.
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

    /**
     * Blogger caps a response page. Walk start-index in batches so large labels
     * and the main feed can return all available posts, not only the first page.
     */
    private fun fetchAllPages(url: String): BloggerFeedResult {
        val base = url.substringBefore('?')
        val query = url.substringAfter('?', "")
        val allGames = LinkedHashMap<String, Game>()
        var blogId = ""

        for (page in 0 until 50) {
            val startIndex = page * 100 + 1
            val pageUrl = buildString {
                append(base)
                append('?')
                if (query.isNotBlank()) {
                    append(query)
                    append('&')
                }
                append("start-index=")
                append(startIndex)
            }
            val result = fetchFeed(pageUrl)
            if (blogId.isBlank()) blogId = result.blogId
            result.games.forEach { game ->
                val key = game.sourcePostUrl.ifBlank { game.url }
                if (key.isNotBlank()) allGames[key] = game
            }
            if (result.games.size < 100) break
        }

        return BloggerFeedResult(blogId = blogId, games = allGames.values.toList())
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
