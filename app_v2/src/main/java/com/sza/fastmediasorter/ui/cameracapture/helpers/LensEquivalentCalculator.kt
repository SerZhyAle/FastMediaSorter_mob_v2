package com.sza.fastmediasorter.ui.cameracapture.helpers

import kotlin.math.abs
import kotlin.math.ln

/**
 * Computes a lens's equivalent-zoom multiplier (native ratio -> system-camera-style equivalent)
 * from static lens facts. Pure math, no Android imports, so the rule is unit-testable with the
 * numbers a device report supplies (S1261 research 01).
 *
 * Why raw focal ratios are not enough (defect D2): focal lengths on different sensor sizes are not
 * comparable - the S25 FE ultra-wide (1.74 mm) against the main (5.40 mm) yields 0.32 while the
 * true equivalent floor is 0.57, and the tele (7.00 mm) yields 1.30 while the system camera calls
 * it ~3x. Field-of-view normalization by sensor width fixes both; the parent logical camera's
 * sub-1x floor is authoritative for the widest lens inside it (the platform computed it already).
 */
object LensEquivalentCalculator {

    /** The lens the equivalents are expressed against - the device's main back camera. */
    data class Reference(
        val focalMm: Float,
        val sensorWidthMm: Float = UNKNOWN_MM,
    )

    /** Static facts of the lens whose multiplier is being computed. */
    data class Lens(
        val isBack: Boolean,
        val focalMm: Float,
        val sensorWidthMm: Float = UNKNOWN_MM,
        /** Floor of the logical camera this lens belongs to; own floor for a logical entry. */
        val parentLogicalMinZoom: Float = 0f,
        /** The lens is the widest back sub-lens inside its logical parent. */
        val isWidestInParent: Boolean = false,
    )

    /**
     * Priority order: parent-floor cross-check for the widest sub-lens of a sub-1x logical camera
     * (exact - the platform already solved the optics), then FOV normalization when both sensor
     * widths are known, then the raw focal ratio as the last resort. Front lenses and lenses with
     * no usable focal data stay at 1 (their native scale, matching today's behavior).
     */
    fun multiplier(lens: Lens, reference: Reference): Float {
        val parentFloor = lens.parentLogicalMinZoom
        val fovRatio = if (lens.sensorWidthMm > 0f && reference.sensorWidthMm > 0f) {
            (lens.focalMm / lens.sensorWidthMm) / (reference.focalMm / reference.sensorWidthMm)
        } else {
            0f
        }
        return when {
            !lens.isBack || lens.focalMm <= 0f || reference.focalMm <= 0f -> NEUTRAL
            lens.isWidestInParent && parentFloor > 0f && parentFloor < NEUTRAL -> parentFloor
            fovRatio > 0f -> fovRatio
            else -> lens.focalMm / reference.focalMm
        }
    }

    /**
     * S4688: 35mm-equivalent focal length from the lens focal and its sensor width (full frame is 36 mm
     * wide), so lenses on different sensors compare; [UNKNOWN_MM] when either fact is missing.
     */
    fun equivalentFocalMm(focalMm: Float, sensorWidthMm: Float): Float =
        if (focalMm > 0f && sensorWidthMm > 0f) focalMm * FULL_FRAME_WIDTH_MM / sensorWidthMm else UNKNOWN_MM

    /**
     * S4688: index of the main back camera among [backLogicals], given as (zoom floor, has flash,
     * 35mm-equivalent focal) triples in the platform's own camera order; -1 for an empty list.
     *
     * Rules, first match wins, each one independent of how an OEM orders or flags its cameras:
     * - a floor below 1 - only the fused main camera can zoom out past its own optics;
     * - the equivalent focal nearest the normal lens, compared on a log scale because magnifications
     *   multiply - a main lens stays the pick through a misreported sensor size as long as it lands
     *   nearer the normal lens than its neighbours (the S25 FE reports a width that puts its main at 34 mm);
     * - the first camera with a flash, then the first one at all - the platform's own primary.
     *
     * A Galaxy S25+ lists its standalone ultra-wide as a logical camera with a flash; elected by flash
     * in widest-first order it became the 1x reference, every session started on it and the real main
     * lens was labelled tele.
     */
    fun referenceIndex(backLogicals: List<Triple<Float, Boolean, Float>>): Int {
        val fused = backLogicals.indexOfFirst { (floor, _, _) -> floor > 0f && floor < NEUTRAL }.takeIf { it >= 0 }
        val normal = backLogicals.indices
            .filter { backLogicals[it].third > 0f }
            .minByOrNull { abs(ln(backLogicals[it].third / NORMAL_EQUIVALENT_FOCAL_MM)) }
        val flashed = backLogicals.indexOfFirst { (_, hasFlash, _) -> hasFlash }.takeIf { it >= 0 }
        return fused ?: normal ?: flashed ?: backLogicals.indices.firstOrNull() ?: -1
    }

    /** Sensor width is unknown - the FOV path is unavailable for this lens. */
    const val UNKNOWN_MM = 0f

    private const val NEUTRAL = 1f
    private const val FULL_FRAME_WIDTH_MM = 36f

    /** A phone main camera sits at 23-28 mm equivalent; ultra-wides near 13, teles from 50. */
    private const val NORMAL_EQUIVALENT_FOCAL_MM = 26f
}
