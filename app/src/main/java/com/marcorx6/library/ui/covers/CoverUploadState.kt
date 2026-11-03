package com.marcorx6.library.ui

import android.widget.EditText
import android.widget.ImageView

/** Temporary editor state for a cover selected for the next Blogger post. */
internal class CoverUploadState {
    var pendingLocalPath: String = ""
        private set
    var pendingPublicUrl: String = ""
        private set
    var inProgress: Boolean = false
        private set
    var failed: Boolean = false
        private set
    var preview: ImageView? = null
    var urlField: EditText? = null

    fun beginSelection() {
        inProgress = true
        failed = false
        pendingPublicUrl = ""
    }

    fun localFileSaved(path: String) {
        pendingLocalPath = path
    }

    fun uploadSucceeded(path: String, publicUrl: String) {
        pendingLocalPath = path
        pendingPublicUrl = publicUrl
        inProgress = false
        failed = false
    }

    fun uploadFailed() {
        inProgress = false
        failed = true
    }

    fun resetFailure() {
        failed = false
    }

    fun clear() {
        pendingLocalPath = ""
        pendingPublicUrl = ""
        inProgress = false
        failed = false
        preview = null
        urlField = null
    }
}
