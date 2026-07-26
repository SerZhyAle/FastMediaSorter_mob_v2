package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import com.sza.fastmediasorter.ui.cameracapture.model.CameraLensEntry
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.max

/**
 * Turns the platform's list of logical cameras into the set of lenses the capture screen offers.
 *
 * The default list stops at logical cameras, which is why a multi-lens phone appears to have exactly
 * one back camera: its ultra-wide and macro optics are physical sub-lenses of that one entry. This
 * manager expands them ([expand]) and then decides which are worth showing ([select]).
 */
class CameraLensEnumerationManager {

    /** Every lens the platform admits to, logical cameras plus their physical sub-lenses. */
    fun expand(provider: ProcessCameraProvider): List<CameraLensEntry> {
        val logicalCameras = runCatching { provider.availableCameraInfos }.getOrElse {
            Timber.w(it, "CameraLensEnumeration: camera enumeration failed")
            return emptyList()
        }
        val entries = mutableListOf<CameraLensEntry>()
        logicalCameras.forEach { logical ->
            val logicalId = cameraIdOf(logical) ?: return@forEach
            entries += entryOf(logical, logicalId, null, logical)
            physicalInfosOf(logical).forEach { physical ->
                cameraIdOf(physical)?.let { physicalId ->
                    entries += entryOf(logical, logicalId, physicalId, physical)
                }
            }
        }
        return entries.sortedWith(lensOrder)
    }

    /**
     * The lenses the switcher offers: every logical camera, plus each physical sub-lens that reaches
     * a focal length no already-kept lens of the same facing covers.
     *
     * Keeping the logical cameras unconditionally is what guarantees a single-lens device ends up
     * with exactly the set it had before this ticket. Swap this one function to return [entries]
     * unchanged to offer every physical lens instead (strategic §5.3, §6 item 2).
     */
    fun select(entries: List<CameraLensEntry>): List<CameraLensEntry> {
        val kept = entries.filterNot { it.isPhysicalSubLens }.toMutableList()
        entries.filter { it.isPhysicalSubLens }.forEach { entry ->
            if (entry.focalLengthMm <= 0f) return@forEach
            val covered = kept.any {
                it.lensFacing == entry.lensFacing && sameMagnification(it.focalLengthMm, entry.focalLengthMm)
            }
            if (!covered) kept += entry.copy(hasOwnMagnification = true)
        }
        return kept.sortedWith(lensOrder)
    }

    private fun physicalInfosOf(info: CameraInfo): List<CameraInfo> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptyList()
        return runCatching { info.physicalCameraInfos.toList() }.getOrElse {
            Timber.w(it, "CameraLensEnumeration: physical lens enumeration failed")
            emptyList()
        }
    }

    /**
     * [bindInfo] is the logical camera the session selects; [lensInfo] is the lens whose
     * characteristics describe the entry - the same object for a logical entry, the sub-lens for a
     * physical one.
     */
    private fun entryOf(
        bindInfo: CameraInfo,
        logicalId: String,
        physicalId: String?,
        lensInfo: CameraInfo,
    ): CameraLensEntry = CameraLensEntry(
        cameraInfo = bindInfo,
        logicalCameraId = logicalId,
        physicalCameraId = physicalId,
        lensFacing = runCatching { lensInfo.lensFacing }.getOrDefault(CameraSelector.LENS_FACING_BACK),
        focalLengthMm = characteristic(lensInfo, CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull() ?: 0f,
        // A physical sub-lens exposes no zoom state of its own, so it falls back to the plain ratio.
        minZoomRatio = runCatching { lensInfo.zoomState.value?.minZoomRatio }.getOrNull()
            ?: CameraRuntimeCapabilities.DEFAULT_ZOOM,
        minFocusDistanceDiopters = characteristic(
            lensInfo,
            CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE,
        ) ?: 0f,
    )

    private fun cameraIdOf(info: CameraInfo): String? =
        runCatching { Camera2CameraInfo.from(info).cameraId }.getOrElse {
            Timber.w(it, "CameraLensEnumeration: camera id unavailable")
            null
        }

    private fun <T> characteristic(info: CameraInfo, key: CameraCharacteristics.Key<T>): T? =
        runCatching { Camera2CameraInfo.from(info).getCameraCharacteristic(key) }.getOrNull()

    private fun sameMagnification(first: Float, second: Float): Boolean =
        first > 0f && second > 0f && abs(first - second) / max(first, second) < FOCAL_MATCH_TOLERANCE

    private companion object {
        /** Two focal lengths within this relative gap are the same lens as far as the user can tell. */
        private const val FOCAL_MATCH_TOLERANCE = 0.15f

        /** Back lenses first, widest first inside a facing; a lens of unknown focal length goes last. */
        private val lensOrder = compareBy<CameraLensEntry>(
            { it.lensFacing != CameraSelector.LENS_FACING_BACK },
            { if (it.focalLengthMm > 0f) it.focalLengthMm else Float.MAX_VALUE },
        )
    }
}
