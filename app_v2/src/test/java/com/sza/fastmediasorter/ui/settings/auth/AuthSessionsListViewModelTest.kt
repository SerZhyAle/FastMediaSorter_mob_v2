package com.sza.fastmediasorter.ui.settings.auth

import com.sza.fastmediasorter.domain.repository.AuthAccountDomain
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class AuthSessionsListViewModelTest {

    @Test
    fun `account groups preserve activity ordering and keep dismissed rows last inside a host`() = runTest {
        val repository = mockk<AuthSessionRepository>(relaxed = true)
        every { repository.observeAccountsAll() } returns flowOf(
            listOf(
                authAccount(host = "b.example", accountId = "used-recent", lastUsedAt = "2026-05-11T12:00:00Z"),
                authAccount(host = "c.example", accountId = "used-older", lastUsedAt = "2026-05-10T12:00:00Z"),
                authAccount(host = "d.example", accountId = "never-used", savedAt = "2026-05-11T10:00:00Z"),
                authAccount(
                    host = "b.example",
                    accountId = "__dismissed__:used-recent",
                    displayName = "@named_account",
                    isDismissed = true,
                ),
                authAccount(
                    host = "a.example",
                    accountId = "__dismissed__",
                    displayName = "",
                    isDismissed = true,
                ),
            ),
        )

        val groups = AuthSessionsListViewModel(repository).accountGroups.first()

        assertEquals(listOf("b.example", "c.example", "d.example", "a.example"), groups.map { it.host })
        assertEquals(
            listOf("used-recent", "__dismissed__:used-recent"),
            groups.first().accounts.map { it.accountId },
        )
    }

    @Test
    fun `visible label never falls back dismissed rows to raw storage id`() {
        val dismissed = authAccount(
            host = "example.com",
            accountId = "__dismissed__:acct-42",
            displayName = "",
            isDismissed = true,
        )

        assertEquals(
            "Not authorized (you declined)",
            dismissed.visibleLabel("Not authorized (you declined)"),
        )
    }

    private fun authAccount(
        host: String,
        accountId: String,
        displayName: String = accountId,
        savedAt: String = "2026-05-09T00:00:00Z",
        lastUsedAt: String? = null,
        isDismissed: Boolean = false,
    ): AuthAccountDomain = AuthAccountDomain(
        host = host,
        accountId = accountId,
        displayName = displayName,
        cookieCount = if (isDismissed) 0 else 2,
        savedAt = Instant.parse(savedAt),
        lastUsedAt = lastUsedAt?.let(Instant::parse),
        isDismissed = isDismissed,
    )
}