package com.marcorx6.library.ui

import android.widget.ImageView

internal class CoverDisplayController(
    private val normalizeUrl: (String) -> String,
    private val resolveKey: (String, String) -> String,
    private val loadBitmap: (String, String, (android.graphics.Bitmap?) -> Unit) -> Unit
) {
    private data class RequestState(val key: String)

    fun load(image: ImageView, localPath: String, imageUrl: String) {
        val resolvedImageUrl = normalizeUrl(imageUrl)
        if (localPath.isBlank() && resolvedImageUrl.isBlank()) {
            image.tag = null
            // The system gallery glyph looks oversized in full-width cover previews.
            // Keep the empty state centered and visually quiet until a real cover exists.
            image.scaleType = ImageView.ScaleType.CENTER_INSIDE
            image.setPadding(
                (image.resources.displayMetrics.density * 28).toInt(),
                (image.resources.displayMetrics.density * 28).toInt(),
                (image.resources.displayMetrics.density * 28).toInt(),
                (image.resources.displayMetrics.density * 28).toInt()
            )
            image.imageTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.rgb(145, 153, 166)
            )
            image.setImageResource(android.R.drawable.ic_menu_gallery)
            return
        }

        val key = resolveKey(localPath, resolvedImageUrl)
        val previous = image.tag as? RequestState
        if (previous?.key == key) {
            // A matching request is already displayed or in flight. Do not reset the
            // drawable or enqueue another callback for the same ImageView.
            return
        }

        val state = RequestState(key)
        image.tag = state
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        image.setPadding(0, 0, 0, 0)
        image.imageTintList = null
        image.setImageResource(android.R.drawable.ic_menu_gallery)
        loadBitmap(localPath, resolvedImageUrl) { bitmap ->
            if (image.tag === state) {
                if (bitmap != null) {
                    image.setImageBitmap(bitmap)
                } else {
                    // Permit a later render to retry a failed cover request.
                    image.tag = null
                }
            }
        }
    }
}
