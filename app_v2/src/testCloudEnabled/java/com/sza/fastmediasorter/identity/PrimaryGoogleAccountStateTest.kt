package com.sza.fastmediasorter.identity

import android.content.Context
import com.sza.fastmediasorter.domain.identity.GoogleAccessToken
import com.sza.fastmediasorter.domain.identity.GoogleScope
import com.sza.fastmediasorter.domain.identity.NeedsResignInReason
import com.sza.fastmediasorter.domain.identity.PrimaryGoogleAccount
import com.sza.fastmediasorter.domain.identity.PrimaryGoogleAccountState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S0200 Step 02.5: covers the state-machine paths of [CredentialManagerGoogleIdentityRepository]
 * that don't require Credential Manager static-factory mocking. The interactive sign-in branches
 * (`signInPrimary`, `requestAdditionalScopes`, `requestSecondaryAccount`) live in the Robolectric
 * test in Step 02.6.
 *
 * Validated paths:
 *  - `getAccessToken` with `Unbound` state returns null and does not call the issuer.
 *  - `getAccessToken` with `Bound` state delegates to issuer and returns its token.
 *  - `getAccessToken` returning null transitions state to `NeedsResignIn(TokenExpired)`.
 *  - `signOutPrimary` clears issuer + store and emits `Unbound`.
 *  - `invalidateToken` delegates to issuer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrimaryGoogleAccountStateTest {

    private val context: Context = mockk(relaxed = true)
    private lateinit var store: PrimaryGoogleAccountStore
    private lateinit var issuer: GoogleTokenIssuer
    private lateinit var repo: CredentialManagerGoogleIdentityRepository

    private val scopes = setOf(GoogleScope.DRIVE, GoogleScope.DRIVE_READONLY)

    private val sampleAccount = PrimaryGoogleAccount(
        email = "user@example.com",
        displayName = "Test User",
        photoUrl = null,
        grantedScopes = scopes,
        boundAt = Instant.parse("2026-05-16T20:00:00Z")
    )

    @Before
    fun setUp() {
        store = mockk(relaxed = true)
        issuer = mockk(relaxed = true)
    }

    private fun TestScope.buildRepo(): CredentialManagerGoogleIdentityRepository =
        CredentialManagerGoogleIdentityRepository(
            appContext = context,
            store = store,
            tokenIssuer = issuer,
            scope = this,
            webClientId = "stub-web-client-id"
        )

    @Test
    fun `getAccessToken when Unbound returns null and does not call issuer`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { store.load() } returns null
        repo = buildRepo()

        val token = repo.getAccessToken(scopes)

        assertNull(token)
        coVerify(exactly = 0) { issuer.issue(any(), any()) }
        assertTrue(repo.state.value is PrimaryGoogleAccountState.Unbound)
    }

    @Test
    fun `getAccessToken when Bound returns issuer token`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { store.load() } returns sampleAccount
        val expectedToken = GoogleAccessToken(
            token = "secret-access-token",
            scopes = scopes,
            expiresAt = Instant.parse("2026-05-16T21:00:00Z")
        )
        coEvery { issuer.issue(sampleAccount.email, scopes) } returns TokenIssueResult.Success(expectedToken)
        repo = buildRepo()

        val token = repo.getAccessToken(scopes)

        assertNotNull(token)
        assertEquals(expectedToken.token, token!!.token)
        assertTrue(repo.state.value is PrimaryGoogleAccountState.Bound)
    }

    @Test
    fun `getAccessToken on Failed flips state to NeedsResignIn`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { store.load() } returns sampleAccount
        coEvery { issuer.issue(sampleAccount.email, scopes) } returns TokenIssueResult.Failed
        repo = buildRepo()

        val token = repo.getAccessToken(scopes)

        assertNull(token)
        val state = repo.state.value
        assertTrue("expected NeedsResignIn, got $state", state is PrimaryGoogleAccountState.NeedsResignIn)
        assertEquals(
            NeedsResignInReason.TokenExpired,
            (state as PrimaryGoogleAccountState.NeedsResignIn).reason
        )
    }

    @Test
    fun `getAccessToken on AccountAbsent clears binding and emits Unbound`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { store.load() } returns sampleAccount
        coEvery { issuer.issue(sampleAccount.email, scopes) } returns TokenIssueResult.AccountAbsent
        repo = buildRepo()

        val token = repo.getAccessToken(scopes)

        assertNull(token)
        coVerify(exactly = 1) { store.clear() }
        assertTrue(repo.state.value is PrimaryGoogleAccountState.Unbound)
    }

    @Test
    fun `signOutPrimary clears issuer and store and emits Unbound`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { store.load() } returns sampleAccount
        repo = buildRepo()

        repo.signOutPrimary()

        coVerify(exactly = 1) { issuer.invalidate() }
        coVerify(exactly = 1) { store.clear() }
        assertTrue(repo.state.value is PrimaryGoogleAccountState.Unbound)
    }

    @Test
    fun `invalidateToken delegates to issuer`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { store.load() } returns null
        repo = buildRepo()

        repo.invalidateToken()

        coVerify(exactly = 1) { issuer.invalidate() }
    }
}
