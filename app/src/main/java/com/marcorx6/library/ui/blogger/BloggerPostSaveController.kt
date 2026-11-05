package com.marcorx6.library.ui

import com.marcorx6.library.data.Game
import org.json.JSONObject

internal class BloggerPostSaveController(
    private val executeIo: (() -> Unit) -> Unit,
    private val postToUi: (() -> Unit) -> Unit,
    private val savePost: (Game, List<String>, String?) -> JSONObject,
    private val translate: (String) -> String
) {
    fun save(
        post: Game,
        labels: List<String>,
        existing: Game?,
        onSuccess: (Game) -> Unit,
        onError: (String) -> Unit
    ) {
        executeIo {
            try {
                val result = savePost(post, labels, existing?.bloggerPostId)
                postToUi {
                    val saved = post.copy(
                        bloggerPostId = result.optString("id", post.bloggerPostId),
                        bloggerHtml = result.optString("content", post.bloggerHtml),
                        url = result.optString("url", post.url),
                        sourcePostUrl = result.optString("url", post.sourcePostUrl),
                        blogImported = true
                    )
                    onSuccess(saved)
                }
            } catch (e: Exception) {
                postToUi { onError(translate(e.message ?: "فشل نشر المقال")) }
            }
        }
    }
}
