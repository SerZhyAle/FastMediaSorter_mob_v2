package com.sza.fastmediasorter.domain.usecase.broadcast

import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialRegistry
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastTunnelIdentities
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeBroadcastAnnouncer
import com.sza.fastmediasorter.data.broadcast.exchange.PreparedTunnelKey
import com.sza.fastmediasorter.data.repository.DeviceExchangeAccountRepository
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.PreparedBroadcastLink
import com.sza.fastmediasorter.domain.model.PreparedLinkChoice
import com.sza.fastmediasorter.domain.repository.PreparedBroadcastLinkStore
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class ManagePreparedBroadcastLinksUseCaseTest {

    private val entry = DeviceExchangeAccountEntry("exchange.example", 7443, "alice", "DEVICE_A", null, outgoing = true)
    private val store = FakeStore()
    private val accounts = mockk<DeviceExchangeAccountRepository>(relaxed = true) {
        every { producer } returns flowOf(entry)
        every { entries } returns flowOf(listOf(entry))
    }
    private val settings = mockk<SettingsRepository>(relaxed = true) {
        every { getSettings() } returns flowOf(AppSettings())
    }
    private val tunnels = mockk<BroadcastTunnelIdentities>(relaxed = true)
    private val useCase = newUseCase(Dispatchers.Unconfined)

    private fun newUseCase(io: CoroutineDispatcher) = ManagePreparedBroadcastLinksUseCase(
        store,
        accounts,
        BroadcastCredentialRegistry(Dispatchers.Unconfined),
        tunnels,
        settings,
        mockk<ExchangeBroadcastAnnouncer>(relaxed = true),
        io,
    )

    // The retry between unreadable reads is a delay, so these tests run it on virtual time.
    private fun TestScope.virtualTimeUseCase() = newUseCase(UnconfinedTestDispatcher(testScheduler))

    @Test
    fun `a sixth live link is refused`() = runTest {
        repeat(ManagePreparedBroadcastLinksUseCase.MAX_LIVE_LINKS) { prepare(7) }

        assertEquals(ManagePreparedBroadcastLinksUseCase.Outcome.LimitReached, prepare(7))
    }

    @Test
    fun `the lifetime is clamped to 30 days and a revoked link is gone`() = runTest {
        val link = (prepare(90) as ManagePreparedBroadcastLinksUseCase.Outcome.Prepared).link

        assertEquals(TimeUnit.DAYS.toMillis(30), link.untilMs - link.createdAtMs)
        useCase.revoke(link.broadcastId)
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `expired and orphaned links are pruned and a consumed one is never listed`() = runTest {
        val now = System.currentTimeMillis()
        store.put(record("expired", entry.identity, until = now - 1))
        store.put(record("orphan", "gone", until = now + DAY))
        store.put(record("used", entry.identity, until = now + DAY).copy(consumed = true))
        store.put(record("live", entry.identity, until = now + DAY))

        val listed = useCase.links.first()

        assertEquals(listOf("live"), listed.map { it.broadcastId })
        assertEquals(setOf("used", "live"), store.all().map { it.broadcastId }.toSet())
    }

    @Test
    fun `a custom password that breaks the rules is refused`() = runTest {
        val outcome = useCase.prepare(BroadcastMode.AUDIO_ONLY, 7, PreparedLinkChoice.CUSTOM, "", emptyList())

        assertEquals(ManagePreparedBroadcastLinksUseCase.Outcome.InvalidPassword, outcome)
    }

    @Test
    fun `an unreadable list that cannot be cleared sweeps and removes nothing`() = runTest {
        val now = System.currentTimeMillis()
        store.put(record("live", entry.identity, until = now + DAY, alias = KEPT_ALIAS))
        store.put(record("expired", entry.identity, until = now - 1, alias = DEAD_ALIAS))
        store.unreadableReads = Int.MAX_VALUE
        store.discardClears = false

        virtualTimeUseCase().prune()

        verify(exactly = 0) { tunnels.sweepPrepared(any()) }
        verify(exactly = 0) { tunnels.deletePrepared(any()) }
        assertEquals(setOf("live", "expired"), store.all().map { it.broadcastId }.toSet())
    }

    @Test
    fun `a read that recovers after one failure prunes and sweeps as for a readable list`() = runTest {
        val now = System.currentTimeMillis()
        store.put(record("live", entry.identity, until = now + DAY, alias = KEPT_ALIAS))
        store.put(record("expired", entry.identity, until = now - 1, alias = DEAD_ALIAS))
        store.unreadableReads = 1

        virtualTimeUseCase().prune()

        assertEquals(0, store.discards)
        verify(exactly = 1) { tunnels.deletePrepared(DEAD_ALIAS) }
        verify(exactly = 1) { tunnels.sweepPrepared(setOf(KEPT_ALIAS)) }
        assertEquals(listOf("live"), store.all().map { it.broadcastId })
    }

    @Test
    fun `a list that stays unreadable is discarded once and the sweep follows the empty list`() = runTest {
        store.put(record("live", entry.identity, until = System.currentTimeMillis() + DAY, alias = KEPT_ALIAS))
        store.unreadableReads = Int.MAX_VALUE

        virtualTimeUseCase().prune()

        assertEquals(1, store.discards)
        verify(exactly = 1) { tunnels.sweepPrepared(emptySet()) }
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `prepare is refused with no tunnel key while the stored list is unreadable`() = runTest {
        val sealed = entry.copy(features = listOf(DeviceExchangeAccountEntry.FEATURE_SEALED))
        every { accounts.producer } returns flowOf(sealed)
        every { accounts.entries } returns flowOf(listOf(sealed))
        store.put(record("live", sealed.identity, until = System.currentTimeMillis() + DAY, alias = KEPT_ALIAS))
        store.unreadableReads = Int.MAX_VALUE
        store.discardClears = false

        val outcome = virtualTimeUseCase()
            .prepare(BroadcastMode.AUDIO_ONLY, 7, PreparedLinkChoice.GENERATED, null, emptyList())

        assertEquals(ManagePreparedBroadcastLinksUseCase.Outcome.NotStored, outcome)
        verify(exactly = 0) { tunnels.prepare() }
        assertEquals(listOf("live"), store.all().map { it.broadcastId })
    }

    @Test
    fun `a prune started while prepare waits to store sweeps only after the record names its key`() = runTest {
        val sealed = entry.copy(features = listOf(DeviceExchangeAccountEntry.FEATURE_SEALED))
        every { accounts.producer } returns flowOf(sealed)
        every { accounts.entries } returns flowOf(listOf(sealed))
        every { tunnels.prepare() } returns PreparedTunnelKey(PREPARED_ALIAS, "SHA256:pin")
        val sweeps = mutableListOf<Set<String>>()
        every { tunnels.sweepPrepared(any()) } answers { sweeps += firstArg<Set<String>>() }
        val release = CompletableDeferred<Unit>()
        store.putGate = release

        val preparing = async { prepare(7) }
        runCurrent()
        val pruning = async { useCase.prune() }
        runCurrent()
        val sweepsWhileWaiting = sweeps.size
        release.complete(Unit)
        preparing.await()
        pruning.await()

        // Only prepare's own prune, which ran before its key existed, may have swept while it waited to store.
        assertEquals(1, sweepsWhileWaiting)
        assertEquals(setOf(PREPARED_ALIAS), sweeps.last())
    }

    private suspend fun prepare(days: Int) =
        useCase.prepare(BroadcastMode.AUDIO_ONLY, days, PreparedLinkChoice.GENERATED, null, listOf("bob"))

    private fun record(id: String, identity: String, until: Long, alias: String? = null) = PreparedBroadcastLink(
        broadcastId = id,
        entryIdentity = identity,
        publicEndpoint = "exchange.example:7443",
        certFingerprint = null,
        mode = "AUDIO_ONLY",
        title = "Phone",
        sourceId = null,
        createdAtMs = 0L,
        untilMs = until,
        linkChoice = PreparedLinkChoice.OPEN,
        linkUser = null,
        linkPassword = null,
        relayLinkKey = null,
        tunnelAlias = alias,
        tunnelPin = null,
    )

    private class FakeStore : PreparedBroadcastLinkStore {
        private val state = MutableStateFlow<List<PreparedBroadcastLink>>(emptyList())

        /** How many of the next reads answer unreadable. */
        var unreadableReads = 0

        /** Whether [discardUnreadable] clears the unreadable state, as a real corrupt list would be cleared. */
        var discardClears = true
        var discards = 0
        var putGate: CompletableDeferred<Unit>? = null

        override val links: Flow<List<PreparedBroadcastLink>> = state
        override suspend fun all() = state.value

        override suspend fun allOrNull(): List<PreparedBroadcastLink>? {
            if (unreadableReads == 0) return state.value
            unreadableReads--
            return null
        }

        override suspend fun put(link: PreparedBroadcastLink): Boolean {
            putGate?.await()
            state.value = state.value.filterNot { it.broadcastId == link.broadcastId } + link
            return true
        }

        override suspend fun discardUnreadable(): Boolean {
            discards++
            if (!discardClears) return false
            unreadableReads = 0
            state.value = emptyList()
            return true
        }

        override suspend fun remove(broadcastId: String) {
            state.value = state.value.filterNot { it.broadcastId == broadcastId }
        }

        override suspend fun markConsumed(broadcastId: String) {
            state.value = state.value.map { if (it.broadcastId == broadcastId) it.copy(consumed = true) else it }
        }
    }

    private companion object {
        val DAY = TimeUnit.DAYS.toMillis(1)
        const val KEPT_ALIAS = "fms-broadcast-prepared-keep"
        const val DEAD_ALIAS = "fms-broadcast-prepared-dead"
        const val PREPARED_ALIAS = "fms-broadcast-prepared-new"
    }
}
