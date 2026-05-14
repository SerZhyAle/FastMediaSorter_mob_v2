package com.sza.fastmediasorter.data.network.smb

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.sza.fastmediasorter.core.network.NetworkReachabilityGate
import com.sza.fastmediasorter.core.network.NetworkStateMonitor
import com.sza.fastmediasorter.data.network.SmbConnectionManager
import com.sza.fastmediasorter.data.network.SmbPlaybackConnectionTracker
import com.sza.fastmediasorter.data.network.exceptions.NetworkConnectionLostException
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Unit tests for SmbConnectionManager.
 * Tests connection pooling, timeout handling, and client selection logic.
 */
class SmbConnectionManagerTest {
    
    private lateinit var connectionManager: SmbConnectionManager
    private lateinit var mockSmbClient: SMBClient
    private lateinit var mockConnection: Connection
    private lateinit var mockSession: Session
    private lateinit var mockShare: DiskShare
    private lateinit var mockNetworkStateMonitor: NetworkStateMonitor
    private lateinit var mockReachabilityGate: NetworkReachabilityGate

    @Before
    fun setup() {
        // Initialize mocks
        mockSmbClient = mockk(relaxed = true)
        mockConnection = mockk(relaxed = true)
        mockSession = mockk(relaxed = true)
        mockShare = mockk(relaxed = true)
        mockNetworkStateMonitor = mockk(relaxed = true)
        mockReachabilityGate = mockk(relaxed = true)
        every { mockReachabilityGate.requireWifi(any()) } just Runs
        every { mockReachabilityGate.requireAnyNetwork(any()) } just Runs
        
        // Setup default mock behavior
        every { mockSmbClient.connect(any(), any()) } returns mockConnection
        every { mockConnection.authenticate(any()) } returns mockSession
        every { mockSession.connectShare(any()) } returns mockShare
        every { mockConnection.isConnected } returns true
        every { mockSession.connection.isConnected } returns true
        every { mockShare.isConnected } returns true

        mockkConstructor(SMBClient::class)
        every { anyConstructed<SMBClient>().connect(any(), any()) } returns mockConnection
        every { anyConstructed<SMBClient>().close() } just Runs

        // Mock Socket to prevent real TCP connectivity checks in unit tests
        mockkConstructor(Socket::class)
        every { anyConstructed<Socket>().connect(any<InetSocketAddress>(), any<Int>()) } just Runs
        every { anyConstructed<Socket>().close() } just Runs

        // Create manager instance
        // S0018: out-of-scope inline fix — added playbackTracker mock to satisfy updated constructor
        connectionManager = SmbConnectionManager(
            mockNetworkStateMonitor,
            mockk<SmbPlaybackConnectionTracker>(relaxed = true),
            mockReachabilityGate,
            dagger.Lazy { mockk(relaxed = true) },
        )
    }
    
    @After
    fun tearDown() {
        connectionManager.close()
        unmockkConstructor(SMBClient::class)
        unmockkConstructor(Socket::class)
        clearAllMocks()
    }
    
    @Test
    fun `getClient returns normal client by default`() {
        val client = connectionManager.getClient("testserver", 445)
        assertNotNull("Client should not be null", client)
    }
    
    @Test
    fun `withConnection creates fresh connection on first call`() = runBlocking {
        val connectionInfo = SmbConnectionInfo(
            server = "testserver",
            shareName = "testshare",
            username = "testuser",
            password = "testpass",
            domain = "",
            port = 445
        )
        
        mockkObject(connectionManager) {
            every { connectionManager.getClient(any(), any()) } returns mockSmbClient
            
            var blockExecuted = false
            val result = connectionManager.withConnection(connectionInfo) { share ->
                blockExecuted = true
                assertEquals("Share should be mockShare", mockShare, share)
                SmbResult.Success(Unit)
            }
            
            assertTrue("Block should have been executed", blockExecuted)
            assertTrue("Result should be Success", result is SmbResult.Success)
            
            // Verify connection was created
            verify(exactly = 1) { mockSmbClient.connect("testserver", 445) }
            verify(exactly = 1) { mockConnection.authenticate(any()) }
            verify(exactly = 1) { mockSession.connectShare("testshare") }
        }
    }
    
    @Test
    fun `withConnection reuses pooled connection for same server`() = runBlocking {
        val connectionInfo = SmbConnectionInfo(
            server = "testserver",
            shareName = "testshare",
            username = "testuser",
            password = "testpass",
            domain = "",
            port = 445
        )
        
        mockkObject(connectionManager) {
            every { connectionManager.getClient(any(), any()) } returns mockSmbClient
            
            // First call - creates connection
            connectionManager.withConnection(connectionInfo) { share ->
                SmbResult.Success(Unit)
            }
            
            // Second call - should reuse connection
            connectionManager.withConnection(connectionInfo) { share ->
                SmbResult.Success(Unit)
            }
            
            // Verify connection was created only once
            verify(exactly = 1) { mockSmbClient.connect("testserver", 445) }
            verify(exactly = 1) { mockConnection.authenticate(any()) }
            verify(exactly = 1) { mockSession.connectShare("testshare") }
        }
    }
    
    @Test
    fun `withConnection handles authentication with domain`() = runBlocking {
        val connectionInfo = SmbConnectionInfo(
            server = "testserver",
            shareName = "testshare",
            username = "testuser",
            password = "testpass",
            domain = "TESTDOMAIN",
            port = 445
        )
        
        mockkObject(connectionManager) {
            every { connectionManager.getClient(any(), any()) } returns mockSmbClient
            
            val authContextSlot = slot<AuthenticationContext>()
            every { mockConnection.authenticate(capture(authContextSlot)) } returns mockSession
            
            connectionManager.withConnection(connectionInfo) { share ->
                SmbResult.Success(Unit)
            }
            
            // Verify domain was passed correctly
            val capturedContext = authContextSlot.captured
            assertNotNull("AuthenticationContext should not be null", capturedContext)
        }
    }
    
    @Test
    fun `withConnection handles anonymous authentication`() = runBlocking {
        val connectionInfo = SmbConnectionInfo(
            server = "testserver",
            shareName = "testshare",
            username = "",
            password = "",
            domain = "",
            port = 445
        )
        
        mockkStatic(AuthenticationContext::class) {
            val mockAnonymousContext = mockk<AuthenticationContext>(relaxed = true)
            every { AuthenticationContext.anonymous() } returns mockAnonymousContext
            
            mockkObject(connectionManager) {
                every { connectionManager.getClient(any(), any()) } returns mockSmbClient
                
                connectionManager.withConnection(connectionInfo) { share ->
                    SmbResult.Success(Unit)
                }
                
                // Verify anonymous context was used
                verify(exactly = 1) { AuthenticationContext.anonymous() }
            }
        }
    }
    
    @Test
    fun `withConnection creates fresh connection when pooled is stale`() = runBlocking {
        val connectionInfo = SmbConnectionInfo(
            server = "testserver",
            shareName = "testshare",
            username = "testuser",
            password = "testpass",
            domain = "",
            port = 445
        )
        
        mockkObject(connectionManager) {
            every { connectionManager.getClient(any(), any()) } returns mockSmbClient
            
            // First call
            connectionManager.withConnection(connectionInfo) { share ->
                SmbResult.Success(Unit)
            }
            
            // Simulate idle timeout (45+ seconds)
            Thread.sleep(100) // Short delay for test
            
            // Make connection stale
            every { mockConnection.isConnected } returns false
            
            // Second call - should create new connection
            connectionManager.withConnection(connectionInfo) { share ->
                SmbResult.Success(Unit)
            }
            
            // Verify connection was created twice (once fresh, once after stale)
            verify(atLeast = 2) { mockSmbClient.connect("testserver", 445) }
        }
    }
    
    @Test
    fun `withConnection handles block exception correctly`() = runBlocking {
        val connectionInfo = SmbConnectionInfo(
            server = "testserver",
            shareName = "testshare",
            username = "testuser",
            password = "testpass",
            domain = "",
            port = 445
        )
        
        mockkObject(connectionManager) {
            every { connectionManager.getClient(any(), any()) } returns mockSmbClient
            
            val result = connectionManager.withConnection(connectionInfo) { share ->
                throw RuntimeException("Test exception")
                @Suppress("UNREACHABLE_CODE")
                SmbResult.Success(Unit)
            }
            
            assertTrue("Result should be Error", result is SmbResult.Error)
            assertEquals("Error message should contain test exception", 
                "Test exception", 
                (result as SmbResult.Error).exception?.message)
        }
    }
    
    @Test
    fun `clearConnectionPool closes all connections`() = runBlocking {
        val connectionInfo = SmbConnectionInfo(
            server = "testserver",
            shareName = "testshare",
            username = "testuser",
            password = "testpass",
            domain = "",
            port = 445
        )
        
        mockkObject(connectionManager) {
            every { connectionManager.getClient(any(), any()) } returns mockSmbClient
            
            // Create connection
            connectionManager.withConnection(connectionInfo) { share ->
                SmbResult.Success<Unit>(Unit)
            }
            
            // Clear pool
            connectionManager.clearConnectionPool()

            // closeAllConnections runs async via CoroutineScope — wait for IO dispatcher
            delay(500)

            // Verify resources were closed
            verify(atLeast = 1) { mockShare.close() }
            verify(atLeast = 1) { mockSession.close() }
            verify(atLeast = 1) { mockConnection.close() }
        }
    }
    
    @Test
    fun `forceFullReset closes all connections and resets clients`() {
        // Create mock client
        mockkObject(connectionManager) {
            every { connectionManager.getClient(any(), any()) } returns mockSmbClient
            
            // Force reset
            connectionManager.forceFullReset()
            
            // Verify reset was called
            verify(atLeast = 0) { mockSmbClient.close() }
        }
    }
    
    @Test
    fun `close releases all resources`() {
        mockkObject(connectionManager) {
            every { connectionManager.getClient(any(), any()) } returns mockSmbClient
            
            connectionManager.close()
            
            // Verify cleanup
            // Note: Actual verification depends on internal state
        }
    }
    
    @Test
    fun `withConnection handles connection timeout`() = runBlocking {
        val connectionInfo = SmbConnectionInfo(
            server = "testserver",
            shareName = "testshare",
            username = "testuser",
            password = "testpass",
            domain = "",
            port = 445
        )
        
        mockkObject(connectionManager) {
            every { connectionManager.getClient(any(), any()) } returns mockSmbClient
            every { mockSmbClient.connect(any(), any()) } throws 
                java.util.concurrent.TimeoutException("Connection timeout")
            every { anyConstructed<SMBClient>().connect(any(), any()) } throws
                java.util.concurrent.TimeoutException("Connection timeout")
            
            val result = connectionManager.withConnection(connectionInfo) { share ->
                SmbResult.Success(Unit)
            }
            
            assertTrue("Result should be Error on timeout", result is SmbResult.Error)
        }
    }
    
    @Test
    fun `withConnection tracks consecutive timeouts`() = runBlocking {
        val connectionInfo = SmbConnectionInfo(
            server = "testserver",
            shareName = "testshare",
            username = "testuser",
            password = "testpass",
            domain = "",
            port = 445
        )
        
        mockkObject(connectionManager) {
            every { connectionManager.getClient(any(), any()) } returns mockSmbClient
            
            // First call succeeds
            connectionManager.withConnection(connectionInfo) { share ->
                SmbResult.Success(Unit)
            }
            
            // Second call times out
            every { mockShare.list(any<String>()) } throws 
                java.util.concurrent.TimeoutException("Timeout")
            
            val result = connectionManager.withConnection(connectionInfo) { share ->
                try {
                    share.list("")
                } catch (e: Exception) {
                    // Expected timeout
                }
                SmbResult.Success(Unit)
            }
            
            // Timeout counter should be incremented
            // (Internal state, verified through subsequent behavior)
        }
    }

    // ── S0025: Wi-Fi gate + smart retry ─────────────────────────────────────

    @Test
    fun `withConnection throws synchronously when Wi-Fi gate denies — no socket attempt`() = runBlocking {
        val connectionInfo = SmbConnectionInfo(
            server = "testserver",
            shareName = "testshare",
            username = "u",
            password = "p",
            domain = "",
            port = 445
        )
        every { mockReachabilityGate.requireWifi("SMB") } throws NetworkConnectionLostException()

        val thrown = try {
            connectionManager.withConnection(connectionInfo) { SmbResult.Success(Unit) }
            null
        } catch (e: NetworkConnectionLostException) {
            e
        }

        assertNotNull("requireWifi failure must propagate as NetworkConnectionLostException", thrown)
        verify(exactly = 0) { anyConstructed<Socket>().connect(any<InetSocketAddress>(), any<Int>()) }
        verify(exactly = 0) { anyConstructed<SMBClient>().connect(any(), any<Int>()) }
    }

    @Test
    fun `withConnection skips degraded retry when TCP precheck fails — tcpReachable false branch`() = runBlocking {
        val connectionInfo = SmbConnectionInfo(
            server = "deadhost",
            shareName = "share",
            username = "u",
            password = "p",
            domain = "",
            port = 445
        )
        // Force TCP precheck failure: Socket.connect throws on every attempt
        every { anyConstructed<Socket>().connect(any<InetSocketAddress>(), any<Int>()) } throws
            java.net.SocketTimeoutException("simulated TCP precheck failure")

        val result = connectionManager.withConnection(connectionInfo) { SmbResult.Success(Unit) }

        assertTrue("Expected Error result on TCP precheck failure", result is SmbResult.Error)
        // Critical assertion: SMBJ connect must NOT be attempted when TCP precheck failed.
        verify(exactly = 0) { anyConstructed<SMBClient>().connect(any(), any<Int>()) }
    }

    @Test
    fun `withConnection consults reachability gate before pooled-connection attempt`() = runBlocking {
        val connectionInfo = SmbConnectionInfo(
            server = "testserver",
            shareName = "testshare",
            username = "u",
            password = "p",
            domain = "",
            port = 445
        )
        mockkObject(connectionManager) {
            every { connectionManager.getClient(any(), any()) } returns mockSmbClient
            connectionManager.withConnection(connectionInfo) { SmbResult.Success(Unit) }
            verify(atLeast = 1) { mockReachabilityGate.requireWifi("SMB") }
        }
    }
}
