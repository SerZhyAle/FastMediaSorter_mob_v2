package com.sza.fastmediasorter.domain.usecase.broadcast

import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialRegistry
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastTunnelIdentities
import com.sza.fastmediasorter.data.repository.DeviceExchangeAccountRepository
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.PreparedBroadcastLink
import com.sza.fastmediasorter.domain.model.PreparedLinkChoice
import com.sza.fastmediasorter.domain.repository.PreparedBroadcastLinkStore
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

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
    private val useCase = ManagePreparedBroadcastLinksUseCase(
        store,
        accounts,
        BroadcastCredentialRegistry(Dispatchers.Unconfined),
        BroadcastTunnelIdentities(),
        settings,
        Dispatchers.Unconfined,
    )

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

    private suspend fun prepare(days: Int) =
        useCase.prepare(BroadcastMode.AUDIO_ONLY, days, PreparedLinkChoice.GENERATED, null, listOf("bob"))

    private fun record(id: String, identity: String, until: Long) = PreparedBroadcastLink(
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
        tunnelAlias = null,
        tunnelPin = null,
    )

    private class FakeStore : PreparedBroadcastLinkStore {
        private val state = MutableStateFlow<List<PreparedBroadcastLink>>(emptyList())
        override val links: Flow<List<PreparedBroadcastLink>> = state
        override suspend fun all() = state.value
        override suspend fun put(link: PreparedBroadcastLink): Boolean {
            state.value = state.value.filterNot { it.broadcastId == link.broadcastId } + link
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
    }
}
