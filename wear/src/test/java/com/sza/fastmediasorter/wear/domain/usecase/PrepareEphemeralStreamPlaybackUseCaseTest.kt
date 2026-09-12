package com.sza.fastmediasorter.wear.domain.usecase

import android.net.Uri
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S2551: the phone's camera address is a port that will not exist tomorrow, so opening it must leave
 * no record behind.
 *
 * The failure this guards is silent and only visible a day later: a play count and a home-screen
 * recent row pointing at a dead port, reopened by the launch-target resolver as if it were a channel.
 */
class PrepareEphemeralStreamPlaybackUseCaseTest {

    private val selectedMediaManager = SelectedMediaManager()
    private val playbackSetManager = PlaybackSetManager()
    private val useCase = PrepareEphemeralStreamPlaybackUseCase(selectedMediaManager, playbackSetManager)

    /**
     * `Uri.parse` is an android.jar stub returning null under `isReturnDefaultValues`, and the media
     * file's uri is not nullable - so the stub is answered here rather than the mapping avoided.
     */
    @Before
    fun stubUriParsing() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
    }

    @After
    fun releaseUriStub() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `the served url reaches the player as a direct network stream`() {
        val target = useCase(url = CAMERA_URL, title = "Phone camera")

        val published = selectedMediaManager.getSelectedFileById(target.fileId)

        assertNotNull("the camera url did not reach the player", published)
        assertTrue("the camera stream was published as a local file", published!!.isNetworkSource)
        assertEquals(CAMERA_URL, published.streamUri)
        assertEquals("Phone camera", published.file.name)
    }

    @Test
    fun `the published set holds exactly the open stream`() {
        useCase(url = CAMERA_URL, title = "Phone camera")

        val set = playbackSetManager.currentSet

        assertEquals(1, set.value?.files?.size)
        assertEquals(0, set.value?.index)
    }

    @Test
    fun `a camera session opens the video player, and audio-only opens the other one`() {
        assertTrue(useCase(url = CAMERA_URL, title = "Phone camera").isVideo)
        assertTrue(!useCase(url = CAMERA_URL, title = "Phone mic", isVideo = false).isVideo)
    }

    /**
     * The mechanism, asserted rather than assumed: the two durable writes are unreachable because the
     * repositories that own them are not constructor arguments. A test that mocked them would prove
     * the opposite of what is wanted - that they could be passed in at all.
     */
    @Test
    fun `neither durable repository can be handed to this use case`() {
        val parameterTypes = PrepareEphemeralStreamPlaybackUseCase::class.java
            .constructors
            .single()
            .parameterTypes
            .map { it.name }

        assertEquals(
            listOf(SelectedMediaManager::class.java.name, PlaybackSetManager::class.java.name),
            parameterTypes
        )
    }

    private companion object {
        const val CAMERA_URL = "rtsp://192.168.1.42:8554/camera"
    }
}
