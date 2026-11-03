package com.marcorx6.library.ui

import android.widget.ImageView

internal class CoverDisplayController(
    private val normalizeUrl: (String) -> String,
    private val resolveKey: (String, String) -> String,
    private val loadBitmap: (String, String, (android.graphics.Bitmap?) -> Unit) -> Unit
) {
    fun load(image: ImageView, localPath: String, imageUrl: String) {
        val resolvedImageUrl = normalizeUrl(imageUrl)
        image.setImageResource(android.R.drawable.ic_menu_gallery)
        if (localPath.isBlank() && resolvedImageUrl.isBlank()) {
            image.tag = null
            return
        }
        val key = resolveKey(localPath, resolvedImageUrl)
        image.tag = key
        loadBitmap(localPath, resolvedImageUrl) { bitmap ->
            if (bitmap != null && image.tag == key) image.setImageBitmap(bitmap)
        }
    }
}
