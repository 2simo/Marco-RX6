package com.marcorx6.library.network

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the Blogger article from structured fields.
 * The same HTML is used by preview and publishing to prevent visual/content drift.
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
            "الصيغة" to fileFormat,
            "اللغة" to language,
            "كلمة السر" to password,
            "الحقوق" to rights
        ).filter { it.second.isNotBlank() }
            .joinToString("\n") { (label, value) ->
                "<li><strong>${escape(label)}:</strong> ${escape(value)}</li>"
            }

        val links = parseLinks(downloadLinksJson, downloadUrl)
        val linksHtml = links.joinToString("\n") { item ->
            "<p><a href=\"${escape(item.second)}\" target=\"_blank\" rel=\"noopener noreferrer\">${escape(item.first)}</a></p>"
        }
        val safeCover = escape(BloggerFeedParser.normalizeBloggerImageUrl(coverUrl))
        val coverHtml = if (safeCover.isNotBlank()) {
            "<p style=\"text-align:center\"><a href=\"$safeCover\" target=\"_blank\"><img src=\"$safeCover\" alt=\"$safeTitle\" style=\"max-width:100%;height:auto;\"/></a></p>"
        } else ""
        val safeGenre = escape(genre)
        val safeYear = escape(releaseYear)
        val gameMeta = listOfNotNull(
            safeGenre.takeIf { it.isNotBlank() }?.let { "<li><strong>النوع:</strong> $it</li>" },
            safeYear.takeIf { it.isNotBlank() }?.let { "<li><strong>سنة الإصدار:</strong> $it</li>" }
        ).joinToString("\n")
        val descriptionHtml = description.trim().split(Regex("\\n\\s*\\n"))
            .filter { it.isNotBlank() }
            .joinToString("") {
                "<p>${escape(it).replace("\n", "<br/>")}</p>"
            }
        val categoryHtml = categories.takeIf { it.isNotBlank() }
            ?.let { "<p><strong>الأقسام:</strong> ${escape(it)}</p>" }.orEmpty()

        return """
            <div class="marcorx6-post" dir="rtl">
              <p>أهلاً بكم متابعي مدونة MARCO_RX6 الكرام. إليكم لعبة جديدة من إضافاتنا الحصرية بروابط مباشرة وسريعة.</p>
              $coverHtml
              <h3 style="color: #8b5cf6;">معلومات اللعبة:</h3>
              <ul>
                $metadata
                $gameMeta
              </ul>
              <h3 style="color: #8b5cf6;">روابط التحميل المباشرة:</h3>
              $linksHtml
              $categoryHtml
              <h3 style="color: #8b5cf6;">وصف اللعبة:</h3>
              $descriptionHtml
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
                if (label.isNotBlank() && (url.startsWith("https://") || url.startsWith("http://"))) {
                    result += label to url
                }
            }
        } catch (_: Exception) {
            // Older posts may have no structured links yet.
        }
        if (result.isEmpty() && fallbackUrl.isNotBlank() &&
            (fallbackUrl.startsWith("https://") || fallbackUrl.startsWith("http://"))
        ) result += "تحميل اللعبة" to fallbackUrl
        return result
    }
}
