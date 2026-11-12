package com.marcorx6.library.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageTranslatorTest {
    @Test
    fun everyTranslationEntryContainsEnglishFrenchAndSpanishVariants() {
        assertTrue(
            "Incomplete translation keys: ${LanguageTranslator.incompleteTranslationKeys()}",
            LanguageTranslator.incompleteTranslationKeys().isEmpty()
        )
    }

    @Test
    fun translatesStaticLabelsIntoAllSupportedLanguages() {
        assertEquals("Downloads", LanguageTranslator.translate("التنزيلات", "en"))
        assertEquals("Téléchargements", LanguageTranslator.translate("التنزيلات", "fr"))
        assertEquals("Descargas", LanguageTranslator.translate("التنزيلات", "es"))
    }

    @Test
    fun translatesBrandNameInAllSupportedLanguagesWithoutIndexOverflow() {
        assertEquals("MARCO RX6", LanguageTranslator.translate("MARCO RX6", "en"))
        assertEquals("MARCO RX6", LanguageTranslator.translate("MARCO RX6", "fr"))
        assertEquals("MARCO RX6", LanguageTranslator.translate("MARCO RX6", "es"))
        assertEquals("MARCO RX6", LanguageTranslator.translate("MARCO RX6", "ar"))
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

    @Test
    fun translatesBloggerComposerValidationMessages() {
        assertEquals(
            "Enter both the link name and its URL",
            LanguageTranslator.translate("أدخل اسم الرابط وعنوانه معاً", "en")
        )
        assertEquals(
            "Ajoutez au moins un lien de téléchargement avant l’aperçu",
            LanguageTranslator.translate("أضف رابط تحميل واحداً على الأقل قبل المعاينة", "fr")
        )
        assertEquals(
            "Añade al menos un enlace de descarga antes de publicar",
            LanguageTranslator.translate("أضف رابط تحميل واحداً على الأقل قبل النشر", "es")
        )
    }

    @Test
    fun translatesBloggerSyncStatus() {
        assertEquals(
            "Connecting to the MARCO RX6 blog...",
            LanguageTranslator.translate("جارٍ الاتصال بمدونة MARCO RX6...", "en")
        )
        assertEquals(
            "Connexion au blog MARCO RX6...",
            LanguageTranslator.translate("جارٍ الاتصال بمدونة MARCO RX6...", "fr")
        )
        assertEquals(
            "Conectando con el blog MARCO RX6...",
            LanguageTranslator.translate("جارٍ الاتصال بمدونة MARCO RX6...", "es")
        )
    }

    @Test
    fun translatesDynamicOnboardingStepNumbers() {
        assertEquals("Step 2", LanguageTranslator.translate("خطوة 2", "en"))
        assertEquals("Étape 3", LanguageTranslator.translate("خطوة 3", "fr"))
        assertEquals("Paso 4", LanguageTranslator.translate("خطوة 4", "es"))
        assertEquals("خطوة 4", LanguageTranslator.translate("خطوة 4", "ar"))
    }

    @Test
    fun translatesDownloadBrowserControlsAndValidation() {
        assertEquals("Download browser", LanguageTranslator.translate("متصفح التحميل", "en"))
        assertEquals("Rechercher ou saisir l’adresse d’un site", LanguageTranslator.translate("ابحث أو أدخل عنوان الموقع", "fr"))
        assertEquals("La URL de la página no es válida o no es segura", LanguageTranslator.translate("رابط الصفحة غير صالح أو غير آمن", "es"))
        assertEquals("Forward", LanguageTranslator.translate("تقدم", "en"))
        assertEquals("Open externally", LanguageTranslator.translate("فتح خارجياً", "en"))
    }

    @Test
    fun translatesPublishingStudioAndAccountVerificationStrings() {
        assertEquals(
            "Enter the game information and links, then preview the post before publishing.",
            LanguageTranslator.translate("أدخل معلومات اللعبة وروابطها، ثم راجع شكل المقال قبل النشر.", "en")
        )
        assertEquals("Titre et libellés de l’article", LanguageTranslator.translate("العنوان وأقسام المقال", "fr"))
        assertEquals("Direct download links", LanguageTranslator.translate("روابط التحميل المباشرة", "en"))
        assertEquals("Nom du lien (exemple : Google Drive)", LanguageTranslator.translate("اسم الرابط (مثال: Google Drive)", "fr"))
        assertEquals("HTTPS download URL", LanguageTranslator.translate("رابط التحميل HTTPS", "en"))
        assertEquals("Download link", LanguageTranslator.translate("رابط تحميل", "en"))
        assertEquals("Supprimer ce lien", LanguageTranslator.translate("إزالة هذا الرابط", "fr"))
        assertEquals("Add another link", LanguageTranslator.translate("إضافة رابط جديد", "en"))
        assertEquals("Back from the publishing studio", LanguageTranslator.translate("الرجوع من استوديو النشر", "en"))
        assertEquals("Estudio de publicación", LanguageTranslator.translate("استوديو النشر", "es"))
        assertEquals("Edit post", LanguageTranslator.translate("تحرير المقال", "en"))
        assertEquals("El enlace debe comenzar por HTTPS", LanguageTranslator.translate("يجب أن يبدأ الرابط بـ HTTPS", "es"))
        assertEquals(
            "Enter your current password to verify your identity before deletion.",
            LanguageTranslator.translate("أدخل كلمة المرور الحالية للتحقق من هويتك قبل الحذف.", "en")
        )
    }

    @Test
    fun translatesNewVectorNavigationAccessibilityLabels() {
        assertEquals("Open section", LanguageTranslator.translate("فتح القسم", "en"))
        assertEquals("Ouvrir la section", LanguageTranslator.translate("فتح القسم", "fr"))
        assertEquals("Abrir sección", LanguageTranslator.translate("فتح القسم", "es"))
        assertEquals("Open profile", LanguageTranslator.translate("فتح الملف الشخصي", "en"))
        assertEquals("Ouvrir le profil", LanguageTranslator.translate("فتح الملف الشخصي", "fr"))
        assertEquals("Abrir perfil", LanguageTranslator.translate("فتح الملف الشخصي", "es"))
    }

    @Test
    fun translatesNewAccountAndCommunityLabelsIntoAllSupportedLanguages() {
        val cases = listOf(
            "ملف MARCO الشخصي" to listOf("MARCO profile", "Profil MARCO", "Perfil de MARCO"),
            "مرحباً بك في مجتمع MARCO" to listOf("Welcome to the MARCO community", "Bienvenue dans la communauté MARCO", "Te damos la bienvenida a la comunidad MARCO"),
            "تسجيل الخروج من MARCO؟" to listOf("Sign out of MARCO?", "Se déconnecter de MARCO ?", "¿Cerrar sesión en MARCO?"),
            "أنشئ حساب MARCO لمتابعة هذا الملف الشخصي" to listOf("Create a MARCO account to follow this profile", "Créez un compte MARCO pour suivre ce profil", "Crea una cuenta MARCO para seguir este perfil"),
            "+ تبويب جديد" to listOf("+ New tab", "+ Nouvel onglet", "+ Nueva pestaña"),
            "رفضت قواعد Firestore قراءة قائمة المتابعين (PERMISSION_DENIED)." to listOf("Firestore rules denied access to the followers list (PERMISSION_DENIED).", "Les règles Firestore ont refusé l’accès à la liste des abonnés (PERMISSION_DENIED).", "Las reglas de Firestore rechazaron el acceso a la lista de seguidores (PERMISSION_DENIED).")
        )
        cases.forEach { (source, expected) ->
            assertEquals(expected[0], LanguageTranslator.translate(source, "en"))
            assertEquals(expected[1], LanguageTranslator.translate(source, "fr"))
            assertEquals(expected[2], LanguageTranslator.translate(source, "es"))
            assertEquals(source, LanguageTranslator.translate(source, "ar"))
        }
    }
    @Test
    fun translatesExpandedAppWideControlsIntoAllSupportedLanguages() {
        val cases = listOf(
            "التشخيص وسجل الأخطاء" to listOf("Diagnostics & error log", "Diagnostics et journal d’erreurs", "Diagnóstico y registro de errores"),
            "عدد التنزيلات المتزامنة" to listOf("Concurrent downloads", "Téléchargements simultanés", "Descargas simultáneas"),
            "إيقاف الكل" to listOf("Pause all", "Tout mettre en pause", "Pausar todo"),
            "اختيار صورة الغلاف من الهاتف" to listOf("Choose cover image from phone", "Choisir une image de couverture sur le téléphone", "Elegir imagen de portada del teléfono"),
            "اختر القسم الذي تريد تصفحه" to listOf("Choose the section you want to browse", "Choisissez la section à parcourir", "Elige la sección que quieres explorar"),
            "MARCO  /  PERSONALIZATION" to listOf("MARCO  /  PERSONALIZATION", "MARCO  /  PERSONNALISATION", "MARCO  /  PERSONALIZACIÓN")
        )
        cases.forEach { (source, expected) ->
            assertEquals(expected[0], LanguageTranslator.translate(source, "en"))
            assertEquals(expected[1], LanguageTranslator.translate(source, "fr"))
            assertEquals(expected[2], LanguageTranslator.translate(source, "es"))
            assertEquals(source, LanguageTranslator.translate(source, "ar"))
        }
    }

    @Test
    fun translatesDownloadDialogsAndFileControlsIntoAllSupportedLanguages() {
        val cases = listOf(
            "إضافة إلى التنزيلات" to listOf("Add to downloads", "Ajouter aux téléchargements", "Añadir a descargas"),
            "رابط التنزيل" to listOf("Download link", "Lien de téléchargement", "Enlace de descarga"),
            "اسم الملف" to listOf("File name", "Nom du fichier", "Nombre del archivo"),
            "مجلد الحفظ" to listOf("Save folder", "Dossier d’enregistrement", "Carpeta de guardado"),
            "لا توجد تنزيلات في القائمة" to listOf("There are no downloads in the list", "Aucun téléchargement dans la liste", "No hay descargas en la lista")
        )
        cases.forEach { (source, expected) ->
            assertEquals(expected[0], LanguageTranslator.translate(source, "en"))
            assertEquals(expected[1], LanguageTranslator.translate(source, "fr"))
            assertEquals(expected[2], LanguageTranslator.translate(source, "es"))
        }
    }

    @Test
    fun translatesPreviouslyUntranslatedAccountNotificationAndBrowserLabels() {
        val cases = listOf(
            "لا توجد رسائل جديدة حالياً." to listOf("No new messages right now.", "Aucun nouveau message pour le moment.", "No hay mensajes nuevos por ahora."),
            "تعذر تحميل الإشعارات. تحقق من الاتصال." to listOf("Could not load notifications. Check your connection.", "Impossible de charger les notifications. Vérifiez votre connexion.", "No se pudieron cargar las notificaciones. Comprueba tu conexión."),
            "لا يوجد متابعون بعد." to listOf("No followers yet.", "Aucun abonné pour le moment.", "Aún no hay seguidores."),
            "لغة المحتوى" to listOf("Content language", "Langue du contenu", "Idioma del contenido"),
            "مسح البيانات" to listOf("Clear data", "Effacer les données", "Borrar datos"),
            "التبويبات المفتوحة" to listOf("Open tabs", "Onglets ouverts", "Pestañas abiertas"),
            "أدخل رابطاً أو عبارة للبحث" to listOf("Enter a URL or search term", "Saisissez une URL ou un terme de recherche", "Introduce una URL o un término de búsqueda"),
            "إعدادات حجب الإعلانات" to listOf("Ad-blocking settings", "Paramètres de blocage des publicités", "Ajustes del bloqueo de anuncios")
        )
        cases.forEach { (source, expected) ->
            assertEquals(expected[0], LanguageTranslator.translate(source, "en"))
            assertEquals(expected[1], LanguageTranslator.translate(source, "fr"))
            assertEquals(expected[2], LanguageTranslator.translate(source, "es"))
            assertEquals(source, LanguageTranslator.translate(source, "ar"))
        }
    }

    @Test
    fun translatesDynamicBrowserAndAdBlockLabels() {
        assertEquals("Tab 3", LanguageTranslator.translate("تبويب 3", "en"))
        assertEquals("Onglet 4", LanguageTranslator.translate("التبويب 4", "fr"))
        assertEquals("Pestaña 5", LanguageTranslator.translate("تبويب 5", "es"))
        assertEquals("Ads blocked: 12", LanguageTranslator.translate("عدد الإعلانات المحجوبة: 12", "en"))
        assertEquals("Publicités bloquées : 8", LanguageTranslator.translate("عدد الإعلانات المحجوبة: 8", "fr"))
        assertEquals("No se pudo cargar la lista (403)", LanguageTranslator.translate("تعذر تحميل القائمة (403)", "es"))
    }

    @Test
    fun translatesBlackAndSilverPaletteName() {
        assertEquals("Black & Silver", LanguageTranslator.translate("أسود فضي", "en"))
        assertEquals("Noir et argent", LanguageTranslator.translate("أسود فضي", "fr"))
        assertEquals("Negro y plata", LanguageTranslator.translate("أسود فضي", "es"))
        assertEquals("أسود فضي", LanguageTranslator.translate("أسود فضي", "ar"))
    }

}
