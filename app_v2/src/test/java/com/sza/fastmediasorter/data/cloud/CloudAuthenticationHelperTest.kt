package com.sza.fastmediasorter.data.cloud

import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * P2-4 (A1-T14): Tests for CloudAuthenticationHelper.executeWithAutoReauth().
 *
 * Covers:
 * - Successful operation passes through without re-auth
 * - Auth error triggers re-auth via state machine and retries once
 * - Non-auth error passes through without re-auth
 * - getCloudClient returns null when auth state machine fails
 * - Re-auth failure returns null
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CloudAuthenticationHelperTest {

    private lateinit var mockGoogleDriveClient: GoogleDriveRestClient
    private lateinit var mockDropboxClient: DropboxClient
    private lateinit var mockOneDriveClient: OneDriveRestClient
    private lateinit var mockStateMachine: CloudAuthStateMachine
    private lateinit var helper: CloudAuthenticationHelper

    @Before
    fun setUp() {
        mockGoogleDriveClient = mockk(relaxed = true)
        mockDropboxClient = mockk(relaxed = true)
        mockOneDriveClient = mockk(relaxed = true)
        mockStateMachine = mockk(relaxed = true)

        helper = CloudAuthenticationHelper(
            googleDriveClient = mockGoogleDriveClient,
            dropboxClient = mockDropboxClient,
            oneDriveClient = mockOneDriveClient,
            authStateMachine = mockStateMachine
        )
    }

    // ── executeWithAutoReauth Tests ─────────────────────────────────────────

    @Test
    fun `successful operation returns result without re-auth attempt`() = runTest {
        // Arrange
        every { mockGoogleDriveClient.isAuthenticated() } returns true
        val expected = CloudResult.Success(listOf("file1", "file2"))

        // Act
        val result = helper.executeWithAutoReauth(CloudProvider.GOOGLE_DRIVE) { _ ->
            expected
        }

        // Assert
        assertNotNull(result)
        assertTrue(result is CloudResult.Success)
        assertEquals(listOf("file1", "file2"), (result as CloudResult.Success).data)

        // State machine should not be called for re-auth
        coVerify(exactly = 0) { mockStateMachine.onAuthError(any(), any()) }
    }

    @Test
    fun `auth error triggers re-auth and retries operation once`() = runTest {
        // Arrange
        every { mockGoogleDriveClient.isAuthenticated() } returns true
        coEvery { mockStateMachine.authenticateOrRestore(CloudProvider.GOOGLE_DRIVE, any()) } returns
            CloudResult.Success("test@gmail.com")

        var callCount = 0
        val operation: suspend (CloudStorageClient) -> CloudResult<String> = { _ ->
            callCount++
            if (callCount == 1) {
                CloudResult.Error("Not authenticated")
            } else {
                CloudResult.Success("data-after-reauth")
            }
        }

        // Act
        val result = helper.executeWithAutoReauth(CloudProvider.GOOGLE_DRIVE, operation)

        // Assert
        assertNotNull(result)
        assertTrue("Expected Success but got $result", result is CloudResult.Success)
        assertEquals("data-after-reauth", (result as CloudResult.Success).data)
        assertEquals(2, callCount)

        // Verify re-auth flow
        coVerify(exactly = 1) { mockStateMachine.onAuthError(CloudProvider.GOOGLE_DRIVE, "Not authenticated") }
        coVerify(exactly = 1) { mockStateMachine.authenticateOrRestore(CloudProvider.GOOGLE_DRIVE, any()) }
    }

    @Test
    fun `non-auth error passes through without re-auth`() = runTest {
        // Arrange
        every { mockGoogleDriveClient.isAuthenticated() } returns true
        val error = CloudResult.Error("Network timeout")

        // Act
        val result = helper.executeWithAutoReauth(CloudProvider.GOOGLE_DRIVE) { _ -> error }

        // Assert
        assertNotNull(result)
        assertTrue(result is CloudResult.Error)
        assertEquals("Network timeout", (result as CloudResult.Error).message)

        // No re-auth triggered for non-auth errors
        coVerify(exactly = 0) { mockStateMachine.onAuthError(any(), any()) }
    }

    @Test
    fun `re-auth failure returns null`() = runTest {
        // Arrange
        every { mockGoogleDriveClient.isAuthenticated() } returns true
        coEvery { mockStateMachine.authenticateOrRestore(CloudProvider.GOOGLE_DRIVE, any()) } returns
            CloudResult.Error("Re-auth failed")

        // Act
        val result = helper.executeWithAutoReauth(CloudProvider.GOOGLE_DRIVE) { _ ->
            CloudResult.Error("Not authenticated")
        }

        // Assert
        assertNull("Expected null when re-auth fails, got $result", result)
    }

    @Test
    fun `returns null when client not authenticated and restore fails`() = runTest {
        // Arrange
        every { mockGoogleDriveClient.isAuthenticated() } returns false
        coEvery { mockStateMachine.authenticateOrRestore(CloudProvider.GOOGLE_DRIVE, any()) } returns
            CloudResult.Error("No stored credentials")

        // Act
        val result = helper.executeWithAutoReauth(CloudProvider.GOOGLE_DRIVE) { _ ->
            CloudResult.Success("should-not-reach")
        }

        // Assert
        assertNull("Expected null when client cannot authenticate", result)
    }

    // ── getCloudClientResult Tests ──────────────────────────────────────────

    @Test
    fun `getCloudClientResult returns Success when already authenticated`() = runTest {
        // Arrange
        every { mockGoogleDriveClient.isAuthenticated() } returns true

        // Act
        val result = helper.getCloudClientResult(CloudProvider.GOOGLE_DRIVE)

        // Assert
        assertTrue(result is CloudAuthenticationHelper.CloudClientResult.Success)
        // State machine should not be called - already authenticated
        coVerify(exactly = 0) { mockStateMachine.authenticateOrRestore(any(), any()) }
    }

    @Test
    fun `getCloudClientResult attempts restore when not authenticated`() = runTest {
        // Arrange
        every { mockGoogleDriveClient.isAuthenticated() } returns false
        coEvery { mockStateMachine.authenticateOrRestore(CloudProvider.GOOGLE_DRIVE, any()) } returns
            CloudResult.Success("test@gmail.com")

        // Act
        val result = helper.getCloudClientResult(CloudProvider.GOOGLE_DRIVE)

        // Assert
        assertTrue(result is CloudAuthenticationHelper.CloudClientResult.Success)
        coVerify(exactly = 1) { mockStateMachine.authenticateOrRestore(CloudProvider.GOOGLE_DRIVE, any()) }
    }

    @Test
    fun `getCloudClientResult returns AuthRequired when restore fails`() = runTest {
        // Arrange
        every { mockGoogleDriveClient.isAuthenticated() } returns false
        coEvery { mockStateMachine.authenticateOrRestore(CloudProvider.GOOGLE_DRIVE, any()) } returns
            CloudResult.Error("Cannot restore")

        // Act
        val result = helper.getCloudClientResult(CloudProvider.GOOGLE_DRIVE)

        // Assert
        assertTrue(result is CloudAuthenticationHelper.CloudClientResult.AuthRequired)
    }

    // ── Multi-provider coverage ─────────────────────────────────────────────

    @Test
    fun `getCloudClient routes to correct provider client`() = runTest {
        // Google Drive
        every { mockGoogleDriveClient.isAuthenticated() } returns true
        val gdClient = helper.getCloudClient(CloudProvider.GOOGLE_DRIVE)
        assertSame(mockGoogleDriveClient, gdClient)

        // OneDrive
        every { mockOneDriveClient.isAuthenticated() } returns true
        val odClient = helper.getCloudClient(CloudProvider.ONEDRIVE)
        assertSame(mockOneDriveClient, odClient)

        // Dropbox
        every { mockDropboxClient.isAuthenticated() } returns true
        val dbClient = helper.getCloudClient(CloudProvider.DROPBOX)
        assertSame(mockDropboxClient, dbClient)
    }
}
