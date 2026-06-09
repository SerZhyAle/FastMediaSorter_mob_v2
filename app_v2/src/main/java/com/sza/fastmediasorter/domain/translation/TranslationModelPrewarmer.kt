package com.sza.fastmediasorter.domain.translation

import com.sza.fastmediasorter.domain.model.TranslationModelPrewarmStatus
import kotlinx.coroutines.flow.StateFlow

interface TranslationModelPrewarmer {
    val status: StateFlow<TranslationModelPrewarmStatus>
    suspend fun prewarm(targetSettingsCode: String)
}
