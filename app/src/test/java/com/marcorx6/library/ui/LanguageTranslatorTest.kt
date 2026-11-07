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


    @Test
    fun preservesUnknownTextAndUnsupportedLanguageCodes() {
        val source = "نص جديد غير موجود في القاموس"
        assertEquals(source, LanguageTranslator.translate(source, "en"))
        assertEquals(source, LanguageTranslator.translate(source, "fr"))
        assertEquals(source, LanguageTranslator.translate(source, "es"))
        assertEquals(source, LanguageTranslator.translate(source, "de"))
        assertEquals(source, LanguageTranslator.translate(source, ""))
    }

    @Test
    fun translatesRatingInterfaceIntoAllSupportedLanguages() {
        val source = "قيّم هذه اللعبة"
        assertEquals("Rate this game", LanguageTranslator.translate(source, "en"))
        assertEquals("Évaluer ce jeu", LanguageTranslator.translate(source, "fr"))
        assertEquals("Califica este juego", LanguageTranslator.translate(source, "es"))
        assertEquals(source, LanguageTranslator.translate(source, "ar"))
    }

    @Test
    fun translatesTranslationModelMessagesIntoAllSupportedLanguages() {
        val source = "تحميل نموذج الترجمة"
        assertEquals("Download translation model", LanguageTranslator.translate(source, "en"))
        assertEquals("Télécharger le modèle de traduction", LanguageTranslator.translate(source, "fr"))
        assertEquals("Descargar el modelo de traducción", LanguageTranslator.translate(source, "es"))
    }

    @Test
    fun keepsTheCanonicalTranslationForPreviouslyDuplicatedKeys() {
        assertEquals(
            "Personaliza MARCO a tu manera",
            LanguageTranslator.translate("خصص تجربة MARCO بالطريقة التي تناسبك", "es")
        )
        assertEquals(
            "Volver a intentar",
            LanguageTranslator.translate("إعادة المحاولة", "es")
        )
        assertEquals(
            "Todo listo",
            LanguageTranslator.translate("جاهز للانطلاق", "es")
        )
        assertEquals(
            "Previous",
            LanguageTranslator.translate("السابق", "en")
        )
        assertEquals(
            "Interface animations and transitions",
            LanguageTranslator.translate("حركات وانتقالات الواجهة", "en")
        )
        assertEquals(
            "Interface transitions enabled",
            LanguageTranslator.translate("تم تفعيل انتقالات الواجهة", "en")
        )
    }

    @Test
    fun translatesDynamicBlockedRequestCountWithoutChangingItsValue() {
        assertEquals(
            "Ads and trackers blocked: 17",
            LanguageTranslator.translate("الإعلانات والمتتبعات المحجوبة: 17", "en")
        )
        assertEquals(
            "Publicités et traceurs bloqués : 17",
            LanguageTranslator.translate("الإعلانات والمتتبعات المحجوبة: 17", "fr")
        )
        assertEquals(
            "Anuncios y rastreadores bloqueados: 17",
            LanguageTranslator.translate("الإعلانات والمتتبعات المحجوبة: 17", "es")
        )
    }
}
