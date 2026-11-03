package com.marcorx6.library.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Coordinates the cover picker result, upload feedback and temporary cover state.
 * Activity-owned views and state are accessed through callbacks.
 */
internal class CoverSelectionController(
    private val context: Context,
    private val uploadController: CoverUploadController,
    private val state: CoverUploadState,
    private val coverUrlField: () -> EditText?,
    private val coverPreview: () -> ImageView?,
    private val loadCover: (ImageView, String, String) -> Unit,
    private val deleteCoverFileIfUnused: (String) -> Unit,
    private val dp: (Int) -> Int,
    private val white: () -> Int,
    private val styleDialog: (AlertDialog) -> Unit
) {
    fun handle(uri: Uri) {
        state.beginSelection()
        coverUrlField()?.setText("")

        uploadController.upload(
            uri = uri,
            onLocalFileSaved = { localFile ->
                val previousPendingPath = state.pendingLocalPath
                state.localFileSaved(localFile.absolutePath)
                deleteCoverFileIfUnused(previousPendingPath)
                coverUrlField()?.setText("")
                coverPreview()?.let { loadCover(it, state.pendingLocalPath, "") }
                Toast.makeText(context, "تم اختيار الصورة، جارٍ رفعها للموقع...", Toast.LENGTH_SHORT).show()
            },
            onSuccess = { uploadResult ->
                state.uploadSucceeded(uploadResult.localFile.absolutePath, uploadResult.publicUrl)
                coverUrlField()?.setText(uploadResult.publicUrl)
                coverPreview()?.let { loadCover(it, state.pendingLocalPath, uploadResult.publicUrl) }
                Toast.makeText(context, "تم رفع الغلاف بنجاح وأصبح جاهزاً للنشر", Toast.LENGTH_LONG).show()
            },
            onFailure = { error ->
                state.uploadFailed()
                val details = (error.message ?: error.javaClass.simpleName).ifBlank { "خطأ غير معروف" }
                val detailsView = TextView(context).apply {
                    text = details
                    setTextIsSelectable(true)
                    setTextColor(white())
                    textSize = 13f
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                }
                val scroll = ScrollView(context).apply {
                    addView(detailsView)
                    setPadding(dp(8), 0, dp(8), 0)
                }
                AlertDialog.Builder(context)
                    .setTitle("فشل رفع الغلاف")
                    .setMessage("التفاصيل الكاملة لخطأ Google Drive:")
                    .setView(scroll)
                    .setPositiveButton("نسخ التفاصيل") { _, _ ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Google Drive error", details))
                        Toast.makeText(context, "تم نسخ تفاصيل الخطأ", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("إغلاق", null)
                    .show()
                    .also(styleDialog)
            }
        )
    }
}
