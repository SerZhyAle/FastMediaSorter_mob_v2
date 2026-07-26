package com.sza.fastmediasorter.data.capture

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.util.Range
import android.util.Size
import com.sza.fastmediasorter.domain.model.CameraHardwareEntry
import com.sza.fastmediasorter.domain.model.CameraHardwareInventory
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Reads the device's full camera tree straight from Camera2 for the System info report.
 *
 * Deliberately independent of the capture screen's own probing: the report has to stay readable on a
 * device where binding a camera fails, which is exactly the device worth diagnosing. Every read is
 * guarded individually so one refused characteristic costs that field, not the whole inventory.
 */
class CameraHardwareDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun read(): CameraHardwareInventory {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return CameraHardwareInventory()
        val ids = runCatching { manager.cameraIdList }.getOrElse {
            Timber.w(it, "CameraHardware: camera id list unavailable")
            emptyArray()
        }
        val entries = mutableListOf<CameraHardwareEntry>()
        ids.forEach { id ->
            val logical = characteristicsOf(manager, id) ?: return@forEach
            entries += entryOf(id, null, logical)
            physicalIdsOf(logical).forEach { physicalId ->
                characteristicsOf(manager, physicalId)?.let { physical ->
                    entries += entryOf(physicalId, id, physical)
                }
            }
        }
        Timber.d("S1189: camera inventory entries=%d", entries.size)
        return CameraHardwareInventory(entries)
    }

    private fun characteristicsOf(manager: CameraManager, id: String): CameraCharacteristics? =
        runCatching { manager.getCameraCharacteristics(id) }.getOrElse {
            Timber.w(it, "CameraHardware: characteristics unavailable for camera %s", id)
            null
        }

    private fun physicalIdsOf(characteristics: CameraCharacteristics): Set<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptySet()
        return runCatching { characteristics.physicalCameraIds }.getOrElse {
            Timber.w(it, "CameraHardware: physical camera ids unavailable")
            emptySet()
        }
    }

    private fun entryOf(
        id: String,
        parentId: String?,
        characteristics: CameraCharacteristics,
    ): CameraHardwareEntry {
        val configs = read(characteristics, CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        return CameraHardwareEntry(
            cameraId = id,
            logicalParentId = parentId,
            lensFacing = read(characteristics, CameraCharacteristics.LENS_FACING),
            focalLengthsMm = read(characteristics, CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.toList()
                .orEmpty(),
            zoomRange = zoomRangeOf(characteristics),
            minFocusDistanceDiopters = read(
                characteristics,
                CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE,
            ),
            focusDistanceCalibration = read(
                characteristics,
                CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION,
            ),
            hardwareLevel = read(characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL),
            maxJpegSize = largest(configs?.getOutputSizes(ImageFormat.JPEG)),
            maxHighResolutionJpegSize = largest(highResolutionSizes(configs)),
        )
    }

    /**
     * The zoom range as the device states it. Below API 30 there is no ratio range at all, so the
     * legacy crop-region maximum is reported as a range starting at 1 - which is itself the finding
     * that explains a camera stuck at a 1x floor.
     */
    private fun zoomRangeOf(characteristics: CameraCharacteristics): Range<Float>? {
        val ratioRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            read(characteristics, CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        } else {
            null
        }
        if (ratioRange != null) return ratioRange
        return read(characteristics, CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
            ?.let { Range(NO_ZOOM, it) }
    }

    /**
     * Where a large sensor declares its full frame - the ordinary output set stops at the sizes the
     * device can stream at full rate. Available since API 23, so it is read on every supported level
     * rather than gated behind the newer ultra-high-resolution capability.
     */
    private fun highResolutionSizes(configs: StreamConfigurationMap?): Array<Size>? =
        runCatching { configs?.getHighResolutionOutputSizes(ImageFormat.JPEG) }.getOrElse {
            Timber.w(it, "CameraHardware: high-resolution output sizes unavailable")
            null
        }

    private fun largest(sizes: Array<Size>?): Size? =
        sizes?.maxByOrNull { it.width.toLong() * it.height.toLong() }

    private fun <T> read(characteristics: CameraCharacteristics, key: CameraCharacteristics.Key<T>): T? =
        runCatching { characteristics.get(key) }.getOrElse {
            Timber.w(it, "CameraHardware: characteristic %s unavailable", key.name)
            null
        }

    private companion object {
        const val NO_ZOOM = 1f
    }
}
