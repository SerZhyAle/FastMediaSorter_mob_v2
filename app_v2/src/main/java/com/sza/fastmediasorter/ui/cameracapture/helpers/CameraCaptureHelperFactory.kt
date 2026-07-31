package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.content.Intent
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.google.android.material.imageview.ShapeableImageView
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.SaveCapturedMediaUseCase
import com.sza.fastmediasorter.ui.common.widget.OutlinedTextView
import com.sza.fastmediasorter.ui.share.SendToMenuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * S1195: owns the camera host's domain dependencies - the settings it reads and writes, the capture
 * saver and the destination lookup - so `CameraCaptureActivity` injects no repository or use case of
 * its own (CLAUDE.md Rule 3). The lifecycle-bound helpers it builds still receive their views and
 * scope from the host, which Hilt cannot supply.
 */
class CameraCaptureHelperFactory @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val resourceRepository: ResourceRepository,
    private val saveCapturedMedia: SaveCapturedMediaUseCase,
) {

    /** Current settings snapshot; the host reads geotag opt-in and the remembered aspect ratio from it. */
    suspend fun currentSettings(): AppSettings = settingsRepository.getSettings().first()

    /** S1066: remember the aspect ratio the settings dialog just applied. */
    suspend fun rememberAspectRatio(ratio: Int) {
        settingsRepository.updateSettings { it.copy(cameraAspectRatio = ratio) }
    }

    @Suppress("LongParameterList") // Mirrors the manager's host-supplied surface one-to-one.
    fun createResultManager(
        activity: FragmentActivity,
        lifecycleScope: CoroutineScope,
        sessionManager: CameraCaptureSessionManager,
        sendToMenuManager: SendToMenuManager,
        galleryThumbnail: ShapeableImageView,
        sendToButton: View,
        onError: (Int) -> Unit,
    ): CameraCaptureResultManager = CameraCaptureResultManager(
        activity = activity,
        lifecycleScope = lifecycleScope,
        sessionManager = sessionManager,
        settingsRepository = settingsRepository,
        saveCapturedMedia = saveCapturedMedia,
        sendToMenuManager = sendToMenuManager,
        galleryThumbnail = galleryThumbnail,
        sendToButton = sendToButton,
        onError = onError,
    )

    @Suppress("LongParameterList") // Mirrors the manager's host-supplied surface one-to-one.
    fun createSaveDestinationLabelManager(
        intent: Intent,
        flowManager: CameraCaptureFlowManager,
        rotationManager: CameraOverlayRotationManager,
        destinationLabel: OutlinedTextView,
        scenarioLabel: OutlinedTextView,
        lifecycleScope: CoroutineScope,
    ): CameraCaptureSaveDestinationLabelManager = CameraCaptureSaveDestinationLabelManager(
        intent = intent,
        flowManager = flowManager,
        settingsRepository = settingsRepository,
        resourceRepository = resourceRepository,
        rotationManager = rotationManager,
        destinationLabel = destinationLabel,
        scenarioLabel = scenarioLabel,
        lifecycleScope = lifecycleScope,
    )
}
