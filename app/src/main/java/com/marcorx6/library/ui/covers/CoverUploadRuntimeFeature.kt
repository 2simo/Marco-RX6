package com.marcorx6.library.ui.covers

import com.marcorx6.library.ui.*

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.widget.ImageView
import java.io.File

/**
 * Owns the Drive cover upload feature, transient upload state and picker-result controller.
 */
internal class CoverUploadRuntimeFeature(
    context: Context,
    filesDir: File,
    contentResolver: ContentResolver,
    accessToken: () -> String,
    executeIo: (() -> Unit) -> Unit,
    postToUi: (() -> Unit) -> Unit,
    loadCover: (ImageView, String, String) -> Unit,
    deleteCoverFileIfUnused: (String) -> Unit,
    dp: (Int) -> Int,
    white: () -> Int,
    styleDialog: (android.app.AlertDialog) -> Unit,
    private val launchCoverPicker: (Intent) -> Unit
) {
    val state = CoverUploadState()

    private val feature = CoverFeature(
        context = context,
        filesDir = filesDir,
        contentResolver = contentResolver,
        accessToken = accessToken,
        state = state,
        executeIo = executeIo,
        postToUi = postToUi,
        coverUrlField = { state.urlField },
        coverPreview = { state.preview },
        loadCover = loadCover,
        deleteCoverFileIfUnused = deleteCoverFileIfUnused,
        dp = dp,
        white = white,
        styleDialog = styleDialog
    )

    val selectionController get() = feature.selectionController

    fun chooseCover() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        launchCoverPicker(intent)
    }
}
