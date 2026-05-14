package com.sza.fastmediasorter.data.cloud

import android.content.Context
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveCredentialsManager
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveHttpClient
import com.sza.fastmediasorter.data.local.db.PendingRevocationDao
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * P2-4 (A1-T13): Tests for Google Drive token expiry, refresh and retry flow.
 *
 * Note: listFiles() calls ensureTokenFresh() → silentSignIn() → GoogleSignIn API,
 * which is unavailable in pure JVM unit tests. We use testConnection() instead
 * (no proactive refresh) and reflection for shouldRefreshToken.
 *
 * Covers:
 * - testConnection 401 retry (exhausted)
 * - testConnection 200 success path
 * - shouldRefreshToken threshold logic (via reflection)
 * - isAuthenticated state tracking
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoogleDriveTokenRefreshTest {

    private lateinit var mockContext: Context
    private lateinit var mockCredentialsManager: GoogleDriveCredentialsManager
    private lateinit var mockHttpClient: GoogleDriveHttpClient
    private lateinit var mockPendingRevocationDao: PendingRevocationDao
    private lateinit var mockCredentialsRepo: NetworkCredentialsRepository
    private lateinit var client: GoogleDriveRestClient

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockCredentialsManager = mockk(relaxed = true)
        mockHttpClient = mockk(relaxed = true)
        mockPendingRevocationDao = mockk(relaxed = true)
        mockCredentialsRepo = mockk(relaxed = true)

        client = GoogleDriveRestClient(
            context = mockContext,
            credentialsManager = mockCredentialsManager,
            httpClient = mockHttpClient,
            pendingRevocationDao = mockPendingRevocationDao,
            networkCredentialsRepository = mockCredentialsRepo,
            reachabilityGate = mockk(relaxed = true),
            lifecycleBootstrapper = dagger.Lazy { mockk(relaxed = true) },
        )
    }

    // ── testConnection: 200 OK ──────────────────────────────────────────────

    @Test
    fun `testConnection succeeds without retry on 200`() = runTest {
        // Arrange
        setClientAuthenticated("test@gmail.com", "valid-token")

        coEvery {
            mockHttpClient.makeAuthenticatedRequest(any(), eq("GET"), eq("valid-token"), any())
        } returns GoogleDriveHttpClient.ApiResponse(
            isSuccess = true,
            data = """{"user":{"emailAddress":"test@gmail.com"}}""",
            errorMessage = null,
            httpCode = 200
        )

        // Act
        val result = client.testConnection()

        // Assert
        assertTrue("Expected Success, got $result", result is CloudResult.Success)
        coVerify(exactly = 1) { mockHttpClient.makeAuthenticatedRequest(any(), any(), any(), any()) }
    }

    // ── testConnection: 401 exhausted ───────────────────────────────────────

    @Test
    fun `testConnection returns error after exhausted 401 retries`() = runTest {
        // Arrange
        setClientAuthenticated("test@gmail.com", "expired-token")

        // All calls return 401
        coEvery {
            mockHttpClient.makeAuthenticatedRequest(any(), any(), any(), any())
        } returns GoogleDriveHttpClient.ApiResponse(
            isSuccess = false,
            data = null,
            errorMessage = "Unauthorized",
            httpCode = 401
        )

        // Act
        val result = client.testConnection()

        // Assert
        assertTrue("Expected Error, got $result", result is CloudResult.Error)
        val errorMsg = (result as CloudResult.Error).message
        assertTrue(
            "Error should reflect auth failure, got: $errorMsg",
            errorMsg.contains("auth", ignoreCase = true) ||
            errorMsg.contains("401") ||
            errorMsg.contains("failed", ignoreCase = true) ||
            errorMsg.contains("Unauthorized", ignoreCase = true)
        )
    }

    // ── testConnection: not authenticated ───────────────────────────────────

    @Test
    fun `testConnection returns error when not authenticated`() = runTest {
        // client has no accessToken set (default null)

        // Act
        val result = client.testConnection()

        // Assert
        assertTrue("Expected Error, got $result", result is CloudResult.Error)
        assertTrue((result as CloudResult.Error).message.contains("Not authenticated"))
        // httpClient should never be called
        coVerify(exactly = 0) { mockHttpClient.makeAuthenticatedRequest(any(), any(), any(), any()) }
    }

    // ── shouldRefreshToken (reflection) ─────────────────────────────────────

    @Test
    fun `shouldRefreshToken returns false when timestamp is zero`() {
        // tokenTimestamp defaults to 0L
        val result = invokeShouldRefreshToken()
        assertFalse("Should not refresh when timestamp is 0", result)
    }

    @Test
    fun `shouldRefreshToken returns true when token older than 50 minutes`() {
        setTokenTimestamp(System.currentTimeMillis() - 51 * 60 * 1000L)
        assertTrue("Should refresh token older than 50 min", invokeShouldRefreshToken())
    }

    @Test
    fun `shouldRefreshToken returns false when token younger than 50 minutes`() {
        setTokenTimestamp(System.currentTimeMillis() - 10 * 60 * 1000L)
        assertFalse("Should not refresh token younger than 50 min", invokeShouldRefreshToken())
    }

    @Test
    fun `shouldRefreshToken returns true at exact threshold boundary`() {
        val threshold = 50 * 60 * 1000L
        setTokenTimestamp(System.currentTimeMillis() - threshold - 1)
        assertTrue("Should refresh at threshold boundary", invokeShouldRefreshToken())
    }

    // ── isAuthenticated state ───────────────────────────────────────────────

    @Test
    fun `isAuthenticated returns false by default`() {
        assertFalse(client.isAuthenticated())
    }

    @Test
    fun `isAuthenticated returns true after setting accessToken`() {
        setField("accessToken", "some-token")
        assertTrue(client.isAuthenticated())
    }

    @Test
    fun `getAccountEmail returns null by default`() {
        assertNull(client.getAccountEmail())
    }

    @Test
    fun `getAccountEmail returns email after authentication`() {
        setClientAuthenticated("user@gmail.com", "token")
        assertEquals("user@gmail.com", client.getAccountEmail())
    }

    // ── Helper Methods ──────────────────────────────────────────────────────

    private fun setClientAuthenticated(email: String, token: String) {
        setField("accessToken", token)
        setField("accountEmail", email)
        setField("tokenTimestamp", System.currentTimeMillis())
    }

    private fun setTokenTimestamp(timestamp: Long) {
        setField("tokenTimestamp", timestamp)
    }

    private fun setField(fieldName: String, value: Any?) {
        val field = GoogleDriveRestClient::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(client, value)
    }

    private fun invokeShouldRefreshToken(): Boolean {
        val method = GoogleDriveRestClient::class.java.getDeclaredMethod("shouldRefreshToken")
        method.isAccessible = true
        return method.invoke(client) as Boolean
    }
}
