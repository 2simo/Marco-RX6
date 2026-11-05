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
    val packageSize: EditText,
    val version: EditText,
    val fileFormat: EditText,
    val language: EditText,
    val password: EditText,
    val rights: EditText,
    val coverPreview: ImageView
)

internal class BloggerPostFormFieldsFactory(
    private val activity: Activity,
    private val dp: (Int) -> Int,
    private val makeInput: (String) -> EditText,
    private val rounded: (Int, Int) -> Drawable,
    private val panel: () -> Int,
    private val translate: (String) -> String
) {
    fun create(): BloggerPostFormFields {
        fun input(hint: String, number: Boolean = false) = makeInput(translate(hint)).apply {
            setSingleLine(true)
            minHeight = dp(52)
            if (number) inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val title = input("عنوان المقال")
        val body = makeInput(translate("وصف المقال (اختياري)")).apply {
            minLines = 5
            minHeight = dp(130)
            gravity = Gravity.TOP or Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(13), dp(12), dp(13), dp(12))
            contentDescription = translate("وصف اللعبة")
        }
        val coverUrl = input("رابط صورة الغلاف (اختياري)").apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        val relatedLink = input("رابط التحميل الأساسي (اختياري)").apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        val genre = input("نوع اللعبة (مثال: أكشن، مغامرات)")
        val releaseYear = input("سنة الإصدار (مثال: 2024)", number = true)
        val packageSize = input("الحجم (مثال: 2.5 GB)")
        val version = input("النسخة (مثال: v1.5)")
        val fileFormat = input("الصيغة (مثال: PKG)")
        val language = input("اللغة (مثال: الإنجليزية)")
        val password = input("كلمة السر (اختياري)")
        val rights = input("الحقوق (اختياري)")
        val coverPreview = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = translate("معاينة صورة غلاف المقال")
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    android.graphics.Color.argb(220, 20, 57, 91),
                    android.graphics.Color.argb(198, 7, 25, 45)
                )
            ).apply {
                cornerRadius = dp(15).toFloat()
                setStroke(dp(1), android.graphics.Color.argb(175, 76, 153, 222))
            }
            clipToOutline = true
            elevation = dp(2).toFloat()
        }
        return BloggerPostFormFields(
            title, body, coverUrl, relatedLink, genre, releaseYear,
            packageSize, version, fileFormat, language, password, rights, coverPreview
        )
    }
}
