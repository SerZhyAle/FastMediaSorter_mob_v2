package com.sza.fastmediasorter.domain.ocr

import kotlin.math.roundToInt

/**
 * Decide the resolution to declare to the recogniser for one image (S1715, S1876).
 *
 * The engine is told a resolution so it can separate neighbouring blocks. Given none it estimates
 * for itself, and the neighbouring project measured that self-estimate at about 70 on a page of
 * roughly 180 DPI - low enough to change the block split. So a value is declared always, even when
 * it is only the floor.
 *
 * Every branch that declares a number computes it; none of them guesses:
 *
 * - [OcrSourceKind.RENDERED_DOCUMENT_PAGE] - the bitmap width in pixels over the page width in
 *   points (72 per inch) is the exact rendered resolution, because we rendered the page ourselves.
 * - [OcrSourceKind.SCREENSHOT] - the screen density is known to the system exactly.
 * - [OcrSourceKind.CAMERA_PHOTO] - the scene width follows from the subject distance and the
 *   35 mm-equivalent focal length by similar triangles, so a photo carrying both EXIF tags gets
 *   arithmetic rather than an assumed page size (S1876). A photo carrying neither falls back to the
 *   floor: `docs/OCR_OVERLAY_ACCURACY.md` rules out the neighbouring project's 11-inch book page
 *   for our material, and inventing a replacement constant is the guess this class exists to
 *   remove. Which rule serves those photos is the ticket's one remaining open question.
 * - [OcrSourceKind.UNKNOWN] - the caller could not tell, so the floor is declared rather than a
 *   plausible-looking number.
 */
class EstimateOcrResolutionUseCase {

    /**
     * @param sourceKind what the image is.
     * @param pixelWidth the bitmap width handed to the recogniser.
     * @param pageWidthPoints page width in PDF points, required for
     *   [OcrSourceKind.RENDERED_DOCUMENT_PAGE] and ignored otherwise.
     * @param screenDensityDpi the display density, required for [OcrSourceKind.SCREENSHOT] and
     *   ignored otherwise.
     * @param focal35Mm EXIF `TAG_FOCAL_LENGTH_IN_35MM_FILM`, used with [subjectDistanceM] for
     *   [OcrSourceKind.CAMERA_PHOTO] and ignored otherwise.
     * @param subjectDistanceM EXIF `TAG_SUBJECT_DISTANCE` in metres, used with [focal35Mm] for
     *   [OcrSourceKind.CAMERA_PHOTO] and ignored otherwise. EXIF writes 0 for "unknown", which is
     *   read here as absent rather than as zero metres.
     * @return the resolution to declare, never below [FLOOR_DPI].
     */
    operator fun invoke(
        sourceKind: OcrSourceKind,
        pixelWidth: Int,
        pageWidthPoints: Float? = null,
        screenDensityDpi: Int? = null,
        focal35Mm: Float? = null,
        subjectDistanceM: Float? = null,
    ): Int {
        val raw = when (sourceKind) {
            OcrSourceKind.RENDERED_DOCUMENT_PAGE -> renderedPageDpi(pixelWidth, pageWidthPoints)
            OcrSourceKind.SCREENSHOT -> screenDensityDpi
            OcrSourceKind.CAMERA_PHOTO -> cameraPhotoDpi(pixelWidth, focal35Mm, subjectDistanceM)
            OcrSourceKind.UNKNOWN -> null
        }
        return (raw ?: FLOOR_DPI).coerceAtLeast(FLOOR_DPI)
    }

    private fun renderedPageDpi(pixelWidth: Int, pageWidthPoints: Float?): Int? {
        if (pageWidthPoints == null || pageWidthPoints <= 0f || pixelWidth <= 0) return null
        return (pixelWidth * POINTS_PER_INCH / pageWidthPoints).roundToInt()
    }

    /**
     * Scene width at the subject plane is [FULL_FRAME_WIDTH_MM] scaled by distance over focal
     * length, so the declared resolution is the pixel width spread across that many inches. A
     * distant subject yields a value under the floor, which the caller then raises - a sign read
     * from across a street genuinely carries less detail per inch than a page held at arm's length.
     */
    private fun cameraPhotoDpi(pixelWidth: Int, focal35Mm: Float?, subjectDistanceM: Float?): Int? {
        val focal = focal35Mm?.takeIf { it > 0f }
        val distance = subjectDistanceM?.takeIf { it > 0f }
        if (pixelWidth <= 0 || focal == null || distance == null) return null
        val sceneWidthMm = FULL_FRAME_WIDTH_MM * distance * MM_PER_M / focal
        return (pixelWidth * MM_PER_INCH / sceneWidthMm).roundToInt()
    }

    companion object {
        /**
         * Inherited from the neighbouring project's rule "DPI declared, floor 70, upscale below
         * 120 DPI" (`docs/OCR_OVERLAY_ACCURACY.md`). Only the floor transfers here; the upscale
         * ladder is out of scope for S1715 and the estimator behind it was rejected for our
         * material.
         */
        const val FLOOR_DPI: Int = 70

        /** PDF's own unit: a point is one seventy-second of an inch. */
        private const val POINTS_PER_INCH: Float = 72f

        /**
         * The frame width the EXIF tag `TAG_FOCAL_LENGTH_IN_35MM_FILM` normalises to by definition,
         * which is why the real sensor size never has to be known.
         */
        private const val FULL_FRAME_WIDTH_MM: Float = 36f

        /** Inches are the unit the recogniser wants; EXIF distance arrives in metres. */
        private const val MM_PER_INCH: Float = 25.4f

        /** EXIF `TAG_SUBJECT_DISTANCE` is metres, the scene arithmetic is millimetres. */
        private const val MM_PER_M: Float = 1000f
    }
}
