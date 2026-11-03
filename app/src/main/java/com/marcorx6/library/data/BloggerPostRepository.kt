package com.marcorx6.library.data

import com.marcorx6.library.network.BloggerApiClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/**
 * Blogger post operations used by the publishing UI.
 * UI state and callbacks stay in MainActivity; this class owns API payloads and paging.
 */
internal class BloggerPostRepository(
    private val api: BloggerApiClient,
    private val blogIdProvider: () -> String,
    private val authorizationCheck: () -> Boolean
) {
    fun fetchLabels(): List<String> {
        val blogId = requireBlogId()
        val labels = linkedSetOf<String>()
        var pageToken = ""
        var pages = 0
        do {
            val tokenPart = if (pageToken.isBlank()) "" else
                "&pageToken=" + URLEncoder.encode(pageToken, Charsets.UTF_8.name()).replace("+", "%20")
            val response = request("GET", "blogs/$blogId/posts?fetchBodies=false&maxResults=500$tokenPart")
            val posts = response.optJSONArray("items") ?: JSONArray()
            for (index in 0 until posts.length()) {
                val postLabels = posts.optJSONObject(index)?.optJSONArray("labels") ?: continue
                for (labelIndex in 0 until postLabels.length()) {
                    postLabels.optString(labelIndex).trim().takeIf { it.isNotBlank() }?.let(labels::add)
                }
            }
            pageToken = response.optString("nextPageToken", "")
            pages++
        } while (pageToken.isNotBlank() && pages < MAX_LABEL_PAGES)
        return labels.toList().sortedBy { it.lowercase(Locale.ROOT) }
    }

    fun savePost(post: Game, labels: List<String>, existingPostId: String?): JSONObject {
        val blogId = requireBlogId()
        if (!existingPostId.isNullOrBlank()) {
            return request(
                "PUT",
                "blogs/$blogId/posts/$existingPostId",
                postPayload(post, labels)
            )
        }
        return request("POST", "blogs/$blogId/posts/", postPayload(post, labels))
    }

    fun deletePost(postId: String) {
        require(postId.isNotBlank()) { "لا يملك هذا المقال معرّف Blogger." }
        val blogId = requireBlogId()
        request("DELETE", "blogs/$blogId/posts/$postId")
    }

    private fun postPayload(post: Game, labels: List<String>) = JSONObject()
        .put("kind", "blogger#post")
        .put("title", post.name)
        .put("content", post.bloggerHtml)
        .put("labels", JSONArray().apply { labels.forEach(::put) })

    private fun request(method: String, path: String, payload: JSONObject? = null): JSONObject {
        if (!authorizationCheck()) {
            throw SecurityException("لم يتم التحقق من صلاحيات Blogger لهذا الحساب")
        }
        return api.request(method, path, payload)
    }

    private fun requireBlogId(): String = blogIdProvider().takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("زامن المدونة أولاً ثم حاول مجدداً.")

    private companion object {
        const val MAX_LABEL_PAGES = 10
    }
}
