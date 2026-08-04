package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import com.sza.fastmediasorter.ui.cameracapture.model.CameraLensEntry
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
     * S1189: the widest equivalent zoom the device can actually reach, taken over every offered back
     * lens rather than the bound one. S1261: reachable floors are `minZoomRatio * equivalentMultiplier`
     * - the per-entry multiplier the enumeration computed (parent-floor / FOV aware), not a raw focal
     * ratio. Front lenses keep their native scale and are excluded.
     */
    fun minEquivalentZoom(lenses: List<CameraLensEntry>): Float {
        val reachable = lenses
            .filter { it.lensFacing == CameraSelector.LENS_FACING_BACK }
            .map { it.minZoomRatio * it.equivalentMultiplier }
        return reachable.minOrNull() ?: CameraRuntimeCapabilities.DEFAULT_ZOOM
    }

    /**
     * S1189: the lens of [facing] that focuses closest, when it clears the macro threshold. Hardware
     * macro is normally a dedicated lens, so a device can offer real macro while the bound lens
     * reports nothing usable.
     */
    fun macroLensFor(lenses: List<CameraLensEntry>, facing: Int): CameraLensEntry? =
        lenses.filter { it.lensFacing == facing }
            .maxByOrNull { it.minFocusDistanceDiopters }
            ?.takeIf { it.minFocusDistanceDiopters >= MACRO_MIN_DIOPTERS }

    fun probe(
        camera: Camera,
        activeLens: CameraLensEntry,
        availableLensFacings: List<Int>,
    ): CameraRuntimeCapabilities {
        val activeLensFacing = activeLens.lensFacing
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
        // S1261: the equivalent-zoom factor now travels on the lens entry (parent-floor / FOV aware)
        // instead of being re-derived from raw focal lengths here; front lenses carry 1 already.
        val multiplier = activeLens.equivalentMultiplier
        // S0753: macro heuristic - a lens that can focus very close (high diopters) gets a macro toggle.
        val minFocusDistance = runCatching {
            Camera2CameraInfo.from(info)
                .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        }.getOrDefault(0f)
        val supportsMacro = minFocusDistance >= MACRO_MIN_DIOPTERS
        val hardwareLevel = runCatching {
            camera2Info.getCameraCharacteristic(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        }.getOrDefault(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY)
        val isoRange = probeCharacteristic<Range<Int>>(
            camera2Info,
            CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE,
        )
        val shutterRangeNs = probeCharacteristic<Range<Long>>(
            camera2Info,
            CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE,
        )
        val awbModes = probeCharacteristic<IntArray>(camera2Info, CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
            ?.toList()
            .orEmpty()
        val photoResolutions = photoResolutionsOf(camera2Info)
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
            highResolutionPhotoSizes = highResolutionPhotoSizes(camera2Info),
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

    /**
     * JPEG output sizes the lens offers, largest first and capped so the picker stays readable.
     *
     * S1189: merges the high-resolution set, where a large sensor declares its full frame - the
     * ordinary set stops at sizes the device can stream at full capture rate, which is why the
     * picker used to top out well below the advertised megapixels. The cap is safe because the list
     * is sorted descending, so the sensor maximum is always the entry that survives it.
     */
    private fun photoResolutionsOf(camera2Info: Camera2CameraInfo): List<Size> {
        val configs = probeCharacteristic<StreamConfigurationMap>(
            camera2Info,
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP,
        )
        val standard = configs?.getOutputSizes(ImageFormat.JPEG)?.toList().orEmpty()
        return (standard + highResolutionSizesOf(configs))
            .distinctBy { "${it.width}x${it.height}" }
            .sortedByDescending { it.width.toLong() * it.height.toLong() }
            .take(MAX_RESOLUTION_OPTIONS)
    }

    /** S1189: the high-resolution JPEG set; available since API 23, so it carries no version gate. */
    fun highResolutionSizesOf(configs: StreamConfigurationMap?): List<Size> =
        runCatching { configs?.getHighResolutionOutputSizes(ImageFormat.JPEG)?.toList() }
            .getOrNull()
            .orEmpty()

    /** S1189: the high-resolution subset of what this lens offers, for the opt-in full-frame path. */
    fun highResolutionPhotoSizes(camera2Info: Camera2CameraInfo): List<Size> =
        highResolutionSizesOf(
            probeCharacteristic<StreamConfigurationMap>(
                camera2Info,
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP,
            ),
        )

    private fun detectAspectRatio(size: Size): Int? {
        val ratio = size.width.toFloat() / size.height.toFloat()
        return when {
            kotlin.math.abs(ratio - FOUR_THREE) < ASPECT_RATIO_EPSILON -> AspectRatio.RATIO_4_3
            kotlin.math.abs(ratio - SIXTEEN_NINE) < ASPECT_RATIO_EPSILON -> AspectRatio.RATIO_16_9
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
        const val FOUR_THREE = 4f / 3f
        const val SIXTEEN_NINE = 16f / 9f
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
