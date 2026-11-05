package com.marcorx6.library.network

import org.json.JSONArray

/**
 * Builds the Blogger article from the MARCO RX6 structured publishing template.
 * Preview and publishing use this same HTML to keep their output identical.
 */
internal object BloggerArticleBuilder {
    private fun escape(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

    fun build(
        title: String,
        categories: String,
        genre: String,
        releaseYear: String,
        description: String,
        coverUrl: String,
        downloadUrl: String,
        packageSize: String = "",
        version: String = "",
        fileFormat: String = "PKG",
        language: String = "الإنجليزية",
        password: String = "",
        rights: String = "",
        downloadLinksJson: String = ""
    ): String {
        val safeTitle = escape(title)
        val metadata = listOf(
            "الحجم" to packageSize,
            "النسخة" to version,
            "الصيغة" to fileFormat.ifBlank { "PKG" },
            "اللغة" to language.ifBlank { "الإنجليزية" },
            "كلمة السر " to password,
            "الحقوق" to rights
        ).joinToString("\n") { (label, value) ->
            "<li><strong>$label:</strong> ${escape(value)}</li>"
        }

        val links = parseLinks(downloadLinksJson, downloadUrl)
        val linksHtml = links.joinToString("\n") { item ->
            "<p><a href=\"${escape(item.second)}\" target=\"_blank\">${escape(item.first)}</a></p>"
        }
        val safeCover = escape(BloggerFeedParser.normalizeBloggerImageUrl(coverUrl))
        val coverHtml = if (safeCover.isNotBlank()) {
            "<p style=\"text-align:center\"><a href=\"$safeCover\" target=\"_blank\"><img src=\"$safeCover\" alt=\"$safeTitle\" style=\"max-width:100%;height:auto;\"/></a></p>"
        } else ""

        return """
            <div class="marcorx6-post">
              <p>أهلاً بكم متابعي مدونة MARCO_RX6 الكرام. إليكم لعبة جديدة من إضافاتنا الحصرية بروابط مباشرة وسريعة.</p>
              $coverHtml
              <h3 style="color: #8b5cf6;">معلومات اللعبة:</h3>
              <ul>
                $metadata
              </ul>
              <h3 style="color: #8b5cf6;">روابط التحميل المباشرة:</h3>
              $linksHtml
            </div>
        """.trimIndent()
    }

    private fun parseLinks(json: String, fallbackUrl: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        try {
            val array = JSONArray(json)
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val label = item.optString("label").trim()
                val url = item.optString("url").trim()
                if (label.isNotBlank() && (url.startsWith("https://", true) || url.startsWith("http://", true))) {
                    result += label to url
                }
            }
        } catch (_: Exception) {
            // Older posts may not have structured download links.
        }
        if (result.isEmpty() && fallbackUrl.isNotBlank() &&
            (fallbackUrl.startsWith("https://", true) || fallbackUrl.startsWith("http://", true))
        ) result += "تحميل اللعبة" to fallbackUrl
        return result
    }
}
