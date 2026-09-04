package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

private const val ROBOLECTRIC_MAX_SDK = 34
private const val SAMPLE_SIZE_BYTES = 2048L
private const val SAMPLE_CREATED_AT = 1_700_000_000_000L

// Wider than MAX_EDGE_PX so the downscale branch is the one under test, not skipped.
private const val SAMPLE_EDGE_PX = 160
private const val SAMPLE_CHANNEL_STEP = 8
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val BLUE_LEVEL = 0x40
private const val BYTE_MASK = 0xFF

/**
 * S1730: the page carries every item's picture, so the two answers that keep it bounded are the
 * ceiling and the decline. A decline must be null and never an empty string - the watch renders
 * null as a type icon, while an empty picture would render as a broken one.
 */
// Robolectric 4.11 ships no image for API 36, so the test runs on the newest one it has. The code
// under test uses no API-level-specific behaviour, so the older image proves the same contract.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_MAX_SDK])
class BuildWatchThumbnailUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var useCase: BuildWatchThumbnailUseCase

    @Before
    fun setUp() {
        // S2489 added the cache repository to the constructor without updating this test, which left
        // the whole app_v2 unit source set non-compiling. Relaxed: these cases assert the ceiling and
        // the decline, and a cache that answers null sends every one of them down the decode path.
        useCase = BuildWatchThumbnailUseCase(
            RuntimeEnvironment.getApplication(),
            mockk(relaxed = true)
        )
    }

    @Test
    fun `image thumbnail stays within the declared ceiling`() {
        // The use case reads the file before it decodes anything, so a case that names a path
        // nothing can open exercises the decline branch and never reaches the ceiling it claims to
        // measure. The fixture is what makes this test about the ceiling.
        val source = writeSampleJpeg(tempFolder.newFile("photo.jpg"))
        val result = runBlocking {
            useCase(mediaFile(name = "photo.jpg", type = MediaType.IMAGE, path = source.absolutePath))
        }

        assertNotNull("An image must produce a picture the page can carry", result)
        assertTrue(
            "Encoded thumbnail must not exceed $MAX_ENCODED_CHARS chars, was ${result?.length}",
            (result?.length ?: 0) <= MAX_ENCODED_CHARS
        )
    }

    @Test
    fun `directory carries no picture`() {
        val directory = mediaFile(name = "Camera", type = MediaType.IMAGE, isDirectory = true)

        assertNull(runBlocking { useCase(directory) })
    }

    @Test
    fun `type with no obtainable preview declines with null rather than an empty result`() {
        val document = mediaFile(name = "manual.pdf", type = MediaType.PDF)

        assertNull(runBlocking { useCase(document) })
    }

    // Encoded here rather than checked in as a binary: the decoder is real under Robolectric, so the
    // fixture has to be a real JPEG, and a generated one keeps the test readable and self-contained.
    private fun writeSampleJpeg(target: File): File {
        val image = BufferedImage(SAMPLE_EDGE_PX, SAMPLE_EDGE_PX, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until SAMPLE_EDGE_PX) {
            for (x in 0 until SAMPLE_EDGE_PX) {
                val red = (x * SAMPLE_CHANNEL_STEP) and BYTE_MASK
                val green = (y * SAMPLE_CHANNEL_STEP) and BYTE_MASK
                image.setRGB(x, y, (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or BLUE_LEVEL)
            }
        }
        ImageIO.write(image, "jpg", target)
        return target
    }

    private fun mediaFile(
        name: String,
        type: MediaType,
        isDirectory: Boolean = false,
        path: String = "/storage/emulated/0/DCIM/$name"
    ): MediaFile = MediaFile(
        name = name,
        path = path,
        type = type,
        size = SAMPLE_SIZE_BYTES,
        createdDate = SAMPLE_CREATED_AT,
        isDirectory = isDirectory
    )
}
