package com.marcorx6.library.ui

import com.marcorx6.library.network.BloggerArticleBuilder
import android.app.Activity
import android.webkit.WebView
import android.webkit.WebViewClient

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
        relatedLink: String,
        packageSize: String = "",
        version: String = "",
        fileFormat: String = "PKG",
        language: String = "الإنجليزية",
        password: String = "",
        rights: String = "",
        downloadLinksJson: String = ""
    ): WebView {
        val previewTitle = title.trim().ifBlank { "معاينة المقال" }
        val previewHtml =
            "<html><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><body style=\"background:${if (isDarkMode()) "#08090c" else "#f2f4f7"};color:${if (isDarkMode()) "#f4f5f7" else "#191d24"};padding:12px;\">" +
                BloggerArticleBuilder.build(
                    title = previewTitle,
                    categories = labels,
                    genre = genre,
                    releaseYear = releaseYear,
                    description = body,
                    coverUrl = coverUrl,
                    downloadUrl = relatedLink,
                    packageSize = packageSize,
                    version = version,
                    fileFormat = fileFormat,
                    language = language,
                    password = password,
                    rights = rights,
                    downloadLinksJson = downloadLinksJson
                ) +
                "</body></html>"

        return WebView(activity).apply {
            setBackgroundColor(backgroundColor())
            settings.javaScriptEnabled = false
            webViewClient = object : WebViewClient() {
                override fun onRenderProcessGone(
                    view: WebView,
                    detail: android.webkit.RenderProcessGoneDetail
                ): Boolean {
                    (view.parent as? android.view.ViewGroup)?.removeView(view)
                    view.destroy()
                    android.util.Log.w("MarcoArticlePreview", "Preview renderer exited; the preview was discarded")
                    return true
                }
            }
            loadDataWithBaseURL(null, previewHtml, "text/html", "UTF-8", null)
        }
    }
}
