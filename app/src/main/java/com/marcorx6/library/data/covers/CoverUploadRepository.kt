package com.marcorx6.library.data

import android.content.ContentResolver
import android.net.Uri
import com.marcorx6.library.network.DriveCoverUploader
import java.io.File
import java.io.FileOutputStream

/**
 * Owns local cover-file persistence and Google Drive upload orchestration.
 * UI state and user feedback remain in MainActivity.
 */
internal class CoverUploadRepository(
    private val filesDir: File,
    private val contentResolver: ContentResolver,
    private val driveClient: DriveCoverUploader
) {
    /** Copies a picked image into the app's private covers directory. */
    fun savePickedCover(uri: Uri): File {
        val folder = File(filesDir, "covers")
        if (!folder.exists() && !folder.mkdirs()) {
            throw IllegalStateException("تعذر إنشاء مجلد الأغلفة")
        }

        val destination = File(folder, "cover_${System.currentTimeMillis()}_${System.nanoTime()}.img")
        try {
            contentResolver.openInputStream(uri).use { input ->
                if (input == null) throw IllegalStateException("تعذرت قراءة الصورة")
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            }
            return destination
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    /**
     * Saves a selected image locally, notifies the UI so it can show the local
     * preview, then uploads it and returns the verified public URL.
     */
    fun saveAndUploadCover(
        uri: Uri,
        accessToken: String,
        onLocalFileSaved: (File) -> Unit
    ): CoverUploadResult {
        val localFile = savePickedCover(uri)
        onLocalFileSaved(localFile)
        val publicUrl = driveClient.uploadCover(localFile, accessToken)
        return CoverUploadResult(localFile, publicUrl)
    }
}

internal data class CoverUploadResult(
    val localFile: File,
    val publicUrl: String
)
