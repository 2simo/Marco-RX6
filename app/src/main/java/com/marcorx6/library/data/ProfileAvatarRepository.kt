package com.marcorx6.library.data

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Persists the profile avatar selected by the user in app-private storage.
 */
internal class ProfileAvatarRepository(
    private val contentResolver: ContentResolver,
    private val filesDirectory: File
) {
    fun save(uri: Uri): File {
        val folder = File(filesDirectory, "profile")
        if (!folder.exists() && !folder.mkdirs()) {
            throw IllegalStateException("تعذر إنشاء مجلد الصورة الشخصية")
        }

        val destination = File(folder, "avatar.img")
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "تعذرت قراءة الصورة" }
            FileOutputStream(destination).use { output -> input.copyTo(output) }
        }
        return destination
    }
}
