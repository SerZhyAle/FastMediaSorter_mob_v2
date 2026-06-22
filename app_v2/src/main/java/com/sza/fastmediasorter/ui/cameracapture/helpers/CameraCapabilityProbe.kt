package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import timber.log.Timber
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities

/**
 * Reads runtime capabilities of the active lens into a [CameraRuntimeCapabilities] snapshot.
 *
 * Keeps every Camera2 / CameraInfo read isolated here instead of leaking it into the Activity or
 * the XML layer (S0545 §3.4). All probing is best-effort and defensive: a lens that refuses to
 * report a characteristic degrades to "unsupported" rather than crashing the capture screen.
 */
class CameraCapabilityProbe {

    /** Which facings the device actually exposes, so lens-switch is offered only when real. */
    fun availableLensFacings(provider: ProcessCameraProvider): List<Int> = buildList {
        runCatching {
            if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) add(CameraSelector.LENS_FACING_BACK)
            if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) add(CameraSelector.LENS_FACING_FRONT)
        }.onFailure { Timber.w(it, "CameraCapabilityProbe: lens enumeration failed") }
    }

    fun probe(
        camera: Camera,
        activeLensFacing: Int,
        availableLensFacings: List<Int>,
    ): CameraRuntimeCapabilities {
        val info = camera.cameraInfo
        val zoom = info.zoomState.value
        val minZoom = zoom?.minZoomRatio ?: CameraRuntimeCapabilities.DEFAULT_ZOOM
        val maxZoom = zoom?.maxZoomRatio ?: CameraRuntimeCapabilities.DEFAULT_ZOOM
        val currentZoom = zoom?.zoomRatio ?: CameraRuntimeCapabilities.DEFAULT_ZOOM

        return CameraRuntimeCapabilities(
            activeLensFacing = activeLensFacing,
            availableLensFacings = availableLensFacings,
            hasFlashUnit = info.hasFlashUnit(),
            supportsTapToFocus = supportsTapToFocus(info),
            minZoomRatio = minZoom,
            maxZoomRatio = maxZoom,
            currentZoomRatio = currentZoom,
            zoomPresets = CameraRuntimeCapabilities.buildZoomPresets(minZoom, maxZoom),
        )
    }

    private fun supportsTapToFocus(info: androidx.camera.core.CameraInfo): Boolean =
        runCatching {
            val afModes = Camera2CameraInfo.from(info)
                .getCameraCharacteristic(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
            // A fixed-focus lens (common on front cameras) reports only OFF: tap-to-focus is a no-op there.
            afModes?.any { it != CameraMetadata.CONTROL_AF_MODE_OFF } ?: false
        }.getOrElse {
            Timber.w(it, "CameraCapabilityProbe: AF mode probe failed")
            false
        }
}
