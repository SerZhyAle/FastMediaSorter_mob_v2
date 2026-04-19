package com.sza.fastmediasorter.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExifPhotoSphereReaderTest {

    private val reader = ExifPhotoSphereReader()

    // ── PROJECTION_TYPE_REGEX ─────────────────────────────────────────

    @Test
    fun `parseXmp returns null when no GPano ProjectionType present`() {
        assertNull(reader.parseXmp("<rdf:Description/>"))
    }

    @Test
    fun `parseXmp recognises attribute form equirectangular`() {
        val xmp = """<rdf:Description GPano:ProjectionType="equirectangular"/>"""
        val result = reader.parseXmp(xmp)
        assertTrue(result?.isEquirectangular == true)
    }

    @Test
    fun `parseXmp recognises attribute form with spaces around equals`() {
        val xmp = """GPano:ProjectionType = 'equirectangular'"""
        val result = reader.parseXmp(xmp)
        assertTrue(result?.isEquirectangular == true)
    }

    @Test
    fun `parseXmp recognises element form equirectangular`() {
        val xmp = "<GPano:ProjectionType>equirectangular</GPano:ProjectionType>"
        val result = reader.parseXmp(xmp)
        assertTrue(result?.isEquirectangular == true)
    }

    @Test
    fun `parseXmp is case-insensitive for equirectangular`() {
        val xmp = """GPano:ProjectionType="EQUIRECTANGULAR" """
        val result = reader.parseXmp(xmp)
        assertTrue(result?.isEquirectangular == true)
    }

    // ── Integer field extraction ──────────────────────────────────────

    @Test
    fun `parseXmp extracts FullPanoWidthPixels from attribute form`() {
        val xmp = """GPano:ProjectionType="equirectangular" GPano:FullPanoWidthPixels="7680" """
        assertEquals(7680, reader.parseXmp(xmp)?.fullPanoWidthPx)
    }

    @Test
    fun `parseXmp extracts FullPanoHeightPixels from element form`() {
        val xmp = """GPano:ProjectionType="equirectangular" """ +
            "<GPano:FullPanoHeightPixels>3840</GPano:FullPanoHeightPixels>"
        assertEquals(3840, reader.parseXmp(xmp)?.fullPanoHeightPx)
    }

    @Test
    fun `parseXmp extracts CroppedAreaImageWidthPixels`() {
        val xmp = """GPano:ProjectionType="equirectangular" GPano:CroppedAreaImageWidthPixels="3840" """
        assertEquals(3840, reader.parseXmp(xmp)?.croppedAreaWidthPx)
    }

    @Test
    fun `parseXmp returns null dimensions when fields absent`() {
        val xmp = """GPano:ProjectionType="equirectangular" """
        val result = reader.parseXmp(xmp)
        assertNull(result?.fullPanoWidthPx)
        assertNull(result?.croppedAreaWidthPx)
    }

    // ── is180Projection ──────────────────────────────────────────────

    @Test
    fun `is180Projection true when cropped width is half of full pano width`() {
        val meta = ExifPhotoSphereReader.PhotoSphereMetadata(
            isEquirectangular = true,
            fullPanoWidthPx = 8000,
            croppedAreaWidthPx = 4000,
        )
        assertTrue(meta.is180Projection())
    }

    @Test
    fun `is180Projection false when cropped width equals full pano width`() {
        val meta = ExifPhotoSphereReader.PhotoSphereMetadata(
            isEquirectangular = true,
            fullPanoWidthPx = 8000,
            croppedAreaWidthPx = 8000,
        )
        assertFalse(meta.is180Projection())
    }

    @Test
    fun `is180Projection false when fullPanoWidthPx is null`() {
        val meta = ExifPhotoSphereReader.PhotoSphereMetadata(isEquirectangular = true)
        assertFalse(meta.is180Projection())
    }
}
