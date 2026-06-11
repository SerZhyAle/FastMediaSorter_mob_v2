package com.sza.fastmediasorter.domain.usecase

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.sza.fastmediasorter.domain.model.TranslationModelPrewarmStatus
import com.sza.fastmediasorter.domain.translation.TranslationLanguageCodeMapper
import com.sza.fastmediasorter.domain.translation.TranslationModelPrewarmer
import com.sza.fastmediasorter.domain.translation.TranslationModelPrewarmEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ensures the ML Kit translation model(s) for a target language are present on device ahead of
 * time, so the OCR / translation hot path does not trigger the in-line download dialog (S0334).
 *
 * Required models for a target language X: English is always the pivot (ML Kit translates only
 * to/from English), so the set is `{en}` when X is English, otherwise `{en, X}`. Already-downloaded
 * models are skipped, making repeated calls for the same language idempotent. Downloads run with no
 * Wi-Fi-only restriction, consistent with the hot-path download behaviour.
 */
@Singleton
class PrewarmTranslationModelUseCase @Inject constructor(
    @TranslationModelPrewarmEnabled private val enabledMarkers: Set<@JvmSuppressWildcards String>
) : TranslationModelPrewarmer {

    // Touch ML Kit lazily on first prewarm(), never at construction. On store flavors the
    // MlKitInitProvider ships in the on-demand :translate_feature module and is removed from the
    // base manifest (S0386), so RemoteModelManager.getInstance() throws "MlKitContext has not been
    // initialized" until the engine is installed. This @Singleton is built eagerly with
    // SettingsViewModel, so an eager getInstance() here crashed Settings on open.
    private val modelManager: RemoteModelManager by lazy { RemoteModelManager.getInstance() }

    private val _status = MutableStateFlow<TranslationModelPrewarmStatus>(TranslationModelPrewarmStatus.Idle)
    override val status: StateFlow<TranslationModelPrewarmStatus> = _status.asStateFlow()

    override suspend fun prewarm(targetSettingsCode: String) {
        Timber.d("S0386: prewarm entered (lazy ML Kit init), target=$targetSettingsCode")
        if (enabledMarkers.isEmpty()) {
            _status.value = TranslationModelPrewarmStatus.Idle
            return
        }
        val targetMlKit = TranslationLanguageCodeMapper.languageCodeToMLKit(targetSettingsCode)
        val required = requiredModels(targetMlKit)
        try {
            val missing = withContext(Dispatchers.IO) {
                required.filterNot { code ->
                    modelManager.isModelDownloaded(TranslateRemoteModel.Builder(code).build()).await()
                }
            }
            if (missing.isEmpty()) {
                _status.value = TranslationModelPrewarmStatus.Ready(targetSettingsCode)
                return
            }
            _status.value = TranslationModelPrewarmStatus.Downloading(targetSettingsCode)
            withContext(Dispatchers.IO) {
                for (code in missing) {
                    modelManager.download(
                        TranslateRemoteModel.Builder(code).build(),
                        DownloadConditions.Builder().build()
                    ).await()
                }
            }
            _status.value = TranslationModelPrewarmStatus.Ready(targetSettingsCode)
        } catch (e: Exception) {
            Timber.w(e, "Translation model prewarm failed for $targetSettingsCode")
            _status.value = TranslationModelPrewarmStatus.Failed(targetSettingsCode)
        }
    }

    private fun requiredModels(targetMlKit: String): Set<String> =
        if (targetMlKit == TranslateLanguage.ENGLISH) {
            setOf(TranslateLanguage.ENGLISH)
        } else {
            setOf(TranslateLanguage.ENGLISH, targetMlKit)
        }
}
