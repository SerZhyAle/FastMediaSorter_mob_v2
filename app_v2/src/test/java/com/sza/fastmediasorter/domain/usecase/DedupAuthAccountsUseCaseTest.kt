package com.sza.fastmediasorter.domain.usecase

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.sza.fastmediasorter.data.link.cookie.EncryptedCookieStore
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.net.HttpCookie
import java.time.Instant

/**
 * JVM coverage for [DedupAuthAccountsUseCase]. The dedup grouping/keep-newest logic is pure JVM;
 * [EncryptedCookieStore] is mocked (its real impl needs EncryptedSharedPreferences) and the
 * idempotency DataStore is mocked with in-memory Preferences.
 */
class DedupAuthAccountsUseCaseTest {

    private val store = mockk<EncryptedCookieStore>()
    private val dataStore = mockk<DataStore<Preferences>>()
    private val doneKey = booleanPreferencesKey("dedup_s0211_done")
    private lateinit var useCase: DedupAuthAccountsUseCase

    @Before
    fun setup() {
        useCase = DedupAuthAccountsUseCase(store, dataStore)
    }

    private fun entry(id: String, lastUsedAt: Instant?, savedAt: Instant? = null) =
        EncryptedCookieStore.AccountEntry(
            accountId = id,
            displayName = id,
            savedAt = savedAt,
            lastUsedAt = lastUsedAt,
            cookieCount = 1,
            type = EncryptedCookieStore.TYPE_ACTIVE,
        )

    // instagram.com identity is derived from the ds_user_id cookie value.
    private fun igCookies(userId: String) = listOf(HttpCookie("ds_user_id", userId))

    private fun stubDataStore(done: Boolean) {
        val prefs = if (done) mutablePreferencesOf(doneKey to true) else emptyPreferences()
        every { dataStore.data } returns flowOf(prefs)
        coEvery { dataStore.updateData(any()) } returns emptyPreferences()
    }

    @Test
    fun `returns immediately when already done`() = runTest {
        stubDataStore(done = true)

        useCase()

        // No store access when the done-flag is set.
        verify(exactly = 0) { store.listAllAccounts() }
    }

    @Test
    fun `deletes all-but-newest duplicate in a same-identity group`() = runTest {
        stubDataStore(done = false)
        val host = "instagram.com"
        val keep = entry("keep", lastUsedAt = Instant.ofEpochSecond(2000))
        val drop = entry("drop", lastUsedAt = Instant.ofEpochSecond(1000))
        every { store.listAllAccounts() } returns listOf(host to keep, host to drop)
        // Same identity (same ds_user_id) for both accounts.
        every { store.loadForAccount(host, "keep") } returns igCookies("42")
        every { store.loadForAccount(host, "drop") } returns igCookies("42")
        val deletedIds = mutableListOf<String>()
        every { store.deleteForAccount(host, capture(deletedIds)) } just Runs

        useCase()

        assertEquals(listOf("drop"), deletedIds)
        verify(exactly = 0) { store.deleteForAccount(host, "keep") }
    }

    @Test
    fun `keeps accounts with distinct identities`() = runTest {
        stubDataStore(done = false)
        val host = "instagram.com"
        every { store.listAllAccounts() } returns listOf(
            host to entry("a", lastUsedAt = Instant.ofEpochSecond(1000)),
            host to entry("b", lastUsedAt = Instant.ofEpochSecond(2000)),
        )
        every { store.loadForAccount(host, "a") } returns igCookies("1")
        every { store.loadForAccount(host, "b") } returns igCookies("2")

        useCase()

        verify(exactly = 0) { store.deleteForAccount(any(), any()) }
    }

    @Test
    fun `ignores accounts with no computable identity`() = runTest {
        stubDataStore(done = false)
        val host = "unknown-host.example"
        every { store.listAllAccounts() } returns listOf(
            host to entry("a", lastUsedAt = Instant.ofEpochSecond(1000)),
            host to entry("b", lastUsedAt = Instant.ofEpochSecond(2000)),
        )
        // Unknown host -> AccountIdentityExtractor returns null -> not deduplicated.
        every { store.loadForAccount(any(), any()) } returns emptyList()

        useCase()

        verify(exactly = 0) { store.deleteForAccount(any(), any()) }
    }

    @Test
    fun `marks done flag after a clean scan`() = runTest {
        stubDataStore(done = false)
        every { store.listAllAccounts() } returns emptyList()
        val transform = slot<suspend (Preferences) -> Preferences>()
        coEvery { dataStore.updateData(capture(transform)) } returns emptyPreferences()

        useCase()

        // The done flag is persisted via updateData.
        val updated = transform.captured.invoke(emptyPreferences())
        assertEquals(true, updated[doneKey])
    }
}
