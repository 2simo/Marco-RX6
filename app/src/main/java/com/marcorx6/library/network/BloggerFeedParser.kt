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
        val description = extractDescription(content).ifBlank { cleanText.take(1800) }
        val links = extractDownloadLinks(content)

        return Game(
            name = title,
            platform = platform,
            description = description.take(1800),
            url = postUrl,
            coverUrl = image,
            sourcePostUrl = postUrl,
            downloadLinks = links,
            blogImported = true,
            bloggerPostId = entry.optJSONObject("id")?.optString(36.toChar().toString() + "t", "")?.let { Regex("post-(\\d+)").find(it)?.groupValues?.getOrNull(1).orEmpty() } ?: "",
            bloggerHtml = entry.optString("offlineHtml").ifBlank { content },
            packageSize = extractInfoValue(content, "الحجم"),
            version = extractInfoValue(content, "النسخة"),
            fileFormat = extractInfoValue(content, "الصيغة"),
            language = extractInfoValue(content, "اللغة"),
            password = extractInfoValue(content, "كلمة السر"),
            rights = extractInfoValue(content, "الحقوق")
        )
    }



    private fun extractDescription(html: String): String {
        val section = Regex(
            "(?is)<h[1-6][^>]*>\\s*وصف اللعبة\\s*:?\\s*</h[1-6]>(.*?)(?:</div>\\s*$|$)"
        ).find(html)?.groupValues?.getOrNull(1).orEmpty()
        return if (section.isBlank()) "" else htmlToText(section)
    }

    fun extractLanguage(html: String): String = extractInfoValue(html, "اللغة")

    private fun extractInfoValue(html: String, label: String): String {
        val escapedLabel = Pattern.quote(label)
        // Blogger posts do not all use the same markup: accept <strong>, <b>,
        // and plain "اللغة:" labels, then stop at the current list/paragraph row.
        val labelPattern =
            "(?:<\\s*(?:strong|b)\\b[^>]*>\\s*$escapedLabel\\s*:?\\s*</\\s*(?:strong|b)\\s*>|$escapedLabel\\s*:)"
        val match = Regex(
            "(?is)$labelPattern\\s*(.*?)(?=</li>|</p>|<br\\s*/?>|<li\\b|<p\\b|$)"
        ).find(html) ?: return ""
        return decodeHtmlEntities(match.groupValues[1].replace(Regex("(?is)<[^>]*>"), " "))
            .replace(Regex("\\s+"), " ").trim()
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

        // Prefer the specific Blogger OS/category label over its broad console label.
        // For example, a PS2 PORT PS4 post can carry both "PS2" and "ps2ps4".
        val terms = (0 until categories.length()).mapNotNull { index ->
            categories.optJSONObject(index)?.optString("term", "")?.takeIf { it.isNotBlank() }
        }
        val specific = terms.firstNotNullOfOrNull { term ->
            normalizePlatform(term)?.takeIf { it in setOf("PS3_ARABIC", "PS2_ARABIC", "PS2_PORT_PS4", "PS2_PORT_PS3") }
        }
        if (specific != null) return specific
        return terms.firstNotNullOfOrNull(::normalizePlatform)
    }

    fun normalizePlatform(value: String): String? =
        com.marcorx6.library.data.GameLibraryManager.normalizePlatform(value)

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
        if (html.isBlank()) return result.toString()

        // Blogger templates vary: href can appear anywhere in the anchor attributes,
        // and download sections have several Arabic/English heading variants.
        val sectionPattern = Regex(
            "(?is)<h[1-6]\\b[^>]*>\\s*(?:روابط\\s*(?:التحميل|التنزيل)(?:\\s*المباشرة)?|تحميل\\s*اللعبة|download\\s*links?)\\s*:?\\s*</h[1-6]>(.*?)(?=<h[1-6]\\b|$)"
        )
        val downloadSections = sectionPattern.findAll(html).map { it.groupValues[1] }.toList()
        val seenUrls = mutableSetOf<String>()
        val downloadTerms = listOf(
            "تحميل", "تنزيل", "download", "direct link", "رابط مباشر", "رابط التحميل",
            "التحديث", "الباتش", "باتش", "ملف اللعبة", "رابط اللعبة",
            "game", "update", "dlc", "mediafire", "mega", "google drive", "drive.google",
            "pixeldrain", "1fichier", "uptobox", "rapidgator", "uploaded", "filecrypt",
            "4shared", "gofile", "terabox", "send.cm", "katfile", "userscloud"
        )
        val knownDownloadHosts = listOf(
            "mediafire.com", "mega.nz", "mega.co.nz", "drive.google.com", "docs.google.com",
            "pixeldrain.com", "1fichier.com", "uptobox.com", "rapidgator.net", "4shared.com",
            "gofile.io", "terabox.com", "1024tera.com", "send.cm", "katfile.com",
            "userscloud.com", "qiwi.gg", "krakenfiles.com", "filecrypt.cc"
        )
        val downloadableExtensions = setOf(
            "zip", "7z", "rar", "iso", "apk", "xapk", "apks", "bin", "cue",
            "chd", "pkg", "nsp", "xci", "torrent", "pdf", "exe", "mp4", "mkv"
        )
        val anchorPattern = Regex("(?is)<a\\b([^>]*)>(.*?)</a\\s*>")
        val hrefPattern = Regex("(?is)\\bhref\\s*=\\s*(['\"])(.*?)\\1")
        val titlePattern = Regex("(?is)\\b(?:title|aria-label)\\s*=\\s*(['\"])(.*?)\\1")
        val imagePattern = Regex("\\.(?:jpe?g|png|gif|webp|bmp|svg|avif|jfif)(?:$|[?#])", RegexOption.IGNORE_CASE)

        for (anchor in anchorPattern.findAll(html)) {
            val attributes = anchor.groupValues[1]
            val href = hrefPattern.find(attributes)?.groupValues?.getOrNull(2)
                ?.let(::decodeHtmlEntities)?.trim().orEmpty()
            if (!href.startsWith("https://", ignoreCase = true)) continue

            val body = anchor.groupValues[2]
            val visibleLabel = decodeHtmlEntities(body.replace(Regex("(?is)<[^>]*>"), " "))
                .replace(Regex("\\s+"), " ").trim()
            val attributeLabel = titlePattern.find(attributes)?.groupValues?.getOrNull(2)
                ?.let(::decodeHtmlEntities)?.trim().orEmpty()
            val imageAlt = Regex("(?is)\\balt\\s*=\\s*(['\"])(.*?)\\1")
                .find(body)?.groupValues?.getOrNull(2).orEmpty()
            val label = visibleLabel.ifBlank { attributeLabel.ifBlank { imageAlt } }
            val normalizedLabel = label.lowercase(Locale.ROOT)
            val normalizedUrl = href.lowercase(Locale.ROOT)
            val urlPath = normalizedUrl.substringBefore('?').substringBefore('#')
            val extension = urlPath.substringAfterLast('.', "").takeIf { urlPath.contains('.') }.orEmpty()
            val isImageUrl = imagePattern.containsMatchIn(urlPath)
            if (isImageUrl || !seenUrls.add(href)) continue

            // Parse the host from the URL string instead of android.net.Uri so this
            // parser behaves identically in Android runtime and local JVM unit tests.
            val host = Regex("^https://([^/:?#]+)", RegexOption.IGNORE_CASE)
                .find(href)?.groupValues?.getOrNull(1)?.lowercase(Locale.ROOT).orEmpty()
            val knownHost = knownDownloadHosts.any { host == it || host.endsWith(".$it") }
            val isInsideDownloadSection = downloadSections.any { it.contains(anchor.value) }
            val isFileUrl = extension in downloadableExtensions
            val hasDownloadLabel = downloadTerms.any { normalizedLabel.contains(it) } ||
                normalizedLabel == "اللعبة"
            // A link in an explicitly named download section, a recognized file host,
            // or a direct downloadable file is sufficient even when its button says
            // only "اضغط هنا". Generic article/navigation links remain excluded.
            if (!isInsideDownloadSection && !knownHost && !isFileUrl && !hasDownloadLabel) continue

            val safeLabel = label.ifBlank { "تحميل ${result.length() + 1}" }
            result.put(JSONObject().put("label", safeLabel).put("url", href))
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
