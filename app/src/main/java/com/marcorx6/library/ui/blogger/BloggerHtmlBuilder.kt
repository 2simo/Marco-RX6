package com.marcorx6.library.ui

import com.marcorx6.library.data.Game

/** Builds the HTML fragment used when composing a Blogger game post. */
internal object BloggerHtmlBuilder {
    fun build(game: Game): String {
        val text = game.description
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>")
        val image = if (game.coverUrl.isNotBlank()) "<p><img src=\"${game.coverUrl}\" /></p>" else ""
        return "<div><p>$text</p>$image<p><a href=\"${game.url}\">تحميل اللعبة</a></p></div>"
    }
}
