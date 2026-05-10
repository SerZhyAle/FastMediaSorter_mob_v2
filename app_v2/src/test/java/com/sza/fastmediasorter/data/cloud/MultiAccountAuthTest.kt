package com.sza.fastmediasorter.data.cloud

import android.content.Context
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveCredentialsManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * P2-4 (A1-T14): Tests for multi-account credential switching and state machine transitions.
 *
 * Covers:
 * - State machine authenticateOrRestore with accountEmail hint (per-account)
 * - Multi-account switch: same provider, different accounts
 * - State isolation between providers
 * - Auth failure followed by successful re-auth for different account
 * - tryRestoreForAccount per-account credential lookup
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MultiAccountAuthTest {

    private val context: Context = mockk(relaxed = true)
    private lateinit var mockGoogleDriveClient: GoogleDriveRestClient
    private lateinit var mockOneDriveClient: OneDriveRestClient
    private lateinit var mockDropboxClient: DropboxClient
    private lateinit var stateMachine: CloudAuthStateMachine

    @Before
    fun setUp() {
        mockGoogleDriveClient = mockk(relaxed = true)
        mockOneDriveClient = mockk(relaxed = true)
        mockDropboxClient = mockk(relaxed = true)

        stateMachine = CloudAuthStateMachine(
            context = context,
            googleDriveClient = mockGoogleDriveClient,
            oneDriveClient = mockOneDriveClient,
            dropboxClient = mockDropboxClient
        )
    }

    // ── Per-account restore tests ───────────────────────────────────────────

    @Test
    fun `authenticateOrRestore with account hint uses tryRestoreForAccount`() = runTest {
        // Arrange
        val email = "personal@gmail.com"
        coEvery { mockGoogleDriveClient.tryRestoreForAccount(email) } returns true
        coEvery { mockGoogleDriveClient.getAccountEmail() } returns email

        // Act
        val result = stateMachine.authenticateOrRestore(CloudProvider.GOOGLE_DRIVE, email)

        // Assert
        assertTrue(result is CloudResult.Success)
        assertEquals(email, (result as CloudResult.Success).data)

        val state = stateMachine.stateOf(CloudProvider.GOOGLE_DRIVE).value
        assertTrue(state is CloudAuthStateMachine.AuthState.Authenticated)
        assertEquals(email, (state as CloudAuthStateMachine.AuthState.Authenticated).accountEmail)

        coVerify(exactly = 1) { mockGoogleDriveClient.tryRestoreForAccount(email) }
    }

    @Test
    fun `authenticateOrRestore without hint uses tryRestoreFromStorage`() = runTest {
        // Arrange
        coEvery { mockGoogleDriveClient.tryRestoreFromStorage() } returns true
        coEvery { mockGoogleDriveClient.getAccountEmail() } returns "default@gmail.com"

        // Act
        val result = stateMachine.authenticateOrRestore(CloudProvider.GOOGLE_DRIVE)

        // Assert
        assertTrue(result is CloudResult.Success)
        assertEquals("default@gmail.com", (result as CloudResult.Success).data)

        coVerify(exactly = 1) { mockGoogleDriveClient.tryRestoreFromStorage() }
        coVerify(exactly = 0) { mockGoogleDriveClient.tryRestoreForAccount(any()) }
    }

    // ── Multi-account switching ─────────────────────────────────────────────

    @Test
    fun `switching between accounts updates state correctly`() = runTest {
        // First account
        val email1 = "personal@gmail.com"
        coEvery { mockGoogleDriveClient.tryRestoreForAccount(email1) } returns true
        coEvery { mockGoogleDriveClient.getAccountEmail() } returns email1

        val result1 = stateMachine.authenticateOrRestore(CloudProvider.GOOGLE_DRIVE, email1)
        assertTrue(result1 is CloudResult.Success)
        assertEquals(email1, (result1 as CloudResult.Success).data)

        // Second account
        val email2 = "work@gmail.com"
        coEvery { mockGoogleDriveClient.tryRestoreForAccount(email2) } returns true
        coEvery { mockGoogleDriveClient.getAccountEmail() } returns email2

        val result2 = stateMachine.authenticateOrRestore(CloudProvider.GOOGLE_DRIVE, email2)
        assertTrue(result2 is CloudResult.Success)
        assertEquals(email2, (result2 as CloudResult.Success).data)

        // State should reflect latest account
        val state = stateMachine.stateOf(CloudProvider.GOOGLE_DRIVE).value
        assertTrue(state is CloudAuthStateMachine.AuthState.Authenticated)
        assertEquals(email2, (state as CloudAuthStateMachine.AuthState.Authenticated).accountEmail)
    }

    @Test
    fun `restore fails for unknown account, falls back to authenticate`() = runTest {
        // Arrange
        val unknownEmail = "unknown@gmail.com"
        coEvery { mockGoogleDriveClient.tryRestoreForAccount(unknownEmail) } returns false
        coEvery { mockGoogleDriveClient.authenticate() } returns AuthResult.Error("No interactive auth in test")

        // Act
        val result = stateMachine.authenticateOrRestore(CloudProvider.GOOGLE_DRIVE, unknownEmail)

        // Assert
        assertTrue(result is CloudResult.Error)
        val state = stateMachine.stateOf(CloudProvider.GOOGLE_DRIVE).value
        assertTrue(state is CloudAuthStateMachine.AuthState.AuthFailed)

        coVerify(exactly = 1) { mockGoogleDriveClient.tryRestoreForAccount(unknownEmail) }
        coVerify(exactly = 1) { mockGoogleDriveClient.authenticate() }
    }

    // ── State isolation between providers ───────────────────────────────────

    @Test
    fun `Google auth failure does not affect OneDrive state`() = runTest {
        // Arrange: fail Google, succeed OneDrive
        coEvery { mockGoogleDriveClient.tryRestoreFromStorage() } returns false
        coEvery { mockGoogleDriveClient.authenticate() } returns AuthResult.Error("GoogleSignIn unavailable")

        coEvery { mockOneDriveClient.initialize(any()) } returns true
        coEvery { mockOneDriveClient.getAccountEmail() } returns "user@outlook.com"

        // Act
        stateMachine.authenticateOrRestore(CloudProvider.GOOGLE_DRIVE)
        stateMachine.authenticateOrRestore(CloudProvider.ONEDRIVE)

        // Assert
        val googleState = stateMachine.stateOf(CloudProvider.GOOGLE_DRIVE).value
        val oneDriveState = stateMachine.stateOf(CloudProvider.ONEDRIVE).value

        assertTrue(googleState is CloudAuthStateMachine.AuthState.AuthFailed)
        assertTrue(oneDriveState is CloudAuthStateMachine.AuthState.Authenticated)
        assertEquals("user@outlook.com", (oneDriveState as CloudAuthStateMachine.AuthState.Authenticated).accountEmail)
    }

    @Test
    fun `signout resets single provider, others unaffected`() = runTest {
        // Arrange: authenticate both
        coEvery { mockGoogleDriveClient.tryRestoreFromStorage() } returns true
        coEvery { mockGoogleDriveClient.getAccountEmail() } returns "guser@gmail.com"
        coEvery { mockOneDriveClient.initialize(any()) } returns true
        coEvery { mockOneDriveClient.getAccountEmail() } returns "msuser@outlook.com"

        stateMachine.authenticateOrRestore(CloudProvider.GOOGLE_DRIVE)
        stateMachine.authenticateOrRestore(CloudProvider.ONEDRIVE)

        // Act: sign out Google only
        stateMachine.onSignedOut(CloudProvider.GOOGLE_DRIVE)

        // Assert
        assertEquals(
            CloudAuthStateMachine.AuthState.Idle,
            stateMachine.stateOf(CloudProvider.GOOGLE_DRIVE).value
        )
        assertTrue(stateMachine.stateOf(CloudProvider.ONEDRIVE).value is CloudAuthStateMachine.AuthState.Authenticated)
    }

    // ── Auth error + recovery ───────────────────────────────────────────────

    @Test
    fun `auth error followed by successful re-auth transitions through all states`() = runTest {
        // Arrange
        coEvery { mockGoogleDriveClient.tryRestoreForAccount("user@gmail.com") } returns true
        coEvery { mockGoogleDriveClient.getAccountEmail() } returns "user@gmail.com"

        // Step 1: Initial auth succeeds
        val result1 = stateMachine.authenticateOrRestore(CloudProvider.GOOGLE_DRIVE, "user@gmail.com")
        assertTrue(result1 is CloudResult.Success)
        assertTrue(stateMachine.stateOf(CloudProvider.GOOGLE_DRIVE).value is CloudAuthStateMachine.AuthState.Authenticated)

        // Step 2: External auth error (e.g., 401 detected in data layer)
        stateMachine.onAuthError(CloudProvider.GOOGLE_DRIVE, "Token expired")
        assertTrue(stateMachine.stateOf(CloudProvider.GOOGLE_DRIVE).value is CloudAuthStateMachine.AuthState.AuthFailed)

        // Step 3: Re-auth succeeds
        val result2 = stateMachine.authenticateOrRestore(CloudProvider.GOOGLE_DRIVE, "user@gmail.com")
        assertTrue(result2 is CloudResult.Success)

        val finalState = stateMachine.stateOf(CloudProvider.GOOGLE_DRIVE).value
        assertTrue(finalState is CloudAuthStateMachine.AuthState.Authenticated)
        assertEquals("user@gmail.com", (finalState as CloudAuthStateMachine.AuthState.Authenticated).accountEmail)
    }

    // ── Dropbox multi-account ───────────────────────────────────────────────

    @Test
    fun `Dropbox per-account restore works same as Google Drive`() = runTest {
        // Arrange
        val email = "dbuser@email.com"
        coEvery { mockDropboxClient.tryRestoreForAccount(email) } returns true
        coEvery { mockDropboxClient.getAccountEmail() } returns email

        // Act
        val result = stateMachine.authenticateOrRestore(CloudProvider.DROPBOX, email)

        // Assert
        assertTrue(result is CloudResult.Success)
        assertEquals(email, (result as CloudResult.Success).data)

        val state = stateMachine.stateOf(CloudProvider.DROPBOX).value
        assertTrue(state is CloudAuthStateMachine.AuthState.Authenticated)
        assertEquals(email, (state as CloudAuthStateMachine.AuthState.Authenticated).accountEmail)
    }

    // ── Exception handling ──────────────────────────────────────────────────

    @Test
    fun `exception during restore transitions to AuthFailed`() = runTest {
        // Arrange
        coEvery { mockGoogleDriveClient.tryRestoreForAccount(any()) } throws RuntimeException("Crypto error")

        // Act
        val result = stateMachine.authenticateOrRestore(CloudProvider.GOOGLE_DRIVE, "test@gmail.com")

        // Assert
        assertTrue(result is CloudResult.Error)
        assertTrue((result as CloudResult.Error).message.contains("Crypto error"))

        val state = stateMachine.stateOf(CloudProvider.GOOGLE_DRIVE).value
        assertTrue(state is CloudAuthStateMachine.AuthState.AuthFailed)
    }
}
