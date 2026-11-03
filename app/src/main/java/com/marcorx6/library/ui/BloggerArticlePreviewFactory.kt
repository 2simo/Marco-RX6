package com.marcorx6.library.ui

import android.app.Activity
import android.webkit.WebView

internal class BloggerArticlePreviewFactory(
    private val activity: Activity,
    private val backgroundColor: () -> Int,
    private val isDarkMode: () -> Boolean
) {
    fun create(
        title: String,
        labels: String,
        genre: String,
        releaseYear: String,
        body: String,
        coverUrl: String,
        relatedLink: String
    ): WebView {
        val previewTitle = title.trim().ifBlank { "معاينة المقال" }
        val previewHtml =
            "<html><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><body style=\"background:${if (isDarkMode()) "#0a0b11" else "#f5f7fb"};color:${if (isDarkMode()) "#f4f4f8" else "#202638"};padding:12px;\">" +
                BloggerArticleBuilder.build(
                    previewTitle,
                    labels,
                    genre,
                    releaseYear,
                    body,
                    coverUrl,
                    relatedLink
                ) +
                "</body></html>"

        return WebView(activity).apply {
            setBackgroundColor(backgroundColor())
            settings.javaScriptEnabled = false
            loadDataWithBaseURL(null, previewHtml, "text/html", "UTF-8", null)
        }
    }
}
