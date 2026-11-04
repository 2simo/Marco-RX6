package com.marcorx6.library.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BloggerFeedParserTest {

    @Test
    fun normalizesSupportedPlayStationPlatforms() {
        assertEquals("PSP", BloggerFeedParser.normalizePlatform("PlayStation Portable"))
        assertEquals("PS1", BloggerFeedParser.normalizePlatform("PlayStation 1"))
        assertEquals("PS2", BloggerFeedParser.normalizePlatform("PS-2"))
        assertEquals("PS3", BloggerFeedParser.normalizePlatform("PlayStation3"))
        assertEquals("PS4", BloggerFeedParser.normalizePlatform("ps4"))
        assertEquals("3DS", BloggerFeedParser.normalizePlatform("Nintendo 3DS"))
        assertEquals("3DS", BloggerFeedParser.normalizePlatform("3DS"))
        assertEquals("GBA", BloggerFeedParser.normalizePlatform("Game Boy Advance"))
        assertEquals("GBA", BloggerFeedParser.normalizePlatform("gba"))
    }

    @Test
    fun rejectsUnknownPlatforms() {
        assertNull(BloggerFeedParser.normalizePlatform("Xbox"))
        assertNull(BloggerFeedParser.normalizePlatform(""))
    }

    @Test
    fun findsHttpsAlternatePostUrlAndRemovesQueryParameters() {
        val entry = JSONObject().put(
            "link",
            org.json.JSONArray()
                .put(JSONObject().put("rel", "self").put("href", "https://example.com/feed"))
                .put(JSONObject().put("rel", "alternate").put("href", "https://example.com/post?id=42&source=feed"))
        )

        assertEquals("https://example.com/post", BloggerFeedParser.findPostUrl(entry))
    }

    @Test
    fun rejectsNonHttpsAlternatePostUrls() {
        val entry = JSONObject().put(
            "link",
            org.json.JSONArray().put(
                JSONObject().put("rel", "alternate").put("href", "http://example.com/post")
            )
        )

        assertEquals("", BloggerFeedParser.findPostUrl(entry))
    }

    @Test
    fun selectsFirstRecognizedPlatformCategory() {
        val entry = JSONObject().put(
            "category",
            org.json.JSONArray()
                .put(JSONObject().put("term", "Action"))
                .put(JSONObject().put("term", "PlayStation 2"))
                .put(JSONObject().put("term", "PS4"))
        )

        assertEquals("PS2", BloggerFeedParser.platformFromCategories(entry))
    }

    @Test
    fun ignoresEntriesWithoutAUsableTitleOrHttpsPostLink() {
        val blankTitle = JSONObject().put("title", JSONObject().put("$" + "t", "   "))
        val insecureLink = JSONObject()
            .put("title", JSONObject().put("$" + "t", "Example"))
            .put(
                "link",
                org.json.JSONArray().put(
                    JSONObject().put("rel", "alternate").put("href", "http://example.com/post")
                )
            )

        assertNull(BloggerFeedParser.parseBlogEntry(blankTitle))
        assertNull(BloggerFeedParser.parseBlogEntry(insecureLink))
    }
}
