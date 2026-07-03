package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import timber.log.Timber

/**
 * Reads runtime capabilities of the active lens into a [CameraRuntimeCapabilities] snapshot.
 *
 * Keeps every Camera2 / CameraInfo read isolated here instead of leaking it into the Activity or
 * the XML layer (S0545 §3.4). All probing is best-effort and defensive: a lens that refuses to
 * report a characteristic degrades to "unsupported" rather than crashing the capture screen.
 */
class CameraCapabilityProbe {

    /**
     * Every camera CameraX exposes to the app, ordered back lenses first (S0753). Lets the lens switch
     * cycle each physical back lens (ultra-wide / main / tele) plus the front, so their 0.5x and
     * long-zoom ranges become reachable instead of being hidden behind one logical back camera.
     */
    fun availableCameras(provider: ProcessCameraProvider): List<CameraInfo> =
        runCatching {
            provider.availableCameraInfos.sortedBy { it.lensFacing != CameraSelector.LENS_FACING_BACK }
        }.getOrElse {
            Timber.w(it, "CameraCapabilityProbe: camera enumeration failed")
            emptyList()
        }

    /** First reported focal length of a lens, in mm; 0 when unavailable. */
    fun focalLengthOf(info: CameraInfo): Float =
        runCatching {
            Camera2CameraInfo.from(info)
                .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.firstOrNull() ?: 0f
        }.getOrElse {
            Timber.w(it, "CameraCapabilityProbe: focal length probe failed")
            0f
        }

    /**
     * Focal length of the device's main back lens, used as the 1x reference for equivalent zoom. The
     * main lens is the back camera that has a flash (ultra-wide / depth lenses usually do not); falls
     * back to the first back camera, else 0 (multipliers then default to 1).
     */
    fun mainBackFocalLength(cameras: List<CameraInfo>): Float {
        val back = cameras.filter { it.lensFacing == CameraSelector.LENS_FACING_BACK }
        val main = back.firstOrNull { it.hasFlashUnit() } ?: back.firstOrNull() ?: return 0f
        return focalLengthOf(main)
    }

    fun probe(
        camera: Camera,
        activeLensFacing: Int,
        availableLensFacings: List<Int>,
        referenceFocal: Float,
    ): CameraRuntimeCapabilities {
        val info = camera.cameraInfo
        val zoom = info.zoomState.value
        val minZoom = zoom?.minZoomRatio ?: CameraRuntimeCapabilities.DEFAULT_ZOOM
        val maxZoom = zoom?.maxZoomRatio ?: CameraRuntimeCapabilities.DEFAULT_ZOOM
        val currentZoom = zoom?.zoomRatio ?: CameraRuntimeCapabilities.DEFAULT_ZOOM
        // linearZoom is the 0..1 position matching the current ratio - the slider's thumb position.
        val currentLinear = zoom?.linearZoom ?: 0f
        val camera2Info = Camera2CameraInfo.from(info)
        // S0753: exposure compensation backs the night-mode fallback when the NIGHT extension is absent.
        val exposureState = info.exposureState
        val exposureSupported = exposureState.isExposureCompensationSupported
        val maxExposureIndex = if (exposureSupported) exposureState.exposureCompensationRange.upper else 0
        // S0753: equivalent-zoom factor so an ultra-wide reads ~0.5x and the main reads 1x; front
        // lenses keep their own native scale (no meaningful comparison to the back reference).
        val thisFocal = focalLengthOf(info)
        val multiplier = if (
            activeLensFacing == CameraSelector.LENS_FACING_BACK && referenceFocal > 0f && thisFocal > 0f
        ) {
            thisFocal / referenceFocal
        } else {
            1f
        }
        // S0753: macro heuristic - a lens that can focus very close (high diopters) gets a macro toggle.
        val minFocusDistance = runCatching {
            Camera2CameraInfo.from(info)
                .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        }.getOrDefault(0f)
        val supportsMacro = minFocusDistance >= MACRO_MIN_DIOPTERS
        val hardwareLevel = runCatching {
            camera2Info.getCameraCharacteristic(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        }.getOrDefault(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY)
        val isoRange = probeCharacteristic<Range<Int>>(camera2Info, CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val shutterRangeNs = probeCharacteristic<Range<Long>>(camera2Info, CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val awbModes = probeCharacteristic<IntArray>(camera2Info, CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
            ?.toList()
            .orEmpty()
        val photoResolutions = probeCharacteristic<StreamConfigurationMap>(
            camera2Info,
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP,
        )?.getOutputSizes(ImageFormat.JPEG)
            ?.toList()
            ?.distinctBy { "${it.width}x${it.height}" }
            ?.sortedByDescending { it.width.toLong() * it.height.toLong() }
            ?.take(MAX_RESOLUTION_OPTIONS)
            .orEmpty()
        val availableAspectRatios = photoResolutions
            .mapNotNull { size -> detectAspectRatio(size) }
            .distinct()
        val supportsManualSensor = hardwareLevel in MANUAL_SENSOR_LEVELS &&
            isoRange != null &&
            shutterRangeNs != null

        return CameraRuntimeCapabilities(
            activeLensFacing = activeLensFacing,
            availableLensFacings = availableLensFacings,
            hasFlashUnit = info.hasFlashUnit(),
            supportsTapToFocus = supportsTapToFocus(info),
            minZoomRatio = minZoom,
            maxZoomRatio = maxZoom,
            currentZoomRatio = currentZoom,
            currentLinearZoom = currentLinear,
            supportsExposureCompensation = exposureSupported,
            maxExposureCompensationIndex = maxExposureIndex,
            supportsManualSensor = supportsManualSensor,
            isoRange = isoRange,
            shutterRangeNs = shutterRangeNs,
            awbModes = awbModes,
            availableAspectRatios = availableAspectRatios.ifEmpty { DEFAULT_ASPECT_RATIOS },
            photoResolutions = photoResolutions,
            zoomMultiplier = multiplier,
            supportsMacro = supportsMacro,
            macroFocusDistance = minFocusDistance,
            maxDisplayZoomRatio = (maxZoom * CameraRuntimeCapabilities.DIGITAL_ZOOM_CAP)
                .coerceAtMost(CameraRuntimeCapabilities.MAX_DISPLAY_ZOOM / multiplier),
            zoomPresets = CameraRuntimeCapabilities.buildZoomPresets(
                minZoom,
                maxZoom,
                multiplier,
                CameraRuntimeCapabilities.DIGITAL_ZOOM_CAP,
            ),
        )
    }

    private fun detectAspectRatio(size: Size): Int? {
        val ratio = size.width.toFloat() / size.height.toFloat()
        return when {
            kotlin.math.abs(ratio - (4f / 3f)) < ASPECT_RATIO_EPSILON -> AspectRatio.RATIO_4_3
            kotlin.math.abs(ratio - (16f / 9f)) < ASPECT_RATIO_EPSILON -> AspectRatio.RATIO_16_9
            else -> null
        }
    }

    private fun <T> probeCharacteristic(
        info: androidx.camera.camera2.interop.Camera2CameraInfo,
        key: CameraCharacteristics.Key<T>,
    ): T? = runCatching { info.getCameraCharacteristic(key) }
        .onFailure { Timber.w(it, "CameraCapabilityProbe: characteristic probe failed key=%s", key.name) }
        .getOrNull()

    private companion object {
        // Min focus distance (diopters) for the macro heuristic: 10 dpt ~ focuses within ~10 cm.
        const val MACRO_MIN_DIOPTERS = 10f
        const val ASPECT_RATIO_EPSILON = 0.02f
        const val MAX_RESOLUTION_OPTIONS = 6
        val DEFAULT_ASPECT_RATIOS = listOf(AspectRatio.RATIO_4_3, AspectRatio.RATIO_16_9)
        val MANUAL_SENSOR_LEVELS = setOf(
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3,
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL,
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
