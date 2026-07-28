package com.sza.fastmediasorter.data.network.exceptions

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import java.io.IOException

/**
 * S1213: a transport precondition the user configured is not a defect. These tests pin the log
 * level so a later edit cannot quietly restore the error-level stack that made Wi-Fi-gate refusals
 * the only red lines in the daily log.
 */
class HandledNetworkOutcomeLoggerTest {

    private data class LogEntry(val priority: Int, val message: String, val throwable: Throwable?)

    private class CapturingTree : Timber.Tree() {
        val entries = mutableListOf<LogEntry>()

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            entries += LogEntry(priority, message, t)
        }
    }

    private lateinit var tree: CapturingTree

    @Before
    fun setUp() {
        tree = CapturingTree()
        Timber.plant(tree)
    }

    @After
    fun tearDown() {
        Timber.uproot(tree)
    }

    @Test
    fun `wifi gate refusal is logged below error and without a stack`() {
        HandledNetworkOutcomeLogger.logConnectionTestFailure(
            scope = "smb-test-connection",
            resourceLabel = "server/share",
            throwable = WifiRequiredException(),
            message = "SMB testConnection failed"
        )

        val entry = tree.entries.single()
        assertEquals(PRIORITY_DEBUG, entry.priority)
        assertNull("policy skips must not carry a stack trace", entry.throwable)
        assertTrue(entry.message.contains("failureClass=wifi-required"))
        assertTrue(entry.message.contains("SMB testConnection failed"))
    }

    @Test
    fun `denied local network permission is treated as policy too`() {
        HandledNetworkOutcomeLogger.logConnectionTestFailure(
            scope = "resource-navigation",
            resourceLabel = "NAS",
            throwable = LocalNetworkPermissionDeniedException(),
            message = "Connection test failed"
        )

        assertEquals(PRIORITY_DEBUG, tree.entries.single().priority)
    }

    @Test
    fun `a real failure keeps error level and its stack`() {
        val failure = IOException("socket closed")

        HandledNetworkOutcomeLogger.logConnectionTestFailure(
            scope = "smb-test-connection",
            resourceLabel = "server/share",
            throwable = failure,
            message = "SMB testConnection failed after 3 attempts"
        )

        val entry = tree.entries.single()
        assertEquals(PRIORITY_ERROR, entry.priority)
        assertSame("the original throwable must reach the log", failure, entry.throwable)
    }

    @Test
    fun `connection lost without wifi restriction stays an error`() {
        HandledNetworkOutcomeLogger.logConnectionTestFailure(
            scope = "resource-navigation",
            resourceLabel = "NAS",
            throwable = NetworkConnectionLostException(),
            message = "Connection test failed"
        )

        assertEquals(PRIORITY_ERROR, tree.entries.single().priority)
    }

    private companion object {
        // android.util.Log priorities, restated here because android.util.Log is stubbed out on the
        // JVM unit-test classpath (isReturnDefaultValues) and would compare every priority as 0.
        const val PRIORITY_DEBUG = 3
        const val PRIORITY_ERROR = 6
    }
}
