package com.marcorx6.library.data

import android.content.ContentResolver
import android.net.Uri
import com.marcorx6.library.network.GoogleDriveClient
import java.io.File
import java.io.FileOutputStream

/**
 * Owns local cover-file persistence and Google Drive upload orchestration.
 * UI state and user feedback remain in MainActivity.
 */
internal class CoverUploadRepository(
    private val filesDir: File,
    private val contentResolver: ContentResolver,
    private val driveClient: GoogleDriveClient
) {
    /** Copies a picked image into the app's private covers directory. */
    fun savePickedCover(uri: Uri): File {
        val folder = File(filesDir, "covers")
        if (!folder.exists() && !folder.mkdirs()) {
            throw IllegalStateException("تعذر إنشاء مجلد الأغلفة")
        }

        val destination = File(folder, "cover_${System.currentTimeMillis()}.img")
        contentResolver.openInputStream(uri).use { input ->
            if (input == null) throw IllegalStateException("تعذرت قراءة الصورة")
            FileOutputStream(destination).use { output -> input.copyTo(output) }
        }
        return destination
    }

    /** Uploads a previously persisted cover and returns its verified public URL. */
    fun uploadCover(file: File, accessToken: String): String =
        driveClient.uploadCover(file, accessToken)
}
