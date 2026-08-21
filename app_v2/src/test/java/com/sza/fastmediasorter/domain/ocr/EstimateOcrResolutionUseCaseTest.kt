package com.sza.fastmediasorter.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EstimateOcrResolutionUseCaseTest {

    private val estimate = EstimateOcrResolutionUseCase()

    @Test
    fun `rendered page resolution is arithmetic on page points, not an estimate`() {
        // A Letter page is 612 points wide. Rendered 1224 px wide that is exactly 144 dpi.
        val dpi = estimate(
            sourceKind = OcrSourceKind.RENDERED_DOCUMENT_PAGE,
            pixelWidth = 1224,
            pageWidthPoints = 612f,
        )
        assertEquals(144, dpi)
    }

    @Test
    fun `rendered page at half the pixels halves the resolution`() {
        val dpi = estimate(
            sourceKind = OcrSourceKind.RENDERED_DOCUMENT_PAGE,
            pixelWidth = 612,
            pageWidthPoints = 612f,
        )
        assertEquals(72, dpi)
    }

    @Test
    fun `screenshot declares the screen density it was told`() {
        val dpi = estimate(
            sourceKind = OcrSourceKind.SCREENSHOT,
            pixelWidth = 1080,
            screenDensityDpi = 420,
        )
        assertEquals(420, dpi)
    }

    @Test
    fun `unknown kind declares the floor rather than a plausible number`() {
        val dpi = estimate(sourceKind = OcrSourceKind.UNKNOWN, pixelWidth = 4000)
        assertEquals(EstimateOcrResolutionUseCase.FLOOR_DPI, dpi)
    }

    @Test
    fun `camera photo resolution is arithmetic on subject distance and focal length`() {
        // 36 mm of frame at 0.3 m through a 26 mm-equivalent lens spans 415 mm of scene, so 4000 px
        // across it is 245 dpi - a document held at arm's length, not an assumed page size.
        val dpi = estimate(
            sourceKind = OcrSourceKind.CAMERA_PHOTO,
            pixelWidth = 4000,
            focal35Mm = 26f,
            subjectDistanceM = 0.3f,
        )
        assertEquals(245, dpi)
    }

    @Test
    fun `camera photo reads a zero subject distance as unknown, not as zero metres`() {
        val dpi = estimate(
            sourceKind = OcrSourceKind.CAMERA_PHOTO,
            pixelWidth = 4000,
            focal35Mm = 26f,
            subjectDistanceM = 0f,
        )
        assertEquals(EstimateOcrResolutionUseCase.FLOOR_DPI, dpi)
    }

    @Test
    fun `camera photo without a focal length declares the floor`() {
        // The open half of the estimator: no EXIF pair, so the floor rather than an invented rule.
        val dpi = estimate(
            sourceKind = OcrSourceKind.CAMERA_PHOTO,
            pixelWidth = 4000,
            subjectDistanceM = 0.3f,
        )
        assertEquals(EstimateOcrResolutionUseCase.FLOOR_DPI, dpi)
    }

    @Test
    fun `a distant camera subject computes below the floor and is raised to it`() {
        // The same lens at 2 m spans 2769 mm of scene: 37 dpi, which the floor overrides.
        val dpi = estimate(
            sourceKind = OcrSourceKind.CAMERA_PHOTO,
            pixelWidth = 4000,
            focal35Mm = 26f,
            subjectDistanceM = 2f,
        )
        assertEquals(EstimateOcrResolutionUseCase.FLOOR_DPI, dpi)
    }

    @Test
    fun `a value below the floor is raised to it`() {
        // 612 points wide rendered at 306 px is 36 dpi - under the floor.
        val dpi = estimate(
            sourceKind = OcrSourceKind.RENDERED_DOCUMENT_PAGE,
            pixelWidth = 306,
            pageWidthPoints = 612f,
        )
        assertEquals(EstimateOcrResolutionUseCase.FLOOR_DPI, dpi)
    }

    @Test
    fun `a screenshot below the floor is raised to it`() {
        val dpi = estimate(
            sourceKind = OcrSourceKind.SCREENSHOT,
            pixelWidth = 320,
            screenDensityDpi = 40,
        )
        assertEquals(EstimateOcrResolutionUseCase.FLOOR_DPI, dpi)
    }

    @Test
    fun `two kinds at identical pixel width do not share one formula`() {
        // The assertion that fails if the per-kind branch is ever collapsed back into one rule.
        val page = estimate(
            sourceKind = OcrSourceKind.RENDERED_DOCUMENT_PAGE,
            pixelWidth = 1224,
            pageWidthPoints = 612f,
        )
        val screenshot = estimate(
            sourceKind = OcrSourceKind.SCREENSHOT,
            pixelWidth = 1224,
            screenDensityDpi = 420,
        )
        assertNotEquals(page, screenshot)
    }

    @Test
    fun `a rendered page missing its point size falls back to the floor rather than dividing by zero`() {
        val missing = estimate(sourceKind = OcrSourceKind.RENDERED_DOCUMENT_PAGE, pixelWidth = 1224)
        val zero = estimate(
            sourceKind = OcrSourceKind.RENDERED_DOCUMENT_PAGE,
            pixelWidth = 1224,
            pageWidthPoints = 0f,
        )
        assertEquals(EstimateOcrResolutionUseCase.FLOOR_DPI, missing)
        assertEquals(EstimateOcrResolutionUseCase.FLOOR_DPI, zero)
    }

    @Test
    fun `every kind declares something at or above the floor`() {
        OcrSourceKind.entries.forEach { kind ->
            val dpi = estimate(sourceKind = kind, pixelWidth = 1000)
            assertTrue(
                "kind $kind declared $dpi, below the floor",
                dpi >= EstimateOcrResolutionUseCase.FLOOR_DPI,
            )
        }
    }
}
