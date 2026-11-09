package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Color
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
            isVerticalScrollBarEnabled = false
            // JavaScript is enabled only for local DOM height measurement. Article
            // scripts and event handlers are stripped before the HTML is loaded.
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            // Remote article content cannot access local files or content providers.
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            // File-URL cross-origin access remains disabled by Android defaults.
            // Do not opt into deprecated file-URL access flags.
            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            settings.loadsImagesAutomatically = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.mediaPlaybackRequiresUserGesture = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Measure the actual DOM, not WebView's viewport/contentHeight
                    // (which can retain the old fixed viewport and create a blank box).
                    view?.post {
                        val articleView = view
                        articleView.evaluateJavascript(
                            "(document.documentElement.scrollHeight || document.body.scrollHeight || 1).toString()"
                        ) { result ->
                            val cssHeight = result?.trim()?.trim('"')?.toFloatOrNull()
                                ?: articleView.contentHeight.toFloat()
                            val targetHeight = (cssHeight * activity.resources.displayMetrics.density)
                                .toInt().coerceAtLeast(1)
                            val params = articleView.layoutParams
                            if (params != null && params.height != targetHeight) {
                                params.height = targetHeight
                                articleView.layoutParams = params
                            }
                        }
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: android.webkit.RenderProcessGoneDetail
                ): Boolean {
                    // The article is optional UI; discard a dead renderer without
                    // taking down the library/details Activity with it.
                    (view.parent as? android.view.ViewGroup)?.removeView(view)
                    view.stopLoading()
                    view.destroy()
                    android.util.Log.w("MarcoArticleWebView", "Article renderer exited; the article view was discarded")
                    return true
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                    val target = request?.url?.toString().orEmpty()
                    if (target.startsWith("https://", ignoreCase = true)) onDownload(target, null, null, null, gameUrl)
                    return true
                }
                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    val target = url.orEmpty()
                    if (target.startsWith("https://", ignoreCase = true)) onDownload(target, null, null, null, gameUrl)
                    return true
                }
            }
            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                onDownload(url, userAgent, contentDisposition, mimeType, gameUrl)
            }
            val raw = if (bloggerHtml.isNotBlank()) bloggerHtml else
                "<h2>$gameName</h2>"
            // The details screen shows the library cover separately. Keep this article
            // area informational only: remove embedded media and inline links so that
            // the cover is displayed once and download destinations appear only as buttons.
            // Metadata is already rendered in the dedicated information card above.
            // Remove the matching Blogger list rows and heading from the article body
            // so the old-format information does not appear a second time below buttons.
            // Keep the introduction, but remove the complete legacy game-information
            // section from the article. Its values are already shown in the new card.
            // Stop at the next heading (normally the download-links heading), which is
            // handled separately below and must not consume the introduction.
            // Rebrand the legacy Blogger introduction for the app experience.
            // Blogger remains the content host; users should see MARCO as the product.
            val appBrandedRaw = raw.replace(
                "أهلاً بكم متابعي مدونة MARCO_RX6 الكرام. إليكم لعبة جديدة من إضافاتنا الحصرية بروابط مباشرة وسريعة.",
                "أهلاً بمستخدمي تطبيق MARCO الكرام، إليكم لعبة جديدة من إضافاتنا الحصرية بروابط مباشرة وسريعة."
            )
            // The introduction is now rendered natively above the metadata card.
            // Remove only that exact introductory paragraph from the article WebView,
            // preserving every other article paragraph and section.
            val articleWithoutIntro = appBrandedRaw
                .replace(
                    Regex("(?is)<p\\b[^>]*>.*?(?:أهلاً\\s+بكم\\s+متابعي\\s+مدونة\\s+MARCO_RX6|أهلاً\\s+بمستخدمي\\s+تطبيق\\s+MARCO\\s+RX6).*?إضافاتنا\\s+الحصرية\\s+بروابط\\s+مباشرة\\s+وسريعة.*?</p>"),
                    ""
                )
                .replace(
                    "أهلاً بكم متابعي مدونة MARCO_RX6 الكرام. إليكم لعبة جديدة من إضافاتنا الحصرية بروابط مباشرة وسريعة.",
                    ""
                )
                .replace(
                    "أهلاً بمستخدمي تطبيق MARCO الكرام، إليكم لعبة جديدة من إضافاتنا الحصرية بروابط مباشرة وسريعة.",
                    ""
                )
            val metadataAliases = "(?:الحجم|حجم|size|النسخة|version|title\\s*id|cusa|الصيغة|format|اللغة|languages?|كلمة\\s*السر|كلمه\\s*السر|password|pass|الحقوق|credits|rights)"
            // Java/Kotlin regex word-boundaries do not reliably recognize Arabic
            // letters as word characters. Match the complete leading metadata label
            // followed by a separator (or whitespace), without a trailing \\b.
            val metadataLabel = Regex("(?i)^\\s*${metadataAliases}(?:\\s*[:：-]\\s*|\\s+|$)")
            fun stripMetadataRows(source: String, tagPattern: Regex): String = tagPattern.replace(source) { match ->
                val text = android.text.Html.fromHtml(
                    match.groupValues[2], android.text.Html.FROM_HTML_MODE_LEGACY
                ).toString().trim()
                if (metadataLabel.containsMatchIn(text)) "" else match.value
            }
            // Blogger uses several layouts: plain rows, bold labels, and table cells.
            // Inspect each row as rendered text so formatting tags or missing colons
            // cannot cause the same metadata to appear again below the native info card.
            val articleWithoutMetadataRows = stripMetadataRows(
                articleWithoutIntro,
                Regex("(?is)<(p|li|tr|dd|dt)\\b[^>]*>(.*?)</\\1\\s*>")
            ).let { rows ->
                stripMetadataRows(rows, Regex("(?is)<(div)\\b[^>]*>((?:(?!<div\\b).)*?)</\\1\\s*>"))
            }
            val withoutGameMetadata = articleWithoutMetadataRows
                .replace(
                    Regex("(?is)<h([1-6])\\b[^>]*>\\s*(?:معلومات\\s*اللعبة|game\\s*information)\\s*:?\\s*</h\\1>.*?(?=<h[1-6]\\b[^>]*>|$)"),
                    ""
                )
            val articleHtml = withoutGameMetadata
                .replace(Regex("(?is)<script[^>]*>.*?</script>"), "")
                .replace(Regex("(?is)<style[^>]*>.*?</style>"), "")
                .replace(Regex("(?is)<(img|source|picture|svg|iframe|video|audio|object|embed)\\b[^>]*>(?:.*?</\\1\\s*>)?"), "")
                .replace(Regex("(?is)<a\\b[^>]*>.*?</a\\s*>"), "")
                .replace(Regex("(?is)<h[1-6][^>]*>\\s*(?:روابط التحميل المباشرة|روابط التحميل|download links)\\s*:?\\s*</h[1-6]>"), "")
                .replace(Regex("(?is)</?(figure|figcaption)\\b[^>]*>"), "")
                .replace(Regex("(?i)\\s(onload|onclick|onerror)\\s*=\\s*(['\"]).*?\\2"), "")
            val style = """
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                *{box-sizing:border-box}
                html,body{margin:0;padding:0;width:100%;max-width:100%;overflow-x:hidden}
                body{background:${if (darkMode) "#08090c" else "#f2f4f7"} !important;color:${if (darkMode) "#f4f4f8" else "#191d24"} !important;font-family:$fontFamily,Arial,sans-serif;font-size:${fontSize}px;line-height:1.9;padding:20px 17px 0;overflow-wrap:anywhere;direction:rtl;text-align:right}
                body *{color:inherit !important;background-color:transparent !important}
                body h1,body h2,body h3,body h4{color:${if (darkMode) "#bec6d2" else "#828a97"} !important;line-height:1.4;margin:25px 0 12px}
                p{margin:0 0 18px}
                img{display:block;max-width:100%!important;height:auto!important;width:auto;margin:18px auto;border-radius:12px;object-fit:contain}
                body a{color:inherit !important;text-decoration:none}
                ul,ol{padding-left:25px;margin:10px 0 20px}
                li{padding:4px 0}
                body blockquote{border-right:3px solid ${if (darkMode) "#828a97" else "#828a97"};padding:8px 14px;margin:16px 0;background:${if (darkMode) "#23262d" else "#e2e6ec"} !important}
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
