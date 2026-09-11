package com.sza.fastmediasorter.service

import com.sza.fastmediasorter.data.capture.ListenRecordingStore
import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.WearListenAckPayload
import com.sza.fastmediasorter.domain.model.WearListenRefusal
import com.sza.fastmediasorter.domain.model.WearNode
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.domain.usecase.StartWatchListeningUseCase
import com.sza.fastmediasorter.domain.usecase.StopWatchListeningUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S2881: the session state machine, which had no test at all while it lived in `WearSyncViewModel`.
 *
 * The stale-answer case is the one worth having: `listenAckFlow` replays its last value, so a
 * mismatched request id reaching the handler would close a live session or open a dead address, and
 * before this class only a comment stood between that and the player.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchListenSessionManagerTest {

    private lateinit var playback: FakePlayback
    private lateinit var repository: FakeWearableRepository
    private lateinit var scope: TestScope
    private lateinit var recordingStore: ListenRecordingStore
    private lateinit var manager: WatchListenSessionManager

    @Before
    fun setUp() {
        playback = FakePlayback()
        repository = FakeWearableRepository()
        scope = TestScope(UnconfinedTestDispatcher())
        // Mocked rather than faked: the store's own subject is the file and the four transfer
        // strategies behind it, none of which this state machine can observe.
        recordingStore = mockk(relaxed = true)
        every { recordingStore.endCapture() } returns null
        manager = WatchListenSessionManager(
            playback = playback,
            startWatchListeningUseCase = StartWatchListeningUseCase(repository),
            stopWatchListeningUseCase = StopWatchListeningUseCase(repository),
            listenRecordingStore = recordingStore,
            stateRenderer = WatchListenStateRenderer { },
            applicationScope = scope,
        )
    }

    @Test
    fun `start moves to awaiting and asks the watch`() = runTest {
        manager.start()

        assertEquals(WearListenState.Awaiting, manager.listenState.value)
        assertEquals(1, repository.startedRequestIds.size)
        assertTrue("the mirror flag is written before the command leaves", playback.prepared)
    }

    @Test
    fun `an answer for another request changes nothing`() = runTest {
        manager.start()
        repository.startedRequestIds.single()

        WearSyncEvents.emitListenAck(ackFor("some-other-request", host = "10.0.0.9"))

        assertEquals(WearListenState.Awaiting, manager.listenState.value)
        assertFalse("a stale address must never reach the player", playback.started)
    }

    @Test
    fun `an address starts playback and reports the record flag`() = runTest {
        manager.start(record = true)
        val requestId = repository.startedRequestIds.single()

        WearSyncEvents.emitListenAck(ackFor(requestId, host = "10.0.0.9"))

        assertTrue(playback.started)
        assertEquals(WearListenState.Listening(recording = true), manager.listenState.value)
    }

    @Test
    fun `a recording session is armed before playback opens the stream`() = runTest {
        var armedWhenPlaybackStarted = false
        var armed = false
        every { recordingStore.begin(any()) } answers { armed = true }
        playback.onStart = { armedWhenPlaybackStarted = armed }

        manager.start(record = true)
        WearSyncEvents.emitListenAck(ackFor(repository.startedRequestIds.single(), host = "10.0.0.9"))

        // The data source is opened inside playback start and asks for its sink there, so arming
        // afterwards would record the session from its second half onwards.
        assertTrue("the recording must be armed before the stream is opened", armedWhenPlaybackStarted)
    }

    @Test
    fun `a session without the record flag never arms a recording`() = runTest {
        manager.start()

        WearSyncEvents.emitListenAck(ackFor(repository.startedRequestIds.single(), host = "10.0.0.9"))

        verify(exactly = 0) { recordingStore.begin(any()) }
    }

    @Test
    fun `a refusal returns to idle with a reason`() = runTest {
        manager.start()
        val requestId = repository.startedRequestIds.single()

        WearSyncEvents.emitListenAck(ackFor(requestId, refusal = WearListenRefusal.DECLINED))

        val state = manager.listenState.value
        assertTrue(state is WearListenState.Idle)
        assertTrue("a refusal must say why", (state as WearListenState.Idle).messageRes != null)
        assertFalse(playback.started)
    }

    @Test
    fun `stop ends playback and tells the watch`() = runTest {
        manager.start()
        val requestId = repository.startedRequestIds.single()
        WearSyncEvents.emitListenAck(ackFor(requestId, host = "10.0.0.9"))

        manager.stop()

        assertEquals(WearListenState.Idle(), manager.listenState.value)
        assertTrue(playback.stopped)
        assertEquals(listOf(requestId), repository.stoppedRequestIds)
    }

    @Test
    fun `playback ended elsewhere ends the session and tells the watch once`() = runTest {
        manager.start()
        val requestId = repository.startedRequestIds.single()
        WearSyncEvents.emitListenAck(ackFor(requestId, host = "10.0.0.9"))

        // S2939: a pause from the media notification, then the IDLE that follows it - one end, one STOP.
        playback.endElsewhere()
        playback.endElsewhere()

        assertEquals(WearListenState.Idle(), manager.listenState.value)
        assertTrue(playback.stopped)
        assertEquals(listOf(requestId), repository.stoppedRequestIds)
    }

    @Test
    fun `starting twice does not open a second session`() = runTest {
        manager.start()
        manager.start()

        assertEquals(1, repository.startedRequestIds.size)
    }

    private fun ackFor(
        requestId: String,
        host: String = "",
        refusal: WearListenRefusal? = null,
    ) = WearListenAckPayload(
        requestId = requestId,
        host = host,
        port = if (refusal == null) STREAM_PORT else 0,
        refusal = refusal,
    )

    private class FakePlayback : WatchListenPlayback {
        var prepared = false
        var started = false
        var stopped = false

        /** Runs where the real data source would be opened, so a test can look at that instant. */
        var onStart: (() -> Unit)? = null

        /** What the real player calls when playback ends by a path the session did not take. */
        var endElsewhere: () -> Unit = {}

        override fun prepareSession() {
            prepared = true
        }

        override fun start(url: String, onPlaying: () -> Unit, onDropped: () -> Unit, onEndedElsewhere: () -> Unit) {
            started = true
            endElsewhere = onEndedElsewhere
            onStart?.invoke()
            onPlaying()
        }

        override fun stop() {
            stopped = true
        }
    }

    private class FakeWearableRepository : WearableDataLayerRepository {
        val startedRequestIds = mutableListOf<String>()
        val stoppedRequestIds = mutableListOf<String>()

        override suspend fun getConnectedNodes(): List<WearNode> =
            listOf(WearNode(id = "node-1", displayName = "Watch"))

        override suspend fun sendListenStart(nodeId: String, requestId: String) {
            startedRequestIds += requestId
        }

        override suspend fun sendListenStop(nodeId: String, requestId: String) {
            stoppedRequestIds += requestId
        }

        override suspend fun putDataItem(path: String, payload: ByteArray) = Unit

        override suspend fun sendMessage(nodeId: String, path: String, data: ByteArray) = Unit

        override suspend fun putEnvelopeDataItem(path: String, envelope: WearEventEnvelope) = Unit
    }

    private companion object {
        const val STREAM_PORT = 8080
    }
}
