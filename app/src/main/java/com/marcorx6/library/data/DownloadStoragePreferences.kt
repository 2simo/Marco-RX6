package com.marcorx6.library.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/** Persists the user's SAF folder grant without requesting broad storage permission. */
internal class DownloadStoragePreferences(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("download_storage", Context.MODE_PRIVATE)

    fun saveTree(uri: Uri) {
        appContext.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        preferences.edit().putString(KEY_TREE_URI, uri.toString()).apply()
    }

    fun treeUri(): Uri? = preferences.getString(KEY_TREE_URI, null)?.let(Uri::parse)

    fun displayName(): String {
        val uri = treeUri() ?: return "تخزين التطبيق"
        return try {
            DocumentsContract.getTreeDocumentId(uri)
                .substringAfter(':')
                .substringAfterLast('/')
                .ifBlank { "مجلد مختار" }
        } catch (_: Exception) {
            "مجلد مختار"
        }
    }

    companion object {
        private const val KEY_TREE_URI = "download_tree_uri"
    }
}
