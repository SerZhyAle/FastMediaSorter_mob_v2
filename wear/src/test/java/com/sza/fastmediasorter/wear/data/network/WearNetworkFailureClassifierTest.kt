package com.sza.fastmediasorter.wear.data.network

import com.jcraft.jsch.JSchException
import com.sza.fastmediasorter.wear.domain.model.WearNetworkFailure
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * S2488: every exception here is constructed, never thrown by a real socket - the classifier's job is
 * a mapping, and a test that opened a connection would be testing the network instead.
 */
class WearNetworkFailureClassifierTest {

    private val classifier = WearNetworkFailureClassifier()

    @Test
    fun `an unknown host is named as one`() {
        assertEquals(
            WearNetworkFailure.UNKNOWN_HOST,
            classifier.classify(UnknownHostException("companion.local"))
        )
    }

    @Test
    fun `a socket timeout is named as one`() {
        assertEquals(
            WearNetworkFailure.TIMEOUT,
            classifier.classify(SocketTimeoutException("connect timed out"))
        )
    }

    @Test
    fun `a refused connection is named as one`() {
        assertEquals(
            WearNetworkFailure.CONNECTION_REFUSED,
            classifier.classify(ConnectException("Connection refused"))
        )
    }

    @Test
    fun `a JSch auth failure carries no type and is read from its message`() {
        assertEquals(
            WearNetworkFailure.AUTH_REJECTED,
            classifier.classify(JSchException("Auth fail"))
        )
    }

    @Test
    fun `a JSch timeout carries no type and is read from its message`() {
        assertEquals(
            WearNetworkFailure.TIMEOUT,
            classifier.classify(JSchException("timeout: socket is not established"))
        )
    }

    @Test
    fun `a refusal wrapped by JSch is still a refusal`() {
        // The wrapper's own message names nothing, so only the cause walk can answer this one.
        val wrapped = JSchException("session is down")
            .apply { initCause(ConnectException("Connection refused")) }

        assertEquals(WearNetworkFailure.CONNECTION_REFUSED, classifier.classify(wrapped))
    }

    // SHARE-SESSION 1.3: a server at capacity closes before its banner, and JSch 0.2.26 Session.connect reports
    // that EOF as this exact message; it is an undetermined transport outcome, never a rejected login.
    @Test
    fun `a pre-banner close is OTHER, not an auth rejection`() {
        val direct = JSchException("connection is closed by foreign host")
        val wrapped = IOException("sftp connect failed", direct)

        listOf(direct, wrapped).forEach { error ->
            assertEquals(WearNetworkFailure.OTHER, classifier.classify(error))
        }
    }

    @Test
    fun `an exception matching nothing falls back to OTHER`() {
        assertEquals(WearNetworkFailure.OTHER, classifier.classify(IOException("disk went away")))
    }
}
