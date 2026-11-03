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
    private val pendingLocalPath: () -> String,
    private val setPendingLocalPath: (String) -> Unit,
    private val setPendingPublicUrl: (String) -> Unit,
    private val setUploadInProgress: (Boolean) -> Unit,
    private val setUploadFailed: (Boolean) -> Unit,
    private val coverUrlField: () -> EditText?,
    private val coverPreview: () -> ImageView?,
    private val loadCover: (ImageView, String, String) -> Unit,
    private val deleteCoverFileIfUnused: (String) -> Unit,
    private val dp: (Int) -> Int,
    private val white: () -> Int,
    private val styleDialog: (AlertDialog) -> Unit
) {
    fun handle(uri: Uri) {
        setUploadInProgress(true)
        setUploadFailed(false)
        setPendingPublicUrl("")
        coverUrlField()?.setText("")

        uploadController.upload(
            uri = uri,
            onLocalFileSaved = { localFile ->
                val previousPendingPath = pendingLocalPath()
                setPendingLocalPath(localFile.absolutePath)
                deleteCoverFileIfUnused(previousPendingPath)
                coverUrlField()?.setText("")
                coverPreview()?.let { loadCover(it, pendingLocalPath(), "") }
                Toast.makeText(context, "تم اختيار الصورة، جارٍ رفعها للموقع...", Toast.LENGTH_SHORT).show()
            },
            onSuccess = { uploadResult ->
                setPendingLocalPath(uploadResult.localFile.absolutePath)
                setPendingPublicUrl(uploadResult.publicUrl)
                setUploadInProgress(false)
                setUploadFailed(false)
                coverUrlField()?.setText(uploadResult.publicUrl)
                coverPreview()?.let { loadCover(it, pendingLocalPath(), uploadResult.publicUrl) }
                Toast.makeText(context, "تم رفع الغلاف بنجاح وأصبح جاهزاً للنشر", Toast.LENGTH_LONG).show()
            },
            onFailure = { error ->
                setUploadInProgress(false)
                setUploadFailed(true)
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
