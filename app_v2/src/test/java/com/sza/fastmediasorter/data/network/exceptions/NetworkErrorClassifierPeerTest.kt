package com.sza.fastmediasorter.data.network.exceptions

import com.jcraft.jsch.JSchException
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeTunnelUnavailableException
import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpPeerUnreachableFromHereException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

/** Contract ANYWHERE-ACCESS failure classes keep their own verdict through the wrappers they travel in. */
class NetworkErrorClassifierPeerTest {

    @Test
    fun `a tunnel refusal wrapped by JSch is its own class`() {
        val wrapped = JSchException("java.io.IOException: tunnel", ExchangeTunnelUnavailableException("unknown"))
        val classified = NetworkErrorClassifier.classify(wrapped)
        assertEquals(
            NetworkPeerUnreachableException.Reason.TUNNEL_NOT_REGISTERED,
            (classified as NetworkPeerUnreachableException).reason,
        )
        assertTrue(NetworkErrorClassifier.isTransient(wrapped))
    }

    @Test
    fun `the unreachable-from-here verdict is its own class`() {
        val failure = SftpPeerUnreachableFromHereException(SocketTimeoutException("connect timed out"))
        val classified = NetworkErrorClassifier.classify(failure)
        assertEquals(
            NetworkPeerUnreachableException.Reason.UNREACHABLE_FROM_THIS_NETWORK,
            (classified as NetworkPeerUnreachableException).reason,
        )
    }

    @Test
    fun `an auth rejection still wins over a peer wrapper`() {
        val failure = SftpPeerUnreachableFromHereException(JSchException("Auth fail"))
        assertTrue(NetworkErrorClassifier.classify(failure) is NetworkAuthRejectedException)
    }
}
