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
        return withEquivalents(entries.sortedWith(lensOrder))
    }

    /**
     * S1261 (defect D1): the entry a fresh session should bind - the MAIN back lens, the one whose
     * equivalent multiplier is closest to 1 (tie-breaks: has flash, then logical over physical).
     * The list is sorted widest-first, so "first back" used to open the screen on the ultra-wide
     * entry (own floor 1.0) and the sub-1x pill vanished. Single-back devices are unaffected.
     */
    fun initialLensIndex(entries: List<CameraLensEntry>): Int {
        val backIndices = entries.indices
            .filter { entries[it].lensFacing == CameraSelector.LENS_FACING_BACK }
        return backIndices.minWithOrNull(
            compareBy(
                { abs(entries[it].equivalentMultiplier - CameraRuntimeCapabilities.DEFAULT_ZOOM) },
                { !hasFlash(entries[it]) },
                { entries[it].isPhysicalSubLens },
            ),
        ) ?: 0
    }

    private fun hasFlash(entry: CameraLensEntry): Boolean =
        runCatching { entry.cameraInfo.hasFlashUnit() }.getOrDefault(false)

    /**
     * S1261: fills [CameraLensEntry.equivalentMultiplier] once the whole set is known - the
     * reference (main back) lens only exists relative to its siblings. No back lens leaves every
     * multiplier at its neutral default.
     */
    private fun withEquivalents(entries: List<CameraLensEntry>): List<CameraLensEntry> {
        val reference = entries
            .filter { it.lensFacing == CameraSelector.LENS_FACING_BACK && !it.isPhysicalSubLens }
            .let { logicals ->
                logicals.firstOrNull(::hasFlash) ?: logicals.firstOrNull()
            } ?: return entries
        val ref = LensEquivalentCalculator.Reference(
            focalMm = reference.focalLengthMm,
            sensorWidthMm = reference.sensorWidthMm,
        )
        return entries.map { entry ->
            entry.copy(
                equivalentMultiplier = LensEquivalentCalculator.multiplier(
                    LensEquivalentCalculator.Lens(
                        isBack = entry.lensFacing == CameraSelector.LENS_FACING_BACK,
                        focalMm = entry.focalLengthMm,
                        sensorWidthMm = entry.sensorWidthMm,
                        parentLogicalMinZoom = entry.parentLogicalMinZoom,
                        isWidestInParent = isWidestInParent(entry, entries),
                    ),
                    ref,
                ),
            )
        }
    }

    /** The widest (shortest-focal) back sub-lens among its logical parent's sub-lenses. */
    private fun isWidestInParent(entry: CameraLensEntry, entries: List<CameraLensEntry>): Boolean {
        val qualifies = entry.isPhysicalSubLens &&
            entry.focalLengthMm > 0f &&
            entry.lensFacing == CameraSelector.LENS_FACING_BACK
        return qualifies && entries.none {
            it.isPhysicalSubLens &&
                it.logicalCameraId == entry.logicalCameraId &&
                it.focalLengthMm > 0f &&
                it.focalLengthMm < entry.focalLengthMm
        }
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
            // S1261: same focal OR same BACK equivalent means the same physics. Focal alone must
            // stay sufficient: the standalone ultra-wide and the fused camera's sub-lens are one
            // lens, but their multipliers diverge when the sensor size is unreadable (focal-ratio
            // fallback vs the parent-floor cross-check) - equivalent-only dedup would offer both.
            // Front lenses all carry the neutral multiplier, so the equivalent leg would collapse
            // every front sub-lens into one - they keep the focal-only rule (emulator evidence:
            // the 5.84 mm front sub-lens vanished until this gate).
            val covered = kept.any {
                val sameEquivalent = entry.lensFacing == CameraSelector.LENS_FACING_BACK &&
                    sameMagnification(it.equivalentMultiplier, entry.equivalentMultiplier)
                it.lensFacing == entry.lensFacing &&
                    (sameMagnification(it.focalLengthMm, entry.focalLengthMm) || sameEquivalent)
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
        sensorWidthMm = characteristic(lensInfo, CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            ?.width ?: 0f,
        // The bind camera's floor: the parent's for a sub-lens, the entry's own for a logical one.
        // The Camera2 range is the fallback because an unbound camera's zoomState can be empty on
        // some firmwares - and this floor (0.57 on the S25 FE) is the whole point of the ticket.
        parentLogicalMinZoom = runCatching { bindInfo.zoomState.value?.minZoomRatio }.getOrNull()
            ?: zoomFloorOf(bindInfo) ?: 0f,
    )

    /** S1261: the logical camera's declared zoom-ratio floor straight from Camera2 (API 30+). */
    private fun zoomFloorOf(info: CameraInfo): Float? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return characteristic(info, CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.lower
    }

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
