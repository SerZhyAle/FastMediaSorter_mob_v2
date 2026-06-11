package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.data.delivery.DeliveredNativeLibraryLoader
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * ML Kit translation + language-identification backend split out of [TranslationManager]
 * (S0386 Phase 01). Instance-scoped: carries the per-call [TranslationManager.TranslationCallback]
 * for the model-download prompt. Behavior is identical to the pre-split monolith.
 */
class TranslationBackend(
    private val context: Context,
    private val callback: TranslationManager.TranslationCallback,
    private val settingsRepository: SettingsRepository,
    private val capabilityRepository: DeliverableCapabilityRepository,
    private val libraryLoader: DeliveredNativeLibraryLoader
) : TextTranslationFacade {

    private var translator: Translator? = null

    private val languageIdentifier by lazy {
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(0.5f)
                .build()
        )
    }
    private val modelManager = RemoteModelManager.getInstance()

    private var currentSourceLang = TranslateLanguage.ENGLISH
    private var currentTargetLang = TranslateLanguage.RUSSIAN

    private fun ensureNativeLibrariesLoaded(): Boolean {
        if (!capabilityRepository.isInstalledBlocking(DeliverableSet.TRANSLATION)) {
            Timber.i("Translation engine not installed - native libraries unavailable")
            return false
        }
        try {
            libraryLoader.load(DeliverableSet.TRANSLATION)
            return true
        } catch (e: Exception) {
            Timber.e(e, "Failed to load translation native libraries")
            return false
        }
    }

    override suspend fun getTargetLanguageCode(): String? {
        val settings = settingsRepository.getSettings().first()
        return TranslationManager.languageCodeToMLKit(settings.translationTargetLanguage)
    }

    override suspend fun detectLanguage(text: String): String {
        if (text.isBlank()) return TranslateLanguage.ENGLISH
        if (!ensureNativeLibrariesLoaded()) return TranslateLanguage.ENGLISH

        return try {
            val detectedLang = languageIdentifier.identifyLanguage(text).await()
            if (detectedLang == "und") { // Undetermined
                Timber.d("Language detection failed, using English as fallback")
                TranslateLanguage.ENGLISH
            } else {
                Timber.d("Detected language: $detectedLang")
                detectedLang
            }
        } catch (e: Exception) {
            Timber.e(e, "Error detecting language")
            TranslateLanguage.ENGLISH
        }
    }

    /**
     * Check if a direct translation path exists between two languages in ML Kit.
     * ML Kit only supports translations to/from English, not between other language pairs.
     */
    private fun isDirectTranslationSupported(sourceLang: String, targetLang: String): Boolean {
        return sourceLang == TranslateLanguage.ENGLISH ||
               targetLang == TranslateLanguage.ENGLISH ||
               sourceLang == targetLang
    }

    override suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String
    ): String? {
        if (text.isBlank()) return null

        // Capability gate: the translation engine must be bundled or downloaded (S0386 Pillar A).
        // The enable-point UX (Phase 06) prompts for download before reaching here; this is the
        // defensive fallback so a missing engine degrades to "no translation" instead of crashing.
        if (!ensureNativeLibrariesLoaded()) {
            return null
        }

        Timber.d("translate() called: sourceLang='$sourceLang', targetLang='$targetLang', textLength=${text.length}")

        // Auto-detect source language if requested
        val actualSourceLang = if (sourceLang == "auto") {
            val detected = detectLanguage(text)
            Timber.d("Auto-detection: sourceLang='auto' → detected='$detected'")
            detected
        } else {
            Timber.d("Using provided source language: '$sourceLang'")
            sourceLang
        }

        // If source and target are the same, return original text
        if (actualSourceLang == targetLang) {
            Timber.d("Source and target languages are identical, skipping translation")
            return text
        }

        try {
            // Check if direct translation is supported
            if (!isDirectTranslationSupported(actualSourceLang, targetLang)) {
                Timber.d("Direct translation $actualSourceLang→$targetLang not supported. Using two-step via English.")

                // Step 1: source → English
                val intermediateText = translateDirect(text, actualSourceLang, TranslateLanguage.ENGLISH)
                if (intermediateText == null) {
                    Timber.e("Intermediate translation ($actualSourceLang→en) failed")
                    return null
                }

                Timber.d("Intermediate translation successful: ${text.take(50)}... → ${intermediateText.take(50)}...")

                // Step 2: English → target
                val finalText = translateDirect(intermediateText, TranslateLanguage.ENGLISH, targetLang)
                if (finalText == null) {
                    Timber.e("Final translation (en→$targetLang) failed")
                    return null
                }

                Timber.d("Two-step translation completed: $actualSourceLang→en→$targetLang")
                return finalText
            } else {
                // Direct translation supported
                return translateDirect(text, actualSourceLang, targetLang)
            }
        } catch (e: Exception) {
            Timber.e(e, "Translation error")
            callback.showError(context.getString(R.string.translation_error))
            return null
        }
    }

    private suspend fun translateDirect(
        text: String,
        sourceLang: String,
        targetLang: String
    ): String? {
        if (text.isBlank()) return null

        try {
            // Reinitialize translator if language pair changed
            Timber.d("translateDirect: translator=${translator != null}, current=($currentSourceLang→$currentTargetLang), requested=($sourceLang→$targetLang)")

            if (translator == null || sourceLang != currentSourceLang || targetLang != currentTargetLang) {
                translator?.close()

                Timber.d("Reinitializing translator (was: $currentSourceLang→$currentTargetLang, now: $sourceLang→$targetLang)")

                currentSourceLang = sourceLang
                currentTargetLang = targetLang

                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(sourceLang)
                    .setTargetLanguage(targetLang)
                    .build()

                translator = Translation.getClient(options)

                // Check if model is downloaded, prompt user if not
                val targetModel = TranslateRemoteModel.Builder(targetLang).build()
                val isModelDownloaded = modelManager.isModelDownloaded(targetModel).await()

                if (!isModelDownloaded) {
                    // Wait (race-free) for the user to confirm the download in the prompt dialog.
                    if (!awaitModelDownloadConfirmation(targetLang)) {
                        Timber.d("Translation model download declined by user")
                        return null
                    }

                    // Download model and wait for completion (no WiFi-only restriction)
                    Timber.d("Starting translation model download: $targetLang")
                    val conditions = DownloadConditions.Builder().build()
                    translator?.downloadModelIfNeeded(conditions)?.await()
                    Timber.i("Translation model download completed: $targetLang")
                }

                // Always ensure model is ready before use (even if isModelDownloaded=true, model might be loading)
                Timber.d("Ensuring translation model is ready: $targetLang")
                val ensureConditions = DownloadConditions.Builder().build()
                translator?.downloadModelIfNeeded(ensureConditions)?.await()
                Timber.d("Translation model ready for use: $targetLang")
            }

            return translator?.translate(text)?.await()
        } catch (e: Exception) {
            Timber.e(e, "Direct translation error: $sourceLang→$targetLang (Fix with AI)")

            // Check if model is corrupted - delete and redownload automatically
            if (e.message?.contains("model files not found", ignoreCase = true) == true ||
                e.message?.contains("downloadModelIfNeeded", ignoreCase = true) == true) {
                Timber.w("Translation model appears corrupted, deleting and re-downloading: $targetLang")
                try {
                    val targetModel = TranslateRemoteModel.Builder(targetLang).build()
                    modelManager.deleteDownloadedModel(targetModel).await()
                    Timber.i("Deleted corrupted translation model: $targetLang")

                    // Prompt for re-download and wait (race-free) for the user's decision.
                    if (!awaitModelDownloadConfirmation(targetLang)) {
                        Timber.d("Translation model re-download declined by user")
                        return null
                    }

                    // Re-download model (no WiFi-only restriction)
                    Timber.d("Starting translation model re-download: $targetLang")
                    val conditions = DownloadConditions.Builder().build()
                    translator?.downloadModelIfNeeded(conditions)?.await()
                    Timber.i("Translation model re-download completed: $targetLang")

                    // Retry translation after re-download
                    return translator?.translate(text)?.await()
                } catch (deleteEx: Exception) {
                    Timber.e(deleteEx, "Failed to recover corrupted translation model")
                }
            }

            return null
        }
    }

    /**
     * Suspend until the user confirms or declines downloading a translation model.
     *
     * A continuation resumes reliably from any thread, and cancelling the enclosing scope
     * (e.g. activity destroyed) cancels it cleanly instead of leaking a poll loop.
     *
     * @return true if the user confirmed the download, false if declined/cancelled.
     */
    private suspend fun awaitModelDownloadConfirmation(targetLang: String): Boolean =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val languageName = TranslationTextUtils.getLanguageName(targetLang)
            callback.showModelDownloadPrompt(
                languageName,
                onConfirm = { if (cont.isActive) cont.resume(true) },
                onCancel = { if (cont.isActive) cont.resume(false) }
            )
        }

    override fun release() {
        translator?.close()
        translator = null
    }
}
