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
}
