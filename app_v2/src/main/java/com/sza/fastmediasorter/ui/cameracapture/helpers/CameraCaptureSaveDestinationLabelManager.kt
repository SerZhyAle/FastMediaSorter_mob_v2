package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.content.Intent
import android.view.View
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.cameracapture.CameraCaptureContract
import com.sza.fastmediasorter.ui.cameracapture.OutlinedTextView
import com.sza.fastmediasorter.util.CaptureDestinationPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * S0844: resolves and renders the save-destination and calling-scenario labels overlaid on
 * [com.sza.fastmediasorter.ui.cameracapture.CameraCaptureActivity]'s preview. Extracted from the
 * Activity to shed its detekt `LargeClass`/`TooManyFunctions` findings.
 */
class CameraCaptureSaveDestinationLabelManager(
    private val intent: Intent,
    private val flowManager: CameraCaptureFlowManager,
    private val settingsRepository: SettingsRepository,
    private val resourceRepository: ResourceRepository,
    private val rotationManager: CameraOverlayRotationManager,
    private val destinationLabel: OutlinedTextView,
    private val scenarioLabel: OutlinedTextView,
    private val lifecycleScope: CoroutineScope,
) {

    fun refresh() {
        lifecycleScope.launch {
            val destinationName = resolveSaveDestinationName()
            if (destinationName.isNullOrBlank()) {
                destinationLabel.visibility = View.GONE
            } else {
                destinationLabel.text = destinationName
                destinationLabel.visibility = View.VISIBLE
                rotationManager.reapply()
            }
        }
    }

    fun renderScenario() {
        val scenario = CameraCaptureContract.readScenario(intent)
        if (scenario.labelRes == 0) {
            scenarioLabel.visibility = View.GONE
        } else {
            scenarioLabel.text = scenarioLabel.context.getString(scenario.labelRes)
            scenarioLabel.visibility = View.VISIBLE
            rotationManager.reapply()
        }
    }

    private suspend fun resolveSaveDestinationName(): String? {
        // S0754: a caller that already knows the real save target (Browse, quick-capture widget)
        // passes it explicitly - the scratch output file's parent folder name is not the destination
        // (e.g. an app-private "Pictures" staging dir while the shot actually lands in "Downloads").
        CameraCaptureContract.readDestinationLabel(intent)?.let {
            return it
        }
        val fixedOutputParent = flowManager.currentOutputFile()?.parentFile?.name
        if (!flowManager.multiCapture) return fixedOutputParent
        val settings = settingsRepository.getSettings().first()
        val configuredId = if (flowManager.isVideoMode) {
            settings.videoRecordingDestinationResourceId
        } else {
            settings.cameraPhotosDestinationResourceId
        }
        val configuredName = configuredId?.toLongOrNull()?.let { id ->
            withContext(Dispatchers.IO) { resourceRepository.getResourceById(id)?.name }
        }
        return configuredName?.takeUnless { it.isBlank() } ?: if (flowManager.isVideoMode) {
            CaptureDestinationPolicy.resolveVideoDestination(null).name
        } else {
            CaptureDestinationPolicy.resolveCameraDestination(null).name
        }
    }
}
