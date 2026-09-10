package com.sza.fastmediasorter.domain.usecase.wear

import androidx.fragment.app.FragmentActivity
import com.sza.fastmediasorter.core.cast.ActiveCastControllerHolder
import com.sza.fastmediasorter.core.cast.CastController
import com.sza.fastmediasorter.core.cast.CastStereoCrop
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.WearCastMediaType
import com.sza.fastmediasorter.domain.model.WearCastOrigin
import com.sza.fastmediasorter.domain.model.WearCastOutcome
import com.sza.fastmediasorter.domain.model.WearCastRequest
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S2531: the outcome table of CastFromWatchRequestUseCase - one case per answer the watch can get.
 */
class CastFromWatchRequestUseCaseTest {

    private val resourceRepository = mockk<ResourceRepository>()

    @Test
    fun `no live player asks for the picker rather than reporting no Cast`() = runBlocking {
        val useCase = CastFromWatchRequestUseCase(ActiveCastControllerHolder(), resourceRepository)

        val ack = useCase(streamRequest())

        assertEquals(WearCastOutcome.PICKER_NEEDED, ack.outcome)
        assertEquals(REQUEST_ID, ack.requestId)
    }

    @Test
    fun `cast seam unavailable is refused outright`() = runBlocking {
        val controller = FakeCastController(available = false, casting = false)
        val useCase = useCaseFor(controller)

        val ack = useCase(streamRequest())

        assertEquals(WearCastOutcome.CAST_UNAVAILABLE, ack.outcome)
        assertEquals(REQUEST_ID, ack.requestId)
        assertNull(controller.sent)
    }

    @Test
    fun `available seam with no session asks for the picker`() = runBlocking {
        val controller = FakeCastController(available = true, casting = false)
        val useCase = useCaseFor(controller)

        val ack = useCase(streamRequest())

        assertEquals(WearCastOutcome.PICKER_NEEDED, ack.outcome)
        assertNull(controller.sent)
    }

    @Test
    fun `stream request reaches the session under its own url`() = runBlocking {
        val controller = FakeCastController(available = true, casting = true)
        val useCase = useCaseFor(controller)

        val ack = useCase(streamRequest())

        assertEquals(WearCastOutcome.CASTING, ack.outcome)
        assertEquals(STREAM_URL, controller.sent?.path)
        assertEquals(MediaType.VIDEO, controller.sent?.type)
        assertNull(controller.sent?.resourceId)
    }

    @Test
    fun `network source request is addressed against its resource`() = runBlocking {
        val controller = FakeCastController(available = true, casting = true)
        coEvery { resourceRepository.getResourceById(SOURCE_ID) } returns MediaResource(
            id = SOURCE_ID,
            name = "Share",
            path = "smb://host:445/media/",
            type = ResourceType.SMB
        )
        val useCase = useCaseFor(controller)

        val ack = useCase(networkSourceRequest())

        assertEquals(WearCastOutcome.CASTING, ack.outcome)
        assertEquals("smb://host:445/media/photos/beach.jpg", controller.sent?.path)
        assertEquals(MediaType.IMAGE, controller.sent?.type)
        assertEquals(SOURCE_ID, controller.sent?.resourceId)
    }

    @Test
    fun `unknown source id is not found`() = runBlocking {
        val controller = FakeCastController(available = true, casting = true)
        coEvery { resourceRepository.getResourceById(SOURCE_ID) } returns null
        val useCase = useCaseFor(controller)

        val ack = useCase(networkSourceRequest())

        assertEquals(WearCastOutcome.NOT_FOUND, ack.outcome)
        assertNull(controller.sent)
    }

    private fun useCaseFor(controller: CastController) = CastFromWatchRequestUseCase(
        ActiveCastControllerHolder().apply { attach(controller) },
        resourceRepository
    )

    private fun streamRequest() = WearCastRequest(
        requestId = REQUEST_ID,
        origin = WearCastOrigin.STREAM,
        address = STREAM_URL,
        sourceId = 0L,
        mediaType = WearCastMediaType.VIDEO,
        displayName = "Live camera"
    )

    private fun networkSourceRequest() = WearCastRequest(
        requestId = REQUEST_ID,
        origin = WearCastOrigin.NETWORK_SOURCE,
        address = "photos/beach.jpg",
        sourceId = SOURCE_ID,
        mediaType = WearCastMediaType.IMAGE,
        displayName = "beach.jpg"
    )

    /** Records the one file handed to the seam, so a wrong address is a failed assert, not a no-op. */
    private class FakeCastController(
        available: Boolean,
        casting: Boolean
    ) : CastController {

        var sent: MediaFile? = null
            private set

        var stopped: Boolean = false
            private set

        override val isCastAvailable: Boolean = available

        override val isCasting: Boolean = casting

        override val castAvailableState: StateFlow<Boolean> = MutableStateFlow(available)

        override fun init() = Unit

        override fun release() = Unit

        override fun showCastDialog(activity: FragmentActivity) = Unit

        override fun sendCurrentMedia(file: MediaFile, stereoCrop: CastStereoCrop?) {
            sent = file
        }

        override fun stopCasting() {
            stopped = true
        }
    }

    private companion object {
        const val REQUEST_ID = "req-1"
        const val SOURCE_ID = 42L
        const val STREAM_URL = "https://example.com/live.m3u8"
    }
}
