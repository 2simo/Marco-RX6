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
    }

    @Test
    fun rejectsUnknownPlatforms() {
        assertNull(BloggerFeedParser.normalizePlatform("Xbox"))
        assertNull(BloggerFeedParser.normalizePlatform(""))
    }

    @Test
    fun findsHttpsAlternatePostUrlAndRemovesQueryParameters() {
        val entry = JSONObject(
            """{"link":[{"rel":"self","href":"https://example.com/feed"},{"rel":"alternate","href":"https://example.com/post?id=42&source=feed"}]}"""
        )

        assertEquals("https://example.com/post", BloggerFeedParser.findPostUrl(entry))
    }

    @Test
    fun rejectsNonHttpsAlternatePostUrls() {
        val entry = JSONObject(
            """{"link":[{"rel":"alternate","href":"http://example.com/post"}]}"""
        )

        assertEquals("", BloggerFeedParser.findPostUrl(entry))
    }

    @Test
    fun selectsFirstRecognizedPlatformCategory() {
        val entry = JSONObject(
            """{"category":[{"term":"Action"},{"term":"PlayStation 2"},{"term":"PS4"}]}"""
        )

        assertEquals("PS2", BloggerFeedParser.platformFromCategories(entry))
    }

    @Test
    fun ignoresEntriesWithoutAUsableTitleOrHttpsPostLink() {
        val blankTitle = JSONObject("""{"title":{"\$t":"   "}}""")
        val insecureLink = JSONObject(
            """{"title":{"\$t":"Example"},"link":[{"rel":"alternate","href":"http://example.com/post"}]}"""
        )

        assertNull(BloggerFeedParser.parseBlogEntry(blankTitle))
        assertNull(BloggerFeedParser.parseBlogEntry(insecureLink))
    }
}
