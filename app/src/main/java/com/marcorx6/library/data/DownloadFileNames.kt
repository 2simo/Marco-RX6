package com.marcorx6.library.data

import android.net.Uri
import android.webkit.MimeTypeMap
import java.net.URLDecoder
import java.util.Locale

/** Extracts and sanitizes server-provided download filenames without losing Unicode characters. */
internal object DownloadFileNames {
    fun fromResponse(disposition: String?, url: String): String? {
        val header = disposition.orEmpty()
        // RFC 5987 permits a language segment (for example UTF-8'en') and percent-encoded
        // UTF-8 filenames. A literal '+' is a plus sign here, not a space.
        val encodedMatch = Regex(
            """filename\*\s*=\s*([^']*)'[^']*'([^;]+)""",
            RegexOption.IGNORE_CASE
        ).find(header)
        if (encodedMatch != null) {
            val charset = encodedMatch.groupValues[1].ifBlank { "UTF-8" }
            val encoded = encodedMatch.groupValues[2].trim().trim('"')
            runCatching { URLDecoder.decode(encoded.replace("+", "%2B"), charset) }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { return clean(it) }
        }
        val regularMatch = Regex(
            """filename\s*=\s*(?:"([^"]*)"|([^;]+))""",
            RegexOption.IGNORE_CASE
        ).find(header)
        val regular = regularMatch?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?: regularMatch?.groupValues?.getOrNull(2)?.trim()
        if (!regular.isNullOrBlank()) return clean(regular)
        val segment = Uri.parse(url).lastPathSegment.orEmpty()
        val decoded = runCatching { Uri.decode(segment) }.getOrDefault(segment)
        return decoded.takeIf { it.isNotBlank() && it.contains('.') }?.let(::clean)
    }

    fun safeName(value: String, mimeType: String, id: Long): String {
        var name = clean(value).replace(Regex("[\\u0000-\\u001F\\u007F/\\\\:*?\\"<>|]"), "_")
            .trim(' ', '.', '_')
            .take(180)
        if (name.isBlank()) name = "download_$id"
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        if (!name.contains('.') && !extension.isNullOrBlank()) {
            name = "$name.$extension"
        }
        return name
    }

    private fun clean(value: String): String = value
        .replace("\\r", "")
        .replace("\\n", "")
        .substringAfterLast('/')
        .substringAfterLast('\\\\')
        .trim()
}
