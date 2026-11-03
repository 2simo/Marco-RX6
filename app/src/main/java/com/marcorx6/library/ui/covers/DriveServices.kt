package com.marcorx6.library.ui

import android.content.ContentResolver
import com.marcorx6.library.data.CoverUploadRepository
import com.marcorx6.library.network.GoogleDriveClient
import java.io.File

/** Composition boundary for Drive cover storage and upload operations. */
internal class DriveServices(
    filesDir: File,
    contentResolver: ContentResolver,
    accessToken: () -> String,
    executeIo: ((() -> Unit) -> Unit),
    postToUi: ((() -> Unit) -> Unit)
) {
    private val repository = CoverUploadRepository(
        filesDir = filesDir,
        contentResolver = contentResolver,
        driveClient = GoogleDriveClient()
    )

    fun createCoverUploadController() = CoverUploadController(
        repository = repository,
        accessToken = accessToken,
        executeIo = executeIo,
        postToUi = postToUi
    )
}
