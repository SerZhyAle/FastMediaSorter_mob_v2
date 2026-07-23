package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.link.cookie.EncryptedCookieStore
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpCookie
import java.time.Instant

/**
 * Orchestration/branch coverage for [AuthSessionRepositoryImpl] over a mocked
 * [EncryptedCookieStore]: the blank/empty save guards, the dismissed-record blank-accountId
 * delegations, the webview reuse-vs-new-UUID path, and the toDomain displayName fallback +
 * settings ordering surfaced through observeAccounts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthSessionRepositoryImplTest {

    private lateinit var store: EncryptedCookieStore

    @Before
    fun setUp() {
        store = mockk(relaxed = true)
        // refreshFlows() (called from init and after writes) reads listAllAccounts().
        every { store.listAllAccounts() } returns emptyList()
    }

    // Unconfined scope runs the init launch eagerly on the calling thread, so migrateIfNeeded +
    // refreshFlows complete synchronously during construction - matching the pre-S1153 timing the
    // flow assertions below rely on. Production injects the IO-backed @ApplicationScope.
    private fun repo() = AuthSessionRepositoryImpl(store, CoroutineScope(Dispatchers.Unconfined))

    private fun cookie() = HttpCookie("sid", "abc")

    private fun entry(
        accountId: String,
        displayName: String = "",
        savedAt: Instant? = Instant.ofEpochMilli(1000),
        lastUsedAt: Instant? = null,
        cookieCount: Int = 1,
        type: String = EncryptedCookieStore.TYPE_ACTIVE,
    ) = EncryptedCookieStore.AccountEntry(accountId, displayName, savedAt, lastUsedAt, cookieCount, type)

    @Test
    fun `saveSession skips when host accountId or cookies are empty`() = runTest {
        val repo = repo()
        repo.saveSession(host = "", accountId = "a", displayName = "d", cookies = listOf(cookie()))
        repo.saveSession(host = "h", accountId = "", displayName = "d", cookies = listOf(cookie()))
        repo.saveSession(host = "h", accountId = "a", displayName = "d", cookies = emptyList())

        coVerify(exactly = 0) { store.saveForAccount(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `saveSession persists a valid account`() = runTest {
        repo().saveSession(host = "h", accountId = "a", displayName = "d", cookies = listOf(cookie()), userAgent = "UA")
        coVerify(exactly = 1) { store.saveForAccount("h", "a", "d", any(), "UA") }
    }

    @Test
    fun `saveSessionFromWebView returns null for blank host or empty cookies`() = runTest {
        val repo = repo()
        assertNull(repo.saveSessionFromWebView(host = "", displayName = "d", cookies = listOf(cookie()), userAgent = null))
        assertNull(repo.saveSessionFromWebView(host = "h", displayName = "d", cookies = emptyList(), userAgent = null))
    }

    @Test
    fun `saveSessionFromWebView reuses an existing account id when identity matches`() = runTest {
        // instagram.com + ds_user_id makes AccountIdentityExtractor yield a non-null identity.
        val cookies = listOf(HttpCookie("ds_user_id", "42"))
        every { store.findAccountIdByIdentity("instagram.com", "42") } returns "reused-id"

        val id = repo().saveSessionFromWebView(host = "instagram.com", displayName = "d", cookies = cookies, userAgent = null)

        assertEquals("reused-id", id)
        coVerify { store.saveForAccount("instagram.com", "reused-id", "d", any(), null) }
    }

    @Test
    fun `saveSessionFromWebView mints a new id when no identity reuse`() = runTest {
        // Unknown platform host -> extractor returns null -> findAccountIdByIdentity never consulted.
        val id = repo().saveSessionFromWebView(host = "example.com", displayName = "d", cookies = listOf(cookie()), userAgent = null)

        assertTrue(id != null && id.isNotBlank() && id != "reused-id")
        coVerify { store.saveForAccount("example.com", id!!, "d", any(), null) }
    }

    @Test
    fun `markDismissed ignores a blank host`() = runTest {
        repo().markDismissed("")
        coVerify(exactly = 0) { store.saveAsDismissed(any()) }
    }

    @Test
    fun `markDismissedForAccount with blank accountId delegates to host-level dismiss`() = runTest {
        repo().markDismissedForAccount(host = "h", accountId = "", displayName = "d")
        coVerify(exactly = 1) { store.saveAsDismissed("h") }
        coVerify(exactly = 0) { store.saveAsDismissedForAccount(any(), any(), any()) }
    }

    @Test
    fun `markDismissedForAccount with accountId dismisses that account`() = runTest {
        repo().markDismissedForAccount(host = "h", accountId = "acc", displayName = null)
        coVerify(exactly = 1) { store.saveAsDismissedForAccount("h", "acc", "") }
    }

    @Test
    fun `isDismissedForAccount falls back to host record when accountId blank`() = runTest {
        every { store.hasDismissedRecord("h") } returns true
        assertTrue(repo().isDismissedForAccount(host = "h", accountId = ""))
        verify(exactly = 0) { store.hasDismissedRecordForAccount(any(), any()) }
    }

    @Test
    fun `isDismissedForAccount checks both host and account record`() = runTest {
        every { store.hasDismissedRecord("h") } returns false
        every { store.hasDismissedRecordForAccount("h", "acc") } returns true
        assertTrue(repo().isDismissedForAccount(host = "h", accountId = "acc"))
    }

    @Test
    fun `observeAccounts maps active entries and falls back displayName to accountId`() = runTest {
        every { store.listAllAccounts() } returns listOf(
            "host.com" to entry(accountId = "acc-1", displayName = "   ", cookieCount = 2),
        )

        val accounts = repo().observeAccounts().first()
        assertEquals(1, accounts.size)
        assertEquals("acc-1", accounts.single().displayName) // blank -> accountId fallback
        assertEquals(2, accounts.single().cookieCount)
    }

    @Test
    fun `observeAccounts filters out entries with zero cookies`() = runTest {
        every { store.listAllAccounts() } returns listOf(
            "host.com" to entry(accountId = "live", cookieCount = 1),
            "host.com" to entry(accountId = "empty", cookieCount = 0),
        )

        val accounts = repo().observeAccounts().first()
        assertEquals(listOf("live"), accounts.map { it.accountId })
    }

    @Test
    fun `observeAccounts orders last-used before never-used`() = runTest {
        every { store.listAllAccounts() } returns listOf(
            "h" to entry(accountId = "never", lastUsedAt = null, savedAt = Instant.ofEpochMilli(5000)),
            "h" to entry(accountId = "recent", lastUsedAt = Instant.ofEpochMilli(9000), savedAt = Instant.ofEpochMilli(1000)),
        )

        val accounts = repo().observeAccountsAll().first()
        assertEquals(listOf("recent", "never"), accounts.map { it.accountId })
    }

    @Test
    fun `hasAnySession reflects store account presence`() = runTest {
        every { store.listAccounts("h") } returns listOf(entry(accountId = "a"))
        every { store.listAccounts("empty") } returns emptyList()
        assertTrue(repo().hasAnySession("h"))
        assertFalse(repo().hasAnySession("empty"))
    }
}
