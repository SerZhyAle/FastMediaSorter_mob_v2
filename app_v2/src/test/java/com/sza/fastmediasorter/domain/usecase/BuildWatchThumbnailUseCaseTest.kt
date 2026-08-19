package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val ROBOLECTRIC_MAX_SDK = 34
private const val SAMPLE_SIZE_BYTES = 2048L
private const val SAMPLE_CREATED_AT = 1_700_000_000_000L

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

    private lateinit var useCase: BuildWatchThumbnailUseCase

    @Before
    fun setUp() {
        useCase = BuildWatchThumbnailUseCase(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `image thumbnail stays within the declared ceiling`() {
        val result = runBlocking { useCase(mediaFile(name = "photo.jpg", type = MediaType.IMAGE)) }

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

    private fun mediaFile(
        name: String,
        type: MediaType,
        isDirectory: Boolean = false
    ): MediaFile = MediaFile(
        name = name,
        path = "/storage/emulated/0/DCIM/$name",
        type = type,
        size = SAMPLE_SIZE_BYTES,
        createdDate = SAMPLE_CREATED_AT,
        isDirectory = isDirectory
    )
}
