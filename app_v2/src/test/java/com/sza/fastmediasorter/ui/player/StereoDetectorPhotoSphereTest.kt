package com.sza.fastmediasorter.ui.player

import com.sza.fastmediasorter.domain.model.StereoMode
import org.junit.Assert.assertEquals
import org.junit.Test

class StereoDetectorPhotoSphereTest {

    @Test
    fun `detectForImage keeps filename stereo layout over generic xmp`() {
        val detector = StereoDetector(
            photoSphereReader = FakePhotoSphereReader(
                ExifPhotoSphereReader.PhotoSphereMetadata(isEquirectangular = true)
            )
        )

        val detected = detector.detectForImage(
            path = "holiday_360_sbs.jpg",
            width = 8000,
            height = 2000,
        )

        assertEquals(StereoMode.EQUIRECT_360_SBS, detected)
    }

    @Test
    fun `detectForImage uses xmp half-sphere metadata to avoid square ou false positive`() {
        val detector = StereoDetector(
            photoSphereReader = FakePhotoSphereReader(
                ExifPhotoSphereReader.PhotoSphereMetadata(
                    isEquirectangular = true,
                    fullPanoWidthPx = 8000,
                    fullPanoHeightPx = 4000,
                    croppedAreaWidthPx = 4000,
                    croppedAreaHeightPx = 4000,
                )
            )
        )

        val detected = detector.detectForImage(
            path = "IMG_0001.JPG",
            width = 4000,
            height = 4000,
        )

        assertEquals(StereoMode.EQUIRECT_180_MONO, detected)
    }

    @Test
    fun `detectForImage falls back to mono sphere when only xmp is present`() {
        val detector = StereoDetector(
            photoSphereReader = FakePhotoSphereReader(
                ExifPhotoSphereReader.PhotoSphereMetadata(isEquirectangular = true)
            )
        )

        val detected = detector.detectForImage(path = "plain_photo.jpg")

        assertEquals(StereoMode.EQUIRECT_360_MONO, detected)
    }

    private class FakePhotoSphereReader(
        private val metadata: ExifPhotoSphereReader.PhotoSphereMetadata?
    ) : PhotoSphereMetadataReader {
        override fun read(path: String): ExifPhotoSphereReader.PhotoSphereMetadata? = metadata
    }
}