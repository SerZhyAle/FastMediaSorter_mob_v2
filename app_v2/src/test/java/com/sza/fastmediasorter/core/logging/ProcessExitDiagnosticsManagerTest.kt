package com.sza.fastmediasorter.core.logging

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProcessExitDiagnosticsManagerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val lines = mutableListOf<String>()
    private val context = mockk<Context>()
    private val activityManager = mockk<ActivityManager>()

    private fun manager(directory: File = folder.root): ProcessExitDiagnosticsManager {
        every { context.applicationContext } returns context
        every { context.noBackupFilesDir } returns directory
        every { context.packageName } returns PACKAGE
        every { context.getSystemService(ActivityManager::class.java) } returns activityManager
        return ProcessExitDiagnosticsManager(context, lines::add)
    }

    @After
    fun resetSnapshots() {
        ProcessExitDiagnosticsManager.audioServicePlaying = false
        ProcessExitDiagnosticsManager.lastTrimLevel = null
    }

    @Test
    fun `old platform preserves last observation without guessing a death cause`() {
        val first = manager()
        first.start()
        assertTrue(lines.single().contains("no prior observation; cause unknown"))
        ProcessExitDiagnosticsManager.audioServicePlaying = true
        ProcessExitDiagnosticsManager.lastTrimLevel = 15
        first.heartbeat()
        manager().start()
        assertEquals(2, lines.size)
        assertTrue(lines.last().contains("API unavailable"))
        assertTrue(lines.last().contains("clean shutdown not observed; cause unknown"))
        assertTrue(lines.last().contains("audioServicePlaying=true lastTrimLevel=15"))
        assertTrue(File(folder.root, "process-exit.properties").length() < MAX_MARKER_BYTES)
    }

    @Test
    fun `invalid marker recovers to a readable next session`() {
        File(folder.root, "process-exit.properties").writeText("lastSeen=\\uINVALID")
        manager().start()
        assertTrue(lines.single().contains("no prior observation"))
        manager().start()
        assertTrue(lines.last().contains("lastSeen="))
    }

    @Test
    fun `unwritable marker does not prevent startup reporting or later samples`() {
        val unavailableDirectory = folder.newFile()
        val diagnostics = manager(unavailableDirectory)
        diagnostics.start()
        assertFalse(diagnostics.heartbeat())
        assertEquals(1, lines.size)
    }

    @Test
    @Config(sdk = [30])
    fun `new platform reports exit details once and ignores other processes`() {
        val exit = mockk<ApplicationExitInfo>()
        every { exit.processName } returns PACKAGE
        every { exit.timestamp } returns EXIT_TIME
        every { exit.reason } returns ApplicationExitInfo.REASON_LOW_MEMORY
        every { exit.status } returns 0
        every { exit.importance } returns ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
        every { exit.pss } returns PSS_KIB
        every { exit.rss } returns RSS_KIB
        every { exit.description } returns "memory\npressure"
        val foreign = mockk<ApplicationExitInfo>()
        every { foreign.processName } returns "$PACKAGE:other"
        every { activityManager.getHistoricalProcessExitReasons(PACKAGE, 0, 10) } returns listOf(exit, foreign)
        manager().start()
        assertTrue(lines.single().contains("reason=${ApplicationExitInfo.REASON_LOW_MEMORY}"))
        assertTrue(lines.single().contains("pssKiB=$PSS_KIB rssKiB=$RSS_KIB description=memory pressure"))
        manager().start()
        assertTrue(lines.last().contains("no new platform exits"))
        assertFalse(lines.last().contains("reason="))
    }

    @Test
    @Config(sdk = [30])
    fun `platform service failure retains marker evidence`() {
        every { activityManager.getHistoricalProcessExitReasons(PACKAGE, 0, 10) } throws SecurityException()
        manager().start()
        manager().start()
        assertTrue(lines.last().contains("history unavailable"))
        assertTrue(lines.last().contains("lastSeen="))
    }

    @Test
    fun `description is bounded to a single line`() {
        val description = ProcessExitDiagnosticsManager.singleLine("native\r\n\t" + "x".repeat(LONG_DESCRIPTION))
        assertEquals(DESCRIPTION_LIMIT, description.length)
        assertFalse(description.contains('\n'))
        assertFalse(description.contains('\r'))
        assertFalse(description.contains('\t'))
    }

    private companion object {
        const val PACKAGE = "com.sza.fastmediasorter.debug"
        const val EXIT_TIME = 1_000L
        const val PSS_KIB = 128L
        const val RSS_KIB = 256L
        const val MAX_MARKER_BYTES = 1024L
        const val LONG_DESCRIPTION = 500
        const val DESCRIPTION_LIMIT = 160
    }
}
