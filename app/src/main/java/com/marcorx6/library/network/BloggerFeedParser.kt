package com.marcorx6.library.network

import android.net.Uri
import android.text.Html
import com.marcorx6.library.data.Game
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.regex.Pattern

/**
 * Converts Blogger feed entries and HTML into the app's game model.
 * Kept free of Activity state so feed parsing can be tested independently.
 */
internal object BloggerFeedParser {
    fun parseBlogEntry(entry: JSONObject): Game? {

        val title = entry.optJSONObject("title")
            ?.optString("\$t", "")?.trim().orEmpty()

        if (title.isBlank()) return null

        val content = entry.optJSONObject("content")
            ?.optString("\$t", "")
            ?: entry.optJSONObject("summary")
                ?.optString("\$t", "").orEmpty()

        val postUrl = findPostUrl(entry)
        if (postUrl.isBlank()) return null

        val platform = platformFromCategories(entry) ?: "BLOG"
        val image = extractFirstImage(content).ifBlank { extractFeedThumbnail(entry) }
        val cleanText = htmlToText(content)
        val links = extractDownloadLinks(content)

        return Game(
            name = title,
            platform = platform,
            description = cleanText.take(1800),
            url = postUrl,
            coverUrl = image,
            sourcePostUrl = postUrl,
            downloadLinks = links,
            blogImported = true,
            bloggerPostId = entry.optJSONObject("id")?.optString(36.toChar().toString() + "t", "")?.let { Regex("post-(\\d+)").find(it)?.groupValues?.getOrNull(1).orEmpty() } ?: "",
            bloggerHtml = entry.optString("offlineHtml").ifBlank { content }
        )
    }

    fun findPostUrl(entry: JSONObject): String {

        val links = entry.optJSONArray("link") ?: return ""

        for (i in 0 until links.length()) {

            val item = links.optJSONObject(i) ?: continue

            if (item.optString("rel") == "alternate") {
                val href = item.optString("href", "")

                if (href.startsWith("https://")) {
                    return href.substringBefore("?")
                }
            }
        }

        return ""
    }

    fun platformFromCategories(entry: JSONObject): String? {

        val categories = entry.optJSONArray("category")
            ?: return null

        for (i in 0 until categories.length()) {

            val term = categories.optJSONObject(i)
                ?.optString("term", "") ?: continue

            val platform = normalizePlatform(term)

            if (platform != null) return platform
        }

        return null
    }

    fun normalizePlatform(value: String): String? {

        val s = value.lowercase(Locale.ROOT)
            .replace(" ", "")
            .replace("-", "")

        return when {
            s == "psp" || s.contains("playstationportable") -> "PSP"
            s == "ps1" || s.contains("playstation1") ||
                s.contains("playstationone") -> "PS1"
            s == "ps2" || s.contains("playstation2") -> "PS2"
            s == "ps3" || s.contains("playstation3") -> "PS3"
            s == "ps4" || s.contains("playstation4") -> "PS4"
            s == "3ds" || s == "nintendo3ds" || s.contains("nintendo3ds") -> "3DS"
            s == "gba" || s == "gameboyadvance" || s.contains("gameboyadvance") -> "GBA"
            else -> null
        }
    }

    // Blogger serves the same uploaded image through size-specific URL paths
    // such as /s320/filename.jpg. Use a larger rendition for the app's cover cards.
    fun normalizeBloggerImageUrl(rawUrl: String): String {
        val decoded = SecureUrlPolicy.upgradeToHttps(
            rawUrl.trim()
                .replace("&amp;", "&")
                .replace("\\/", "/")
                .replace("\\u003d", "=")
        )
        if (decoded.isBlank()) return ""
        return try {
            val uri = Uri.parse(decoded)
            val host = uri.host.orEmpty().lowercase(Locale.ROOT)
            if (host == "blogger.googleusercontent.com" || host.endsWith(".bp.blogspot.com") ||
                host == "bp.blogspot.com" || host.endsWith(".blogspot.com")
            ) {
                // Blogger path-style transforms: /s320/, /s640/, /w320-h240/
                val path = uri.encodedPath.orEmpty()
                val resizedPath = path.replace(
                    Regex("/(?:s\\d+(?:-[a-z0-9]+)*|w\\d+-h\\d+(?:-[a-z0-9]+)*)/"),
                    "/s960/"
                )
                // Newer Google image URLs may put the transform after '='.
                val resized = if (resizedPath != path) resizedPath else
                    uri.toString().replace(Regex("=s\\d+(?:-[a-z0-9]+)*$"), "=s960")
                if (resizedPath != path) uri.buildUpon().encodedPath(resizedPath).build().toString() else resized
            } else decoded
        } catch (_: Exception) {
            decoded
        }
    }

    fun normalizeBloggerImageTags(html: String): String {
        val imageTagPattern = Regex("(?is)<img\\b[^>]*>")
        val sourcePattern = Regex("(?i)(\\b(?:src|data-src)\\s*=\\s*)([\"'])([^\"']+)([\"'])")
        return imageTagPattern.replace(html) { tagMatch ->
            sourcePattern.replace(tagMatch.value) { sourceMatch ->
                val normalized = normalizeBloggerImageUrl(sourceMatch.groupValues[3])
                sourceMatch.groupValues[1] + sourceMatch.groupValues[2] +
                    normalized + sourceMatch.groupValues[4]
            }
        }
    }

    fun extractFeedThumbnail(entry: JSONObject): String {
        val mediaThumbnail = entry.optJSONObject("media\$thumbnail")?.optString("url").orEmpty()
        if (mediaThumbnail.isNotBlank()) return normalizeBloggerImageUrl(mediaThumbnail)
        val mediaContent = entry.optJSONArray("media\$content")
        for (index in 0 until (mediaContent?.length() ?: 0)) {
            val url = mediaContent?.optJSONObject(index)?.optString("url").orEmpty()
            if (url.startsWith("https://") || url.startsWith("http://")) {
                return normalizeBloggerImageUrl(SecureUrlPolicy.upgradeToHttps(url))
            }
        }
        return ""
    }

    fun extractFirstImage(html: String): String {
        // Blogger feed HTML can contain lazy-load attributes or escaped URL entities.
        val tagPattern = Pattern.compile("(?is)<img\\b[^>]*>")
        val tags = tagPattern.matcher(html)
        val attributePattern = Pattern.compile(
            "(?i)(?:src|data-src|data-original|data-lazy-src)\\s*=\\s*[\"']([^\"']+)[\"']"
        )
        while (tags.find()) {
            val attributes = attributePattern.matcher(tags.group())
            while (attributes.find()) {
                val candidate = attributes.group(1)?.trim().orEmpty()
                if (candidate.startsWith("https://") || candidate.startsWith("http://") || candidate.startsWith("//")) {
                    val absolute = if (candidate.startsWith("//")) "https:$candidate"
                        else SecureUrlPolicy.upgradeToHttps(candidate)
                    return normalizeBloggerImageUrl(absolute)
                }
            }
        }
        return ""
    }

    private fun decodeHtmlEntities(value: String): String {
        val named = value
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&#39;", "'", ignoreCase = true)
            .replace("&apos;", "'", ignoreCase = true)
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace("&nbsp;", " ", ignoreCase = true)
        return Regex("&#(x[0-9a-fA-F]+|[0-9]+);").replace(named) { match ->
            val token = match.groupValues[1]
            val point = if (token.startsWith("x", true)) token.substring(1).toIntOrNull(16)
                else token.toIntOrNull()
            if (point != null && Character.isValidCodePoint(point))
                String(Character.toChars(point)) else match.value
        }
    }

    fun extractDownloadLinks(html: String): String {

        val result = JSONArray()

        val pattern = Pattern.compile(
            "(?is)<a[^>]+href=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>"
        )

        val matcher = pattern.matcher(html)
        val seenUrls = mutableSetOf<String>()
        val downloadTerms = listOf(
            "تحميل", "download", "game", "update", "dlc",
            "mediafire", "mega", "google drive", "drive.google",
            "pixeldrain", "1fichier", "uptobox", "rapidgator",
            "uploaded", "filecrypt", "4shared"
        )

        while (matcher.find()) {
            val rawHref = matcher.group(1)?.trim().orEmpty()
            val href = decodeHtmlEntities(rawHref).trim()
            val labelHtml = matcher.group(2).orEmpty()
            val label = decodeHtmlEntities(labelHtml.replace(Regex("(?is)<[^>]*>"), " "))
                .replace(Regex("\\s+"), " ").trim()
            val normalizedLabel = label.lowercase(Locale.ROOT)

            // Blogger article bodies contain ordinary links as well as download links.
            // Only keep links whose visible label identifies a download or known file host.
            val isDownloadLink = downloadTerms.any { term ->
                normalizedLabel.contains(term)
            }
            if (!href.startsWith("https://", ignoreCase = true) ||
                label.isBlank() || !isDownloadLink || !seenUrls.add(href)
            ) continue

            result.put(JSONObject().put("label", label).put("url", href))
        }

        return result.toString()
    }

    fun htmlToText(html: String): String {

        val withBreaks = html
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(
                Regex("(?i)</p>|</div>|</li>|</h[1-6]>"),
                "\n"
            )

        @Suppress("DEPRECATION")
        return Html.fromHtml(withBreaks).toString()
            .replace(Regex("[\\t ]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
