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
        assertEquals("PS3_ARABIC", BloggerFeedParser.normalizePlatform("ps3arabic"))
        assertEquals("PS2_ARABIC", BloggerFeedParser.normalizePlatform("ps2arabic"))
        assertEquals("PS2_PORT_PS4", BloggerFeedParser.normalizePlatform("ps2ps4"))
        assertEquals("PS2_PORT_PS3", BloggerFeedParser.normalizePlatform("ps2ps3"))
        assertEquals("PS2_PORT_PS4", BloggerFeedParser.normalizePlatform("PS2 Port PS4"))
        assertEquals("PS2_PORT_PS3", BloggerFeedParser.normalizePlatform("PS2 Port PS3"))
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
    fun prioritizesSpecificOsCategoryOverBroadConsoleCategory() {
        val entry = JSONObject().put(
            "category",
            org.json.JSONArray()
                .put(JSONObject().put("term", "PS2"))
                .put(JSONObject().put("term", "ps2ps4"))
        )
        assertEquals("PS2_PORT_PS4", BloggerFeedParser.platformFromCategories(entry))
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
    @Test
    fun extractsOnlyClearlyLabeledDownloadLinksAndKnownFileHosts() {
        val html = """
            <p><a href="https://example.com/privacy">Privacy policy</a></p>
            <a href="https://example.com/game.zip">Download Game ZIP</a>
            <a href="https://drive.google.com/file/d/123/view">Google Drive</a>
            <a href="https://example.com/about">About the author</a>
            <a href="https://example.com/update">تحميل التحديث</a>
        """.trimIndent()

        val links = org.json.JSONArray(BloggerFeedParser.extractDownloadLinks(html))
        assertEquals(3, links.length())
        assertEquals("Download Game ZIP", links.getJSONObject(0).getString("label"))
        assertEquals("Google Drive", links.getJSONObject(1).getString("label"))
        assertEquals("تحميل التحديث", links.getJSONObject(2).getString("label"))
    }

    @Test
    fun excludesLinkedBloggerCoverAndKeepsArabicDownloadButtons() {
        val html = """
            <div class="separator">
              <a href="https://blogger.googleusercontent.com/img/b/cover/s747/game-cover.jpg">
                <img src="https://blogger.googleusercontent.com/img/b/cover/s320/game-cover.jpg" />
              </a>
            </div>
            <a href="https://vik1ngfile.site/f/game">اللعبة</a>
            <a href="https://vik1ngfile.site/f/update">التحديث</a>
            <a href="https://vik1ngfile.site/f/patch">الباتش</a>
            <a href="https://vik1ngfile.site/f/dlc">DLC</a>
        """.trimIndent()

        val links = org.json.JSONArray(BloggerFeedParser.extractDownloadLinks(html))
        assertEquals(4, links.length())
        assertEquals("اللعبة", links.getJSONObject(0).getString("label"))
        assertEquals("التحديث", links.getJSONObject(1).getString("label"))
        assertEquals("الباتش", links.getJSONObject(2).getString("label"))
        assertEquals("DLC", links.getJSONObject(3).getString("label"))
        assertEquals(
            "https://vik1ngfile.site/f/game",
            links.getJSONObject(0).getString("url")
        )
    }

    @Test
    fun decodesHtmlEntitiesInDownloadUrlsAndRemovesDuplicates() {
        val html = """
            <a href="https://example.com/download?id=1&amp;part=2">Download Part 1</a>
            <a href="https://example.com/download?id=1&amp;part=2">Download Part 1</a>
        """.trimIndent()

        val links = org.json.JSONArray(BloggerFeedParser.extractDownloadLinks(html))
        assertEquals(1, links.length())
        assertEquals(
            "https://example.com/download?id=1&part=2",
            links.getJSONObject(0).getString("url")
        )
    }

    @Test
    fun detectsDownloadLinksInsideArabicDownloadSectionWithGenericButtonText() {
        val html = """
            <h2>روابط التحميل</h2>
            <div>
              <a class="download" title="رابط اللعبة" href="https://example.org/files/game">
                اضغط هنا
              </a>
              <a href="https://example.org/files/update">الرابط الأول</a>
            </div>
            <h2>معلومات إضافية</h2>
            <a href="https://example.org/author">الكاتب</a>
        """.trimIndent()

        val links = org.json.JSONArray(BloggerFeedParser.extractDownloadLinks(html))
        assertEquals(2, links.length())
        assertEquals("https://example.org/files/game", links.getJSONObject(0).getString("url"))
        assertEquals("https://example.org/files/update", links.getJSONObject(1).getString("url"))
    }

    @Test
    fun detectsDirectFileLinksEvenWhenButtonLabelIsGeneric() {
        val html = """
            <a href="https://files.example.org/archive/game.7z?token=abc">اضغط هنا</a>
            <a href="https://example.org/images/cover.jpg">تحميل اللعبة</a>
            <a href="https://example.org/about">حول اللعبة</a>
        """.trimIndent()

        val links = org.json.JSONArray(BloggerFeedParser.extractDownloadLinks(html))
        assertEquals(1, links.length())
        assertEquals(
            "https://files.example.org/archive/game.7z?token=abc",
            links.getJSONObject(0).getString("url")
        )
    }

    @Test
    fun detectsCommonDownloadHostEvenWithNonDescriptiveButtonText() {
        val html = """
            <a title="ملف اللعبة" href="https://gofile.io/d/AbCd">فتح الرابط</a>
            <a href="https://example.org/contact">اتصل بنا</a>
        """.trimIndent()

        val links = org.json.JSONArray(BloggerFeedParser.extractDownloadLinks(html))
        assertEquals(1, links.length())
        assertEquals("https://gofile.io/d/AbCd", links.getJSONObject(0).getString("url"))
    }

}
