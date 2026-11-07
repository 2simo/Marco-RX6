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
