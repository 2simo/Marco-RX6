package com.marcorx6.library.ui

internal object BloggerPostInputValidator {
    enum class Field { TITLE, COVER_URL, RELATED_LINK, BODY }

    data class Failure(
        val message: String,
        val field: Field? = null,
        val longMessage: Boolean = false
    )

    fun validate(
        coverUploadInProgress: Boolean,
        pendingCoverLocalPath: String,
        selectedLabels: Set<String>,
        title: String,
        coverUrl: String,
        relatedLink: String,
        content: String
    ): Failure? {
        if (coverUploadInProgress) {
            return Failure("انتظر حتى يكتمل رفع صورة الغلاف", longMessage = true)
        }
        if (pendingCoverLocalPath.isNotBlank() && coverUrl.isBlank()) {
            return Failure("صورة الغلاف لم تكتمل عملية رفعها. أعد اختيار الصورة وانتظر رسالة نجاح الرفع.", longMessage = true)
        }
        if (selectedLabels.isEmpty()) {
            return Failure("اختر Label واحداً على الأقل من المدونة")
        }
        if (title.isBlank()) {
            return Failure("أدخل عنوان المقال", Field.TITLE)
        }
        if (coverUrl.isNotBlank() && !coverUrl.startsWith("https://")) {
            return Failure("أدخل رابط صورة يبدأ بـ HTTPS", Field.COVER_URL)
        }
        if (relatedLink.isNotBlank() && !relatedLink.startsWith("https://")) {
            return Failure("أدخل رابطاً يبدأ بـ HTTPS", Field.RELATED_LINK)
        }
        // The structured game information and download links can form a complete
        // post without a free-form description, so BODY is intentionally optional.
        return null
    }
}
