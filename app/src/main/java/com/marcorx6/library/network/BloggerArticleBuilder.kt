package com.marcorx6.library.network

/**
 * Builds the responsive HTML shared by the in-app article preview and Blogger publishing.
 */
internal object BloggerArticleBuilder {
    private fun escape(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

    // Structured article template shared by the preview and Blogger publishing.
    // Modern responsive editorial template shared by preview and Blogger publishing.
    fun build(title: String, categories: String, genre: String, releaseYear: String, description: String, coverUrl: String, downloadUrl: String): String {
        val safeTitle = escape(title)
        val safeCategories = escape(categories)
        val safeGenre = escape(genre)
        val safeYear = escape(releaseYear)
        val descriptionHtml = description.trim().split(Regex("\\n\\s*\\n"))
            .filter { it.isNotBlank() }
            .joinToString("") {
                "<p style=\"margin:0 0 16px;\">" + escape(it).replace("\n", "<br/>") + "</p>"
            }
        val safeCover = escape(BloggerFeedParser.normalizeBloggerImageUrl(coverUrl))
        val safeDownload = escape(downloadUrl)
        val categoryBlock = if (safeCategories.isNotBlank()) {
            "<span style=\"display:inline-block;padding:6px 12px;border:1px solid #2865a5;border-radius:30px;background:#102b49;color:#c9e4ff;font-size:12px;font-weight:700;\">$safeCategories</span>"
        } else ""
        val metaItems = listOfNotNull(
            safeGenre.takeIf { it.isNotBlank() }?.let { "<span style=\"display:inline-block;margin:5px 8px 5px 0;padding:7px 11px;background:#19243a;border:1px solid #293752;border-radius:9px;color:#cbd5e1;font-size:12px;\"><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#60a5fa\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\" style=\"vertical-align:middle;margin-left:6px;\" aria-hidden=\"true\"><path d=\"M6 12h4m-2-2v4m6-3h.01M17 13h.01\"/><path d=\"M7 7h10a4 4 0 0 1 3.8 2.8l1.1 3.7a2 2 0 0 1-3.1 2.2L16 14H8l-2.8 1.7a2 2 0 0 1-3.1-2.2l1.1-3.7A4 4 0 0 1 7 7Z\"/></svg>$it</span>" },
            safeYear.takeIf { it.isNotBlank() }?.let { "<span style=\"display:inline-block;margin:5px 8px 5px 0;padding:7px 11px;background:#19243a;border:1px solid #293752;border-radius:9px;color:#cbd5e1;font-size:12px;\"><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#60a5fa\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\" style=\"vertical-align:middle;margin-left:6px;\" aria-hidden=\"true\"><rect x=\"3\" y=\"5\" width=\"18\" height=\"16\" rx=\"2\"/><path d=\"M16 3v4M8 3v4M3 10h18\"/></svg>$it</span>" }
        ).joinToString("")
        val coverBlock = if (safeCover.isNotBlank()) {
            "<div style=\"margin:22px 0 24px;padding:7px;background:#111b2d;border:1px solid #2a3650;border-radius:18px;\"><img src=\"$safeCover\" alt=\"$safeTitle\" style=\"display:block;width:100%;height:auto;max-height:520px;object-fit:cover;border-radius:13px;\"/></div>"
        } else ""
        val downloadBlock = if (safeDownload.isNotBlank()) {
            "<div style=\"text-align:center;margin:30px 0 24px;\"><a href=\"$safeDownload\" style=\"display:inline-block;padding:14px 30px;border-radius:999px;background:#237fe5;color:#ffffff;text-decoration:none;font-size:15px;font-weight:800;letter-spacing:.2px;box-shadow:0 8px 24px rgba(35,127,229,.25);\"><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"18\" height=\"18\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" style=\"vertical-align:middle;margin-left:8px;\" aria-hidden=\"true\"><path d=\"M12 3v12m-5-5 5 5 5-5\"/><path d=\"M5 17v3h14v-3\"/></svg>تحميل اللعبة الآن</a></div>"
        } else ""
        return """
            <article class="marcorx6-post" dir="rtl" style="box-sizing:border-box;max-width:900px;margin:18px auto;padding:clamp(16px,3vw,30px);overflow:hidden;border:1px solid #29334a;border-radius:22px;background:linear-gradient(145deg,#111a2c 0%,#0b1120 58%,#17112b 100%);color:#e8edf7;font-family:Arial,Tahoma,sans-serif;line-height:1.9;">
              <header style="padding:4px 0 18px;border-bottom:1px solid #29334a;">
                <div style="margin-bottom:12px;color:#a78bfa;font-size:11px;font-weight:800;letter-spacing:2px;">MARCO RX6 <span style="color:#64748b;">/ GAME DATABASE</span></div>
                <h1 style="margin:0 0 14px;color:#ffffff;font-size:clamp(24px,5vw,34px);font-weight:800;line-height:1.35;">$safeTitle</h1>
                $categoryBlock
              </header>
              $coverBlock
              <div style="margin:0 0 18px;">$metaItems</div>
              <section style="color:#d5deec;font-size:16px;line-height:2.05;overflow-wrap:anywhere;">$descriptionHtml</section>
              $downloadBlock
              <footer style="display:flex;align-items:center;justify-content:space-between;gap:10px;flex-wrap:wrap;margin-top:28px;padding-top:16px;border-top:1px solid #29334a;color:#8793a8;font-size:11px;">
                <span style="font-weight:800;letter-spacing:1px;color:#b9a2f5;">MARCO RX6</span>
                <span>مكتبة الألعاب والتطبيقات • اكتشف المزيد</span>
              </footer>
            </article>
        """.trimIndent()
    }
}
