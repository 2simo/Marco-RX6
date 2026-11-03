package com.marcorx6.library.ui

import android.content.ContentResolver
import android.content.Context
import android.widget.EditText
import android.widget.ImageView
import java.io.File

/** Owns Drive cover upload services, transient state, and picker-result coordination. */
internal class CoverFeature(
    context: Context,
    filesDir: File,
    contentResolver: ContentResolver,
    accessToken: () -> String,
    executeIo: (() -> Unit) -> Unit,
    postToUi: (() -> Unit) -> Unit,
    coverUrlField: () -> EditText?,
    coverPreview: () -> ImageView?,
    loadCover: (ImageView, String, String) -> Unit,
    deleteCoverFileIfUnused: (String) -> Unit,
    dp: (Int) -> Int,
    white: () -> Int,
    styleDialog: (android.app.AlertDialog) -> Unit
) {
    val state = CoverUploadState()

    private val driveServices = DriveServices(
        filesDir = filesDir,
        contentResolver = contentResolver,
        accessToken = accessToken,
        executeIo = executeIo,
        postToUi = postToUi
    )

    private val uploadController = driveServices.createCoverUploadController()

    val selectionController = CoverSelectionController(
        context = context,
        uploadController = uploadController,
        state = state,
        coverUrlField = coverUrlField,
        coverPreview = coverPreview,
        loadCover = loadCover,
        deleteCoverFileIfUnused = deleteCoverFileIfUnused,
        dp = dp,
        white = white,
        styleDialog = styleDialog
    )
}
