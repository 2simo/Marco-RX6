package com.marcorx6.library.data

import android.net.Uri
import android.webkit.MimeTypeMap
import java.net.URLDecoder

/** Extracts and sanitizes server-provided download filenames without losing Unicode characters. */
internal object DownloadFileNames {
    fun fromResponse(disposition: String?, url: String): String? {
        val header = disposition.orEmpty()
        val encoded = Regex("""filename\*\s*=\s*UTF-8''([^;]+)""", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.getOrNull(1)?.trim()?.trim('"')
        if (!encoded.isNullOrBlank()) {
            runCatching { URLDecoder.decode(encoded, "UTF-8") }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { return clean(it) }
        }
        val regular = Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.getOrNull(1)?.trim()
        if (!regular.isNullOrBlank()) return clean(regular)
        val segment = Uri.parse(url).lastPathSegment.orEmpty()
        val decoded = runCatching { Uri.decode(segment) }.getOrDefault(segment)
        return decoded.takeIf { it.isNotBlank() && it.contains('.') }?.let(::clean)
    }

    fun safeName(value: String, mimeType: String, id: Long): String {
        var name = clean(value).replace(Regex("[\\u0000-\\u001F\\u007F/\\\\:*?\"<>|]"), "_")
            .trim(' ', '.', '_')
            .take(180)
        if (name.isBlank()) name = "download_$id"
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        if (!name.contains('.').also { hasExtension -> hasExtension } && !extension.isNullOrBlank()) {
            name = "$name.$extension"
        }
        return name
    }

    private fun clean(value: String): String = value
        .replace("\r", "")
        .replace("\n", "")
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .trim()
}
