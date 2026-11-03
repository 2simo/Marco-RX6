package com.marcorx6.library.ui

import java.io.File

/** Deletes only app-owned cover files that are no longer referenced by the catalog. */
internal object CoverFileCleanup {
    fun deleteIfUnused(coversDirectory: File, path: String, referencedPaths: List<String>): Boolean {
        if (path.isBlank()) return false
        val directory = runCatching { coversDirectory.canonicalFile }.getOrNull() ?: return false
        val target = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
        if (target.parentFile != directory || !target.isFile) return false
        val referenced = referencedPaths.any { candidate ->
            candidate.isNotBlank() &&
                runCatching { File(candidate).canonicalFile == target }.getOrDefault(false)
        }
        return !referenced && target.delete()
    }
}
