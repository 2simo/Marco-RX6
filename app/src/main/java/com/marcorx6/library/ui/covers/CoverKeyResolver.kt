package com.marcorx6.library.ui

import java.io.File

/** Resolves a stable identity for a cover, preferring a local file or bundled asset. */
internal object CoverKeyResolver {
    fun resolve(
        localPath: String,
        imageUrl: String,
        stableCacheName: (String) -> String
    ): String {
        val localFile = localPath.takeIf { it.isNotBlank() && File(it).isFile }?.let(::File)
        val asset = if (imageUrl.isNotBlank()) {
            "offline_images/${stableCacheName(imageUrl)}.webp"
        } else ""
        return when {
            localFile != null -> "file:${localFile.absolutePath}|url:$imageUrl"
            asset.isNotBlank() -> "asset:$asset|url:$imageUrl"
            else -> "url:$imageUrl"
        }
    }
}
