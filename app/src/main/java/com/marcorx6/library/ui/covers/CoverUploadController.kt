package com.marcorx6.library.ui

import android.net.Uri
import com.marcorx6.library.data.CoverUploadRepository
import com.marcorx6.library.data.CoverUploadResult
import java.io.File

internal class CoverUploadController(
    private val repository: CoverUploadRepository,
    private val accessToken: () -> String,
    private val executeIo: (() -> Unit) -> Unit,
    private val postToUi: (() -> Unit) -> Unit
) {
    fun upload(
        uri: Uri,
        onLocalFileSaved: (File) -> Unit,
        onSuccess: (CoverUploadResult) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        executeIo {
            try {
                val result = repository.saveAndUploadCover(uri, accessToken()) { localFile ->
                    postToUi { onLocalFileSaved(localFile) }
                }
                postToUi { onSuccess(result) }
            } catch (error: Exception) {
                postToUi { onFailure(error) }
            }
        }
    }
}
