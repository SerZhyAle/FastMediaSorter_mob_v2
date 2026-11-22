package com.sza.fastmediasorter.domain.usecase.ticket

import android.content.Context
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.identity.GoogleScope
import com.sza.fastmediasorter.domain.identity.IdentityFailureReason
import com.sza.fastmediasorter.domain.identity.IdentitySignInResult
import com.sza.fastmediasorter.domain.identity.NeedsResignInReason
import com.sza.fastmediasorter.domain.identity.PrimaryGoogleAccount
import com.sza.fastmediasorter.domain.identity.PrimaryGoogleAccountState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** S4765, S4814: a bound account takes the grant alone, any other state the full sign-in with the chooser forced. */
class GrantTicketDriveAccessUseCaseTest {

    private val context = mockk<Context>()
    private val driveScopes = setOf(GoogleScope.DRIVE, GoogleScope.DRIVE_READONLY)
    private val signInScopes = driveScopes + GoogleScope.EMAIL
    private val account = PrimaryGoogleAccount("owner@example.com", null, null, driveScopes, Instant.EPOCH)
    private val identity = mockk<GoogleIdentityRepository>()

    private fun useCase(state: PrimaryGoogleAccountState): GrantTicketDriveAccessUseCase {
        every { identity.state } returns MutableStateFlow(state)
        return GrantTicketDriveAccessUseCase(identity)
    }

    @Test
    fun `a bound account asks the Drive scopes alone`() = runTest {
        val granted = IdentitySignInResult.Success(account)
        coEvery { identity.grantScopes(context, driveScopes) } returns granted

        val result = useCase(PrimaryGoogleAccountState.Bound(account))(context, driveScopes, signInScopes)

        assertEquals(granted, result)
        coVerify(exactly = 0) { identity.signInPrimary(any(), any(), any()) }
    }

    @Test
    fun `an unbound phone forces the account chooser and a cancel comes back as a cancel`() = runTest {
        coEvery { identity.signInPrimary(context, signInScopes, true) } returns IdentitySignInResult.Cancelled

        val result = useCase(PrimaryGoogleAccountState.Unbound)(context, driveScopes, signInScopes)

        assertEquals(IdentitySignInResult.Cancelled, result)
        coVerify(exactly = 0) { identity.grantScopes(any(), any()) }
    }

    @Test
    fun `a phone that needs a re-sign-in forces the account chooser too`() = runTest {
        val signedIn = IdentitySignInResult.Success(account)
        coEvery { identity.signInPrimary(context, signInScopes, true) } returns signedIn

        val state = PrimaryGoogleAccountState.NeedsResignIn(account, NeedsResignInReason.TokenExpired)
        val result = useCase(state)(context, driveScopes, signInScopes)

        assertEquals(signedIn, result)
        coVerify(exactly = 0) { identity.grantScopes(any(), any()) }
    }

    @Test
    fun `a failure is passed through untouched`() = runTest {
        val failed = IdentitySignInResult.Failed(IdentityFailureReason.UnknownError)
        coEvery { identity.signInPrimary(any(), any(), any()) } returns failed

        val result = useCase(PrimaryGoogleAccountState.Unbound)(context, driveScopes, signInScopes)

        assertEquals(failed, result)
    }
}
