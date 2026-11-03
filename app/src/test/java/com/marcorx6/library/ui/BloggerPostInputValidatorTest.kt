package com.marcorx6.library.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BloggerPostInputValidatorTest {

    private fun validate(
        uploadInProgress: Boolean = false,
        localPath: String = "",
        labels: Set<String> = setOf("Games"),
        title: String = "A game",
        coverUrl: String = "https://example.com/cover.jpg",
        relatedLink: String = "https://example.com/game",
        body: String = "Description"
    ) = BloggerPostInputValidator.validate(
        uploadInProgress, localPath, labels, title, coverUrl, relatedLink, body
    )

    @Test
    fun acceptsCompleteSecurePost() {
        assertNull(validate())
    }

    @Test
    fun blocksSaveWhileCoverUploadIsRunning() {
        val failure = validate(uploadInProgress = true)
        assertEquals("انتظر حتى يكتمل رفع صورة الغلاف", failure?.message)
        assertEquals(true, failure?.longMessage)
    }

    @Test
    fun blocksUnfinishedLocalCoverUpload() {
        val failure = validate(localPath = "/covers/pending.jpg", coverUrl = "")
        assertEquals("صورة الغلاف لم تكتمل عملية رفعها. أعد اختيار الصورة وانتظر رسالة نجاح الرفع.", failure?.message)
    }

    @Test
    fun requiresAtLeastOneBloggerLabel() {
        val failure = validate(labels = emptySet())
        assertEquals("اختر Label واحداً على الأقل من المدونة", failure?.message)
    }

    @Test
    fun identifiesMissingTitleAndInsecureUrls() {
        assertEquals(BloggerPostInputValidator.Field.TITLE, validate(title = " ")?.field)
        assertEquals(BloggerPostInputValidator.Field.COVER_URL, validate(coverUrl = "http://example.com/cover.jpg")?.field)
        assertEquals(BloggerPostInputValidator.Field.RELATED_LINK, validate(relatedLink = "http://example.com")?.field)
        assertEquals(BloggerPostInputValidator.Field.BODY, validate(body = " ")?.field)
    }
}
