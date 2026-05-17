package com.sza.fastmediasorter.ui.player

import com.sza.fastmediasorter.domain.model.StereoMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the `userInitiated` aggressive aspect-ratio branch added to [StereoDetector.detectForImage]
 * in S0238. The passive (default) cascade is untouched; case 6 is the regression guard.
 */
class StereoDetectorUserInitiatedTest {

    @Test
    fun `userInitiated panorama 2400x800 with no tokens returns SBS_FULL`() {
        // Dimensions chosen so the conservative cascade returns MONO (ar=3.0 misses both the
        // narrow EQUIRECT_360_SBS window at AR≈4.0 and the flat-SBS band AR 3.2..3.8).
        // The aggressive branch must surface SBS_FULL on user-initiated tap.
        val detector = StereoDetector(FakePhotoSphereReader(null))

        val detected = detector.detectForImage(
            path = "/storage/renamed_panorama.jpg",
            width = 2400,
            height = 800,
            userInitiated = true,
        )

        assertEquals(StereoMode.SBS_FULL, detected)
    }

    @Test
    fun `userInitiated square 2048x2048 with no tokens returns OU`() {
        val detector = StereoDetector(FakePhotoSphereReader(null))

        val detected = detector.detectForImage(
            path = "/storage/square_no_tokens.jpg",
            width = 2048,
            height = 2048,
            userInitiated = true,
        )

        assertEquals(StereoMode.OU, detected)
    }

    @Test
    fun `userInitiated tall 1024x2048 with no tokens returns OU`() {
        val detector = StereoDetector(FakePhotoSphereReader(null))

        val detected = detector.detectForImage(
            path = "/storage/portrait_no_tokens.jpg",
            width = 1024,
            height = 2048,
            userInitiated = true,
        )

        assertEquals(StereoMode.OU, detected)
    }

    @Test
    fun `userInitiated regular DSLR landscape 4032x3024 stays MONO`() {
        val detector = StereoDetector(FakePhotoSphereReader(null))

        val detected = detector.detectForImage(
            path = "/storage/DSC_0001.JPG",
            width = 4032,
            height = 3024,
            userInitiated = true,
        )

        assertEquals(StereoMode.MONO, detected)
    }

    @Test
    fun `userInitiated filename sbs token wins over dimension heuristic`() {
        val detector = StereoDetector(FakePhotoSphereReader(null))

        // 1024x512 alone would still be aggressive-SBS by AR; the filename token confirms it.
        val detected = detector.detectForImage(
            path = "/storage/renamed_sbs_panorama.jpg",
            width = 1024,
            height = 512,
            userInitiated = true,
        )

        assertEquals(StereoMode.SBS_FULL, detected)
    }

    @Test
    fun `default passive path 2400x800 with no tokens stays MONO regression`() {
        val detector = StereoDetector(FakePhotoSphereReader(null))

        // Same dimensions as case 1 but with default userInitiated = false. The passive cascade
        // must NOT classify this as SBS — that would falsely 3D-claim ordinary panoramas.
        val detected = detector.detectForImage(
            path = "/storage/renamed_panorama.jpg",
            width = 2400,
            height = 800,
        )

        assertEquals(StereoMode.MONO, detected)
    }

    @Test
    fun `userInitiated null width returns MONO without panic`() {
        val detector = StereoDetector(FakePhotoSphereReader(null))

        val detected = detector.detectForImage(
            path = "/storage/dimensions_unknown.jpg",
            width = null,
            height = null,
            userInitiated = true,
        )

        assertEquals(StereoMode.MONO, detected)
    }

    private class FakePhotoSphereReader(
        private val metadata: ExifPhotoSphereReader.PhotoSphereMetadata?,
    ) : PhotoSphereMetadataReader {
        override fun read(path: String): ExifPhotoSphereReader.PhotoSphereMetadata? = metadata
    }
}
