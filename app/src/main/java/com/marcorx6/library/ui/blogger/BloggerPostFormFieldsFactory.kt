package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView

internal data class BloggerPostFormFields(
    val title: EditText,
    val body: EditText,
    val coverUrl: EditText,
    val relatedLink: EditText,
    val genre: EditText,
    val releaseYear: EditText,
    val coverPreview: ImageView
)

internal class BloggerPostFormFieldsFactory(
    private val activity: Activity,
    private val dp: (Int) -> Int,
    private val makeInput: (String) -> EditText,
    private val rounded: (Int, Int) -> Drawable,
    private val panel: () -> Int
) {
    fun create(): BloggerPostFormFields {
        val title = makeInput("عنوان المقال").apply { setSingleLine(true) }
        val body = makeInput("وصف المقال — اكتب المعلومات فقط، وسيُنسّقها القالب تلقائياً").apply {
            minLines = 8
            gravity = Gravity.TOP or Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(13), dp(12), dp(13), dp(12))
        }
        val coverUrl = makeInput("رابط صورة الغلاف (اختياري)").apply { setSingleLine(true) }
        val relatedLink = makeInput("رابط التحميل أو رابط إضافي (اختياري)").apply { setSingleLine(true) }
        val genre = makeInput("نوع اللعبة (مثال: أكشن، مغامرات)").apply { setSingleLine(true) }
        val releaseYear = makeInput("سنة الإصدار (مثال: 2024)").apply {
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val coverPreview = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(panel(), 13)
        }
        return BloggerPostFormFields(title, body, coverUrl, relatedLink, genre, releaseYear, coverPreview)
    }
}
