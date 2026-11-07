package com.marcorx6.library.ui

import com.marcorx6.library.data.Game
import com.marcorx6.library.network.BloggerFeedParser
import java.text.Normalizer
import java.util.Locale

/**
 * Resolves one consistent language label for every game-cover surface.
 * Prefer the stored language field and fall back to the original Blogger HTML
 * for older or imported games.
 */
internal object GameLanguageBadge {
    fun resolve(game: Game): String {
        val articleLanguage = BloggerFeedParser.extractLanguage(game.bloggerHtml)
        for (candidate in listOf(game.language, articleLanguage)) {
            val normalized = Normalizer.normalize(
                candidate.lowercase(Locale.ROOT)
                    .replace(Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069]"), "")
                    .replace('ـ', ' ')
                    .trim(),
                Normalizer.Form.NFD
            ).replace(Regex("\\p{Mn}+"), "")
                .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
                .trim()

            if (normalized.isBlank()) continue
            // When both languages are listed, keep the single Arabic label.
            if (normalized.contains("arab") || normalized.contains("عرب")) return "Arabic"
            if (normalized.contains("english") || normalized.contains("انجليز") ||
                normalized == "en" || normalized.startsWith("en ") ||
                normalized.endsWith(" en") || normalized.contains(" en ")
            ) return "English"
        }
        return ""
    }
}
