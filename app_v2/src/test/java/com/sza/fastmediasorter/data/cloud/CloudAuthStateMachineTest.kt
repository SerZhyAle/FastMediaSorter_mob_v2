package com.sza.fastmediasorter.data.cloud

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudAuthStateMachineTest {

    private val context: Context = mockk(relaxed = true)
    private lateinit var googleDriveClient: GoogleDriveRestClient
    private lateinit var oneDriveClient: OneDriveRestClient
    private lateinit var dropboxClient: DropboxClient
    private lateinit var stateMachine: CloudAuthStateMachine

    @Before
    fun setUp() {
        googleDriveClient = mockk()
        oneDriveClient = mockk()
        dropboxClient = mockk()

        stateMachine = CloudAuthStateMachine(
            context,
            googleDriveClient,
            oneDriveClient,
            dropboxClient
        )
    }

    @Test
    fun `initial state is Idle for all providers`() = runTest {
        assertEquals(CloudAuthStateMachine.AuthState.Idle, stateMachine.stateOf(CloudProvider.GOOGLE_DRIVE).value)
        assertEquals(CloudAuthStateMachine.AuthState.Idle, stateMachine.stateOf(CloudProvider.ONEDRIVE).value)
        assertEquals(CloudAuthStateMachine.AuthState.Idle, stateMachine.stateOf(CloudProvider.DROPBOX).value)
    }

    @Test
    fun `authenticateOrRestore updates state to Authenticated on success`() = runTest {
        // Arrange
        val accountEmail = "test@gmail.com"
        coEvery { googleDriveClient.tryRestoreForAccount(accountEmail) } returns true
        coEvery { googleDriveClient.getAccountEmail() } returns accountEmail

        // Act
        val result = stateMachine.authenticateOrRestore(CloudProvider.GOOGLE_DRIVE, accountEmail)

        // Assert
        assertTrue(result is CloudResult.Success)
        assertEquals(accountEmail, (result as CloudResult.Success).data)
        
        val currentState = stateMachine.stateOf(CloudProvider.GOOGLE_DRIVE).value
        assertTrue(currentState is CloudAuthStateMachine.AuthState.Authenticated)
        assertEquals(accountEmail, (currentState as CloudAuthStateMachine.AuthState.Authenticated).accountEmail)
        
        coVerify(exactly = 1) { googleDriveClient.tryRestoreForAccount(accountEmail) }
    }

    @Test
    fun `onAuthError updates state to AuthFailed`() = runTest {
        // Arrange
        val errorMessage = "Token expired"

        // Act
        stateMachine.onAuthError(CloudProvider.GOOGLE_DRIVE, errorMessage)

        // Assert
        val currentState = stateMachine.stateOf(CloudProvider.GOOGLE_DRIVE).value
        assertTrue(currentState is CloudAuthStateMachine.AuthState.AuthFailed)
        assertEquals(errorMessage, (currentState as CloudAuthStateMachine.AuthState.AuthFailed).message)
    }

    @Test
    fun `onSignedOut resets state to Idle`() = runTest {
        // Arrange
        stateMachine.onAuthError(CloudProvider.GOOGLE_DRIVE, "Some Error")

        // Act
        stateMachine.onSignedOut(CloudProvider.GOOGLE_DRIVE)

        // Assert
        val currentState = stateMachine.stateOf(CloudProvider.GOOGLE_DRIVE).value
        assertEquals(CloudAuthStateMachine.AuthState.Idle, currentState)
    }
}
