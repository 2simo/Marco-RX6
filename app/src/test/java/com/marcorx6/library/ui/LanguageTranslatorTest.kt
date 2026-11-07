package com.marcorx6.library.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageTranslatorTest {
    @Test
    fun translatesStaticLabelsIntoAllSupportedLanguages() {
        assertEquals("Downloads", LanguageTranslator.translate("التنزيلات", "en"))
        assertEquals("Téléchargements", LanguageTranslator.translate("التنزيلات", "fr"))
        assertEquals("Descargas", LanguageTranslator.translate("التنزيلات", "es"))
    }

    @Test
    fun translatesFormalArabicEmptyState() {
        assertEquals("No downloads yet.", LanguageTranslator.translate("لا توجد تنزيلات حالياً.", "en"))
        assertEquals("Aucun téléchargement pour le moment.", LanguageTranslator.translate("لا توجد تنزيلات حالياً.", "fr"))
        assertEquals("Aún no hay descargas.", LanguageTranslator.translate("لا توجد تنزيلات حالياً.", "es"))
        assertEquals("لا توجد تنزيلات حالياً.", LanguageTranslator.translate("لا توجد تنزيلات حالياً.", "ar"))
    }

    @Test
    fun translatesDynamicServerAndDriveErrorsWithoutLosingDetails() {
        assertEquals(
            "The server rejected the download URL (HTTP 403)",
            LanguageTranslator.translate("الخادم رفض رابط التنزيل (HTTP 403)", "en")
        )
        assertEquals(
            "Impossible d’envoyer l’image vers Google Drive (403): Forbidden",
            LanguageTranslator.translate("فشل رفع الصورة إلى Google Drive (403): Forbidden", "fr")
        )
        assertEquals(
            "No se pudo verificar la URL de la imagen (HTTP 404, type: text/html)",
            LanguageTranslator.translate("تعذر التحقق من رابط الصورة (HTTP 404، النوع: text/html)", "es")
        )
    }
    @Test
    fun translatesAllLibrarySectionHeadingsIntoSupportedLanguages() {
        val search = "MARCO  /  البحث الذكي"
        assertEquals("MARCO  /  SMART SEARCH", LanguageTranslator.translate(search, "en"))
        assertEquals("MARCO  /  RECHERCHE INTELLIGENTE", LanguageTranslator.translate(search, "fr"))
        assertEquals("MARCO  /  BÚSQUEDA INTELIGENTE", LanguageTranslator.translate(search, "es"))

        val hub = "MARCO  /  مركز الألعاب الرسمي"
        assertEquals("MARCO  /  OFFICIAL GAME HUB", LanguageTranslator.translate(hub, "en"))
        assertEquals("MARCO  /  CENTRE DE JEUX OFFICIEL", LanguageTranslator.translate(hub, "fr"))
        assertEquals("MARCO  /  CENTRO OFICIAL DE JUEGOS", LanguageTranslator.translate(hub, "es"))

        val games = "MARCO  /  الألعاب"
        assertEquals("MARCO  /  GAMES", LanguageTranslator.translate(games, "en"))
        assertEquals("MARCO  /  JEUX", LanguageTranslator.translate(games, "fr"))
        assertEquals("MARCO  /  JUEGOS", LanguageTranslator.translate(games, "es"))
    }

}
