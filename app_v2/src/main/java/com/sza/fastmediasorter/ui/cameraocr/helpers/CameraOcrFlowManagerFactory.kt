package com.sza.fastmediasorter.ui.cameraocr.helpers

import android.content.Context
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.player.helpers.TranslationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * S1329: owns the settings dependency that [CameraOcrFlowManager] and [TranslationManager] both need,
 * so the camera-OCR host injects no repository of its own (CLAUDE.md Rule 3). The two managers are
 * built together because the flow manager takes the translation manager as a collaborator, and both
 * were constructed side by side in the host's `onCreate` for exactly that reason.
 */
class CameraOcrFlowManagerFactory @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {

    fun create(
        context: Context,
        scope: CoroutineScope,
        storageManager: CameraOcrStorageManager,
        translationCallback: TranslationManager.TranslationCallback,
        callback: CameraOcrFlowManager.Callback,
    ): CameraOcrFlowManager = CameraOcrFlowManager(
        scope = scope,
        settingsRepository = settingsRepository,
        storageManager = storageManager,
        translationManager = TranslationManager(
            context = context,
            callback = translationCallback,
            settingsRepository = settingsRepository,
        ),
        callback = callback,
    )

    /** One-shot snapshot for the compact settings dialog, which reads settings once rather than observing. */
    suspend fun currentSettings(): AppSettings = settingsRepository.getSettings().first()
}
