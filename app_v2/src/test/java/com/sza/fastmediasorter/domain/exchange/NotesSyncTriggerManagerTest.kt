package com.sza.fastmediasorter.domain.exchange

import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
import com.sza.fastmediasorter.data.remote.exchange.ExchangeControlEvent
import com.sza.fastmediasorter.data.remote.exchange.ExchangeNotesEnvelopes
import com.sza.fastmediasorter.data.remote.exchange.ExchangeReceiverControlStreams
import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpServerTunnelManager
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.notes.NotesSyncOutcome
import com.sza.fastmediasorter.domain.repository.NotesSyncRemote
import com.sza.fastmediasorter.domain.usecase.notes.SyncNotesThroughExchangeUseCase
import com.sza.fastmediasorter.util.NotesFolderPolicy
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** S4572: the connect and `notes_changed` triggers of DEVICE-EXCHANGE 1.13 item FM run the sync for the notes entry. */
class NotesSyncTriggerManagerTest {

    private val notesEntry = entry("notes")
    private val otherEntry = entry("other")
    private val welcome = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME)
    private val notesChanged = DeviceExchangeEnvelope.of(ExchangeNotesEnvelopes.TYPE_NOTES_CHANGED)

    private val tunnelEvents = MutableSharedFlow<DeviceExchangeEnvelope>(extraBufferCapacity = BUFFER)
    private val receiverEvents = MutableSharedFlow<ExchangeControlEvent>(extraBufferCapacity = BUFFER)
    private val runs = AtomicInteger()
    private val synced = NotesSyncOutcome.Synced("h", 0, 0, 0, 0, 0, emptyList(), false)

    private val tunnel = mockk<SftpServerTunnelManager> {
        every { controlEvents } returns tunnelEvents
        every { liveEntry } returns notesEntry
    }
    private val receivers = mockk<ExchangeReceiverControlStreams> { every { events } returns receiverEvents }
    private val sync = mockk<SyncNotesThroughExchangeUseCase>()

    // Unconfined, so a collector is subscribed when start() returns and the test's emits are not lost.
    private val managerScope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setUp() {
        // The use case's default folder is read from the platform, which a JVM test has none of.
        mockkObject(NotesFolderPolicy)
        every { NotesFolderPolicy.notesDirectory() } returns File("notes")
        coEvery { sync.invoke(any(), any()) } coAnswers {
            runs.incrementAndGet()
            synced
        }
    }

    @After
    fun tearDown() {
        runBlocking { managerScope.coroutineContext.job.cancelAndJoin() }
        unmockkObject(NotesFolderPolicy)
    }

    @Test
    fun `a connect of the notes entry's own stream runs one sync`() = runBlocking {
        val manager = managerWith(notesEntry)

        manager.start()
        receiverEvents.emit(ExchangeControlEvent(notesEntry, welcome))

        awaitRuns(1)
        assertEquals(1, runs.get())
    }

    @Test
    fun `a notes_changed on the tunnel runs a sync for the live entry`() = runBlocking {
        val manager = managerWith(notesEntry)

        manager.start()
        tunnelEvents.emit(notesChanged)

        awaitRuns(1)
        assertEquals(1, runs.get())
    }

    @Test
    fun `a frame of another entry runs nothing`() = runBlocking {
        val manager = managerWith(notesEntry)

        manager.start()
        receiverEvents.emit(ExchangeControlEvent(otherEntry, notesChanged))
        // The fence: events reach one collector in order, so one run proves the other entry's frame ran none.
        receiverEvents.emit(ExchangeControlEvent(notesEntry, notesChanged))

        awaitRuns(1)
        assertEquals(1, runs.get())
    }

    @Test
    fun `an envelope that is not a trigger runs nothing`() = runBlocking {
        val manager = managerWith(notesEntry)

        manager.start()
        tunnelEvents.emit(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CAST_OFFER))
        tunnelEvents.emit(welcome)

        awaitRuns(1)
        assertEquals(1, runs.get())
    }

    @Test
    fun `with the option off no sync runs`() = runBlocking {
        val manager = managerWith(null)

        manager.start()
        receiverEvents.emit(ExchangeControlEvent(notesEntry, welcome))
        // Off is judged per frame, so the fence is a second frame answered the same way, then a short settle.
        tunnelEvents.emit(notesChanged)
        delay(SETTLE_MS)

        assertEquals(0, runs.get())
    }

    @Test
    fun `a run that synced is announced so an open screen reads the folder again`() = runBlocking {
        val manager = managerWith(notesEntry)
        val announced = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(TIMEOUT_MS) { manager.synced.first() }
        }

        manager.start()
        tunnelEvents.emit(welcome)

        announced.await()
        assertEquals(1, runs.get())
    }

    private fun managerWith(picked: DeviceExchangeAccountEntry?): NotesSyncTriggerManager {
        val remote = mockk<NotesSyncRemote>()
        coEvery { remote.entry() } returns picked
        return NotesSyncTriggerManager(tunnel, receivers, remote, sync, managerScope, Dispatchers.Unconfined)
    }

    private suspend fun awaitRuns(count: Int) = withTimeout(TIMEOUT_MS) {
        while (runs.get() < count) delay(POLL_MS)
    }

    private fun entry(login: String) = DeviceExchangeAccountEntry(
        host = "exchange.example",
        port = PORT,
        login = login,
        deviceId = "AAAAAAAAAAAAAAAAAAAAAA",
        pinnedCertificate = null,
    )

    private companion object {
        const val BUFFER = 16
        const val PORT = 8443
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 10L
        const val SETTLE_MS = 200L
    }
}
