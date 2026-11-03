package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient

internal class GameArticleWebViewFactory(
    private val activity: Activity
) {
    fun create(
        backgroundColor: Int,
        gameUrl: String,
        gameName: String,
        bloggerHtml: String,
        darkMode: Boolean,
        fontFamily: String,
        fontSize: Int,
        onDownload: (url: String, userAgent: String?, contentDisposition: String?, mimeType: String?, pageUrl: String) -> Unit
    ): WebView {
        return WebView(activity).apply {
            setBackgroundColor(backgroundColor)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isVerticalScrollBarEnabled = false
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            // Remote article content cannot access local files or content providers.
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            settings.loadsImagesAutomatically = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.mediaPlaybackRequiresUserGesture = true
            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                onDownload(url, userAgent, contentDisposition, mimeType, gameUrl)
            }
            val raw = if (bloggerHtml.isNotBlank()) bloggerHtml else
                "<h2>$gameName</h2>"
            val articleHtml = raw.replace(Regex("(?is)<script[^>]*>.*?</script>"), "")
                .replace(Regex("(?i)\\s(onload|onclick|onerror)\\s*=\\s*(['\"]).*?\\2"), "")
            val style = """
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                *{box-sizing:border-box}
                html,body{margin:0;padding:0;width:100%;max-width:100%;overflow-x:hidden}
                body{background:${if (darkMode) "#0a0b11" else "#f5f7fb"} !important;color:${if (darkMode) "#f4f4f8" else "#202638"} !important;font-family:$fontFamily,Arial,sans-serif;font-size:${fontSize}px;line-height:1.9;padding:20px 17px 36px;overflow-wrap:anywhere;direction:rtl;text-align:right}
                body *{color:inherit !important;background-color:transparent !important}
                body h1,body h2,body h3,body h4{color:${if (darkMode) "#59b7ff" else "#238de8"} !important;line-height:1.4;margin:25px 0 12px}
                p{margin:0 0 18px}
                img{display:block;max-width:100%!important;height:auto!important;width:auto;margin:18px auto;border-radius:12px;object-fit:contain}
                body a{color:${if (darkMode) "#e2c17c" else "#238de8"} !important;text-decoration:none;font-weight:700}
                ul,ol{padding-left:25px;margin:10px 0 20px}
                li{padding:4px 0}
                body blockquote{border-right:3px solid ${if (darkMode) "#d3b170" else "#8b5cf6"};padding:8px 14px;margin:16px 0;background:${if (darkMode) "#1b1d3b" else "#eee9f8"} !important}
                table{display:block;max-width:100%;overflow-x:auto;border-collapse:collapse}
                td,th{border:1px solid ${if (darkMode) "#596174" else "#cbd2df"};padding:8px}
                iframe,video{max-width:100%!important}
                </style>
            """.trimIndent()
            loadDataWithBaseURL(
                gameUrl.ifBlank { "https://marcorx6.blogspot.com/" },
                "<!doctype html><html lang=\"ar\" dir=\"rtl\"><head>$style</head><body dir=\"rtl\">$articleHtml</body></html>",
                "text/html", "UTF-8", null
            )
        }
    }
}
