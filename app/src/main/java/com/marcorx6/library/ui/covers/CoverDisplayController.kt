package com.marcorx6.library.ui

import android.widget.ImageView

internal class CoverDisplayController(
    private val normalizeUrl: (String) -> String,
    private val resolveKey: (String, String) -> String,
    private val loadBitmap: (String, String, (android.graphics.Bitmap?) -> Unit) -> Unit
) {
    fun load(image: ImageView, localPath: String, imageUrl: String) {
        val resolvedImageUrl = normalizeUrl(imageUrl)
        if (localPath.isBlank() && resolvedImageUrl.isBlank()) {
            image.tag = null
            image.setImageResource(android.R.drawable.ic_menu_gallery)
            return
        }

        val key = resolveKey(localPath, resolvedImageUrl)
        val sameRequest = image.tag == key
        // Keep the currently displayed bitmap when the same cover is requested again.
        // Resetting to the placeholder on every render caused visible flashing while
        // cached images were delivered back to the main thread.
        if (!sameRequest || image.drawable == null) {
            image.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        image.tag = key
        loadBitmap(localPath, resolvedImageUrl) { bitmap ->
            if (bitmap != null && image.tag == key) image.setImageBitmap(bitmap)
        }
    }
}
