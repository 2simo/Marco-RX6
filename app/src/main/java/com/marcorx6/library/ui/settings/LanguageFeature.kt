package com.marcorx6.library.ui

import androidx.activity.ComponentActivity
import android.content.SharedPreferences
import android.widget.Toast\nimport com.marcorx6.library.ui.settings.LocalContentTranslator

/** Owns language preference access, translation, and language picker interaction. */
internal class LanguageFeature(
    private val activity: ComponentActivity,
    private val preferences: SharedPreferences,
    private val styleDialog: (android.app.AlertDialog) -> Unit,
    private val onLanguageChanged: () -> Unit
) {
    fun currentLanguage(): String =
        preferences.getString("language", "ar") ?: "ar"

    fun translate(value: String): String =
        LanguageTranslator.translate(value, currentLanguage())

    fun showPicker() {
        val codes = arrayOf("ar", "en", "fr", "es")
        val labels = arrayOf(translate("العربية"), translate("الإنجليزية"), translate("الفرنسية"), translate("الإسبانية"))
        val current = codes.indexOf(currentLanguage()).coerceAtLeast(0)
        android.app.AlertDialog.Builder(activity)
            .setTitle(translate("اختر لغة التطبيق"))
            .setSingleChoiceItems(labels, current) { dialog, which ->
                preferences.edit().putString("language", codes[which]).apply()
                dialog.dismiss()
                onLanguageChanged()
                Toast.makeText(activity, translate("تم حفظ اللغة"), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(translate("إغلاق"), null)
            .setPositiveButton(translate("تحميل نموذج الترجمة")) { _, _ ->
                val target = currentLanguage()
                if (target == "ar") {
                    Toast.makeText(activity, translate("العربية لا تحتاج إلى نموذج ترجمة"), Toast.LENGTH_SHORT).show()
                } else {
                    val translator = LocalContentTranslator(activity)
                    Toast.makeText(activity, translate("جارٍ تحميل نموذج الترجمة"), Toast.LENGTH_SHORT).show()
                    translator.downloadModel(
                        sourceLanguage = "ar",
                        targetLanguage = target,
                        onReady = {
                            Toast.makeText(activity, translate("نموذج الترجمة جاهز"), Toast.LENGTH_LONG).show()
                            translator.close()
                        },
                        onError = {
                            Toast.makeText(activity, translate("تعذر تحميل نموذج الترجمة"), Toast.LENGTH_LONG).show()
                            translator.close()
                        }
                    )
                }
            }
            .show()
            .also(styleDialog)
    }
}
