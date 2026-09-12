package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearCastAttempt
import com.sza.fastmediasorter.wear.domain.model.WearCastMediaType
import com.sza.fastmediasorter.wear.domain.model.WearCastOrigin
import com.sza.fastmediasorter.wear.domain.model.WearCastOutcome
import com.sza.fastmediasorter.wear.domain.model.WearCastRequest
import com.sza.fastmediasorter.wear.domain.model.WearCastState
import com.sza.fastmediasorter.wear.domain.model.WearCastSubject
import com.sza.fastmediasorter.wear.domain.repository.WearCastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S2531: the two mappings the watch can send, and the one content kind it refuses without sending.
 */
class RequestCastOnPhoneUseCaseTest {

    @Test
    fun `a stream is sent under its own url`() = runBlocking {
        val repository = FakeWearCastRepository()
        val useCase = RequestCastOnPhoneUseCase(repository)

        val attempt = useCase(
            WearCastSubject.Stream(
                url = STREAM_URL,
                displayName = "Live camera",
                mediaType = WearCastMediaType.VIDEO
            )
        )

        assertEquals(WearCastAttempt.Answered(WearCastOutcome.CASTING), attempt)
        assertEquals(WearCastOrigin.STREAM, repository.sent?.origin)
        assertEquals(STREAM_URL, repository.sent?.address)
        assertEquals(WearCastMediaType.VIDEO, repository.sent?.mediaType)
        assertEquals(0L, repository.sent?.sourceId)
    }

    @Test
    fun `a network file is sent as a path inside its phone source`() = runBlocking {
        val repository = FakeWearCastRepository()
        val useCase = RequestCastOnPhoneUseCase(repository)

        val attempt = useCase(networkFile(sourceId = "42"))

        assertEquals(WearCastAttempt.Answered(WearCastOutcome.CASTING), attempt)
        assertEquals(WearCastOrigin.NETWORK_SOURCE, repository.sent?.origin)
        assertEquals("photos/beach.jpg", repository.sent?.address)
        assertEquals(42L, repository.sent?.sourceId)
        assertEquals(WearCastMediaType.IMAGE, repository.sent?.mediaType)
    }

    @Test
    fun `watch-local content is refused before anything is sent`() = runBlocking {
        val repository = FakeWearCastRepository()
        val useCase = RequestCastOnPhoneUseCase(repository)

        val attempt = useCase(WearCastSubject.WatchLocalFile("note.m4a"))

        assertEquals(WearCastAttempt.NotCastable, attempt)
        assertNull(repository.sent)
    }

    @Test
    fun `a source this watch invented has no phone id and is refused`() = runBlocking {
        val repository = FakeWearCastRepository()
        val useCase = RequestCastOnPhoneUseCase(repository)

        val attempt = useCase(networkFile(sourceId = "0d1a5e7c-hand-added"))

        assertEquals(WearCastAttempt.NotCastable, attempt)
        assertNull(repository.sent)
    }


    private fun networkFile(sourceId: String) = WearCastSubject.NetworkFile(
        source = NetworkSource(
            id = sourceId,
            type = NetworkSourceType.SMB,
            name = "Share",
            server = "host",
            username = "u",
            password = "p"
        ),
        relativePath = "photos/beach.jpg",
        displayName = "beach.jpg",
        mediaType = WearCastMediaType.IMAGE
    )

    /** Records the one request that left, so a wrong mapping is a failed assert, not a silent send. */
    private class FakeWearCastRepository : WearCastRepository {

        var sent: WearCastRequest? = null
            private set

        override val castState: StateFlow<WearCastState> =
            MutableStateFlow(WearCastState(isCasting = false, deviceName = null, displayName = null))

        override suspend fun requestCast(request: WearCastRequest): WearCastOutcome {
            sent = request
            return WearCastOutcome.CASTING
        }

        override suspend fun requestStop(): WearCastOutcome = WearCastOutcome.CASTING

        override fun onAckReceived(payload: ByteArray) = Unit

        override fun onStateReceived(payload: ByteArray) = Unit
    }

    private companion object {
        const val STREAM_URL = "https://example.com/live.m3u8"
    }
}
