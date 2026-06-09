package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.domain.model.TranslationModelPrewarmStatus
import com.sza.fastmediasorter.domain.translation.TranslationModelPrewarmer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

class NoOpTextTranslationFacade : TextTranslationFacade {
    override suspend fun getTargetLanguageCode(): String? = null
    override suspend fun detectLanguage(text: String): String = "en"
    override suspend fun translate(text: String, sourceLang: String, targetLang: String): String? = null
    override fun release() {}
}

@Singleton
class NoOpTextTranslationFacadeFactory @Inject constructor() : TextTranslationFacadeFactory {
    override fun create(callback: TranslationManager.TranslationCallback): TextTranslationFacade {
        return NoOpTextTranslationFacade()
    }
}

@Singleton
class NoOpTranslationModelPrewarmer @Inject constructor() : TranslationModelPrewarmer {
    private val _status = MutableStateFlow<TranslationModelPrewarmStatus>(TranslationModelPrewarmStatus.Idle)
    override val status: StateFlow<TranslationModelPrewarmStatus> = _status.asStateFlow()
    override suspend fun prewarm(targetSettingsCode: String) {}
}
