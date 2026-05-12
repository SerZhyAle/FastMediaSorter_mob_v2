package com.sza.fastmediasorter.ui.share

import com.sza.fastmediasorter.domain.repository.AuthAccountDomain
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import com.sza.fastmediasorter.ui.share.helpers.AccountSelectionManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * S0166 Phase 06: Unit tests for AccountSelectionManager account-selection branch logic.
 *
 * Covers:
 *  - No stored accounts        → onNoneAvailable is called (no-record case).
 *  - Dismissed account only    → treated as no-record; onNoneAvailable is called.
 *  - One active account        → onSelected is called with that account (single-record case).
 *  - Active + dismissed mixed  → dismissed is filtered; onSelected called with active (dismissal case).
 *
 * Multi-account picker (≥2 active): requires AppCompatActivity UI; covered by manual device testing.
 * Preview-only and retry paths: live in ReceiveShareActivity; covered by manual device testing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountSelectionManagerTest {

    private lateinit var repository: AuthSessionRepository
    private lateinit var manager: AccountSelectionManager

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        manager = AccountSelectionManager(repository)
    }

    // ── No accounts (no-record case) ─────────────────────────────────────────

    @Test
    fun `no accounts for host calls onNoneAvailable`() = runTest {
        coEvery { repository.listAccountsForHost(HOST) } returns emptyList()

        var noneAvailableCalled = false
        var selectedAccount: AuthAccountDomain? = null

        manager.selectAccount(
            host = HOST,
            activity = mockk(relaxed = true),
            onSelected = { selectedAccount = it },
            onNoneAvailable = { noneAvailableCalled = true },
            onCancelled = {},
        )

        assertTrue("onNoneAvailable should be called when no accounts exist", noneAvailableCalled)
        assertNull("onSelected should not be called", selectedAccount)
    }

    // ── Dismissed only (dismissal case) ─────────────────────────────────────

    @Test
    fun `only dismissed accounts calls onNoneAvailable`() = runTest {
        val dismissed = account("__dismissed__", isDismissed = true)
        coEvery { repository.listAccountsForHost(HOST) } returns listOf(dismissed)

        var noneAvailableCalled = false

        manager.selectAccount(
            host = HOST,
            activity = mockk(relaxed = true),
            onSelected = {},
            onNoneAvailable = { noneAvailableCalled = true },
            onCancelled = {},
        )

        assertTrue("onNoneAvailable should be called when only dismissed records exist", noneAvailableCalled)
    }

    // ── Single active account (single-record case) ───────────────────────────

    @Test
    fun `single active account calls onSelected with that account`() = runTest {
        val active = account("@myaccount", isDismissed = false)
        coEvery { repository.listAccountsForHost(HOST) } returns listOf(active)

        var selectedAccount: AuthAccountDomain? = null

        manager.selectAccount(
            host = HOST,
            activity = mockk(relaxed = true),
            onSelected = { selectedAccount = it },
            onNoneAvailable = {},
            onCancelled = {},
        )

        assertEquals("onSelected should receive the single active account", active, selectedAccount)
    }

    // ── Mixed active + dismissed (dismissal filtering) ───────────────────────

    @Test
    fun `active plus dismissed accounts calls onSelected with active account`() = runTest {
        val active = account("@workaccount", isDismissed = false)
        val dismissed = account("__dismissed__", isDismissed = true)
        coEvery { repository.listAccountsForHost(HOST) } returns listOf(active, dismissed)

        var selectedAccount: AuthAccountDomain? = null
        var noneAvailableCalled = false

        manager.selectAccount(
            host = HOST,
            activity = mockk(relaxed = true),
            onSelected = { selectedAccount = it },
            onNoneAvailable = { noneAvailableCalled = true },
            onCancelled = {},
        )

        assertFalse("onNoneAvailable should not be called when one active account exists", noneAvailableCalled)
        assertEquals("onSelected should receive the active account", active, selectedAccount)
    }

    @Test
    fun `dismissed account with real accountId (per-account dismiss) is filtered out`() = runTest {
        val accountDismissed = account("@personalaccount", isDismissed = true)
        coEvery { repository.listAccountsForHost(HOST) } returns listOf(accountDismissed)

        var noneAvailableCalled = false

        manager.selectAccount(
            host = HOST,
            activity = mockk(relaxed = true),
            onSelected = {},
            onNoneAvailable = { noneAvailableCalled = true },
            onCancelled = {},
        )

        assertTrue("Per-account dismissed record should be filtered same as host-level dismiss", noneAvailableCalled)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun account(accountId: String, isDismissed: Boolean): AuthAccountDomain =
        AuthAccountDomain(
            host = HOST,
            accountId = accountId,
            displayName = accountId,
            cookieCount = if (isDismissed) 0 else 3,
            savedAt = Instant.now(),
            lastUsedAt = null,
            isDismissed = isDismissed,
        )

    private fun assertNull(message: String, value: AuthAccountDomain?) {
        assertTrue(message, value == null)
    }

    private companion object {
        const val HOST = "instagram.com"
    }
}
