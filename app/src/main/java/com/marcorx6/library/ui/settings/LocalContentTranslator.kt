package com.marcorx6.library.ui.settings

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * On-device translation for user-visible dynamic content.
 *
 * Models are downloaded by ML Kit on demand (roughly tens of MB per language).
 * Callers should keep original data unchanged and render only the translated copy.
 */
internal class LocalContentTranslator(context: Context) {
    private val appContext = context.applicationContext
    private val languageIdentifier = LanguageIdentification.getClient()
    private var activeTranslator: Translator? = null
    private var activePair: Pair<String, String>? = null

    fun downloadModel(
        sourceLanguage: String,
        targetLanguage: String,
        onReady: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val translator = translatorFor(sourceLanguage, targetLanguage)
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { onReady() }
            .addOnFailureListener { error -> onError(error as? Exception ?: Exception(error)) }
    }

    fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        onTranslated: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (text.isBlank() || sourceLanguage == targetLanguage) {
            onTranslated(text)
            return
        }

        val translator = translatorFor(sourceLanguage, targetLanguage)
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener(onTranslated)
                    .addOnFailureListener { error ->
                        onError(error as? Exception ?: Exception(error))
                    }
            }
            .addOnFailureListener { error ->
                onError(error as? Exception ?: Exception(error))
            }
    }

    private fun translatorFor(source: String, target: String): Translator {
        require(source in SUPPORTED_LANGUAGES && target in SUPPORTED_LANGUAGES) {
            "Unsupported translation language pair: $source -> $target"
        }
        val pair = source to target
        if (activePair != pair || activeTranslator == null) {
            activeTranslator?.close()
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()
            activeTranslator = Translation.getClient(options)
            activePair = pair
        }
        return checkNotNull(activeTranslator)
    }

    fun close() {
        activeTranslator?.close()
        activeTranslator = null
        activePair = null
    }

    companion object {
        private val SUPPORTED_LANGUAGES = setOf("ar", "en", "fr", "es")
    }
}
