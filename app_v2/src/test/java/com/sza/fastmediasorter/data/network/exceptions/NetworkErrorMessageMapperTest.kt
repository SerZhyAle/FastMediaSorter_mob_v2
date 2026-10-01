package com.sza.fastmediasorter.data.network.exceptions

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.network.NetworkContextAnalyzer
import com.sza.fastmediasorter.domain.model.ResourceType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * S0118 - narrow tests for [NetworkErrorMessageMapper.toMessageRes].
 *
 * Resource-only behavior: no Android Context needed, so these tests run as plain
 * JVM unit tests and serve as regression coverage for the friendly-copy contract
 * around network errors.
 */
class NetworkErrorMessageMapperTest {

    @Test
    fun `timeout exception maps to error_network_timeout`() {
        val res = NetworkErrorMessageMapper.toMessageRes(SocketTimeoutException("timed out"))
        assertEquals(R.string.error_network_timeout, res)
    }

    @Test
    fun `unknown host maps to timeout`() {
        // NetworkErrorClassifier treats an unresolved host as a transient timeout condition,
        // so DNS failures surface the timeout copy rather than connection-lost.
        val res = NetworkErrorMessageMapper.toMessageRes(UnknownHostException("no host"))
        assertEquals(R.string.error_network_timeout, res)
    }

    @Test
    fun `SFTP auth rejection asks to re-pair`() {
        assertEquals(REPAIR, contextAware(NetworkAuthRejectedException("Auth fail"), ResourceType.SFTP))
    }

    @Test
    fun `SFTP permission denied status keeps the access-denied message`() {
        assertEquals(ACCESS_DENIED, contextAware(NetworkAccessDeniedException("permission denied"), ResourceType.SFTP))
    }

    @Test
    fun `FTP access denied keeps the re-pair guidance`() {
        assertEquals(REPAIR, contextAware(NetworkAccessDeniedException("530 login"), ResourceType.FTP))
    }

    private fun contextAware(exception: NetworkException, type: ResourceType): String {
        val context = mockk<Context> {
            every { getString(R.string.error_companion_repair_needed) } returns REPAIR
            every { getString(R.string.error_network_access_denied) } returns ACCESS_DENIED
        }
        val analyzer = mockk<NetworkContextAnalyzer>(relaxed = true)
        return NetworkErrorMessageMapper.toContextAwareMessage(context, exception, type, "sftp://h:22/", analyzer)
    }

    @Test
    fun `generic IOException maps to a non-zero resource id`() {
        // Friendly-copy regression: even unclassified IO errors must still resolve to
        // a real string resource so the user never sees an empty placeholder.
        val res = NetworkErrorMessageMapper.toMessageRes(IOException("boom"))
        assert(res != 0) { "Resource id must be non-zero" }
    }

    private companion object {
        const val REPAIR = "repair"
        const val ACCESS_DENIED = "access-denied"
    }
}
