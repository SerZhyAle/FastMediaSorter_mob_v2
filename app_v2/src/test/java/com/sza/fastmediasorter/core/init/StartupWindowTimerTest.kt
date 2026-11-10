package com.sza.fastmediasorter.core.init

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupWindowTimerTest {

    @Test
    fun `window line names every segment in milliseconds`() {
        val line = StartupWindowTimer.formatWindowLine(
            processStart = 1_000L,
            attachStart = 1_200L,
            attachEnd = 1_250L,
            onCreateEnter = 8_250L,
            injected = 8_400L
        )

        assertEquals(
            "Startup info: window preAttach=200ms attach=50ms providers=7000ms inject=150ms total=7400ms",
            line
        )
    }

    @Test
    fun `unknown process start is written as n-a`() {
        val line = StartupWindowTimer.formatWindowLine(-1L, 1_200L, 1_250L, 1_300L, 1_400L)

        assertTrue(line.contains("preAttach=n/a"))
        assertTrue(line.contains("total=n/a"))
        assertTrue(line.contains("attach=50ms"))
    }

    @Test
    fun `stall line keeps only the requested number of frames`() {
        val frames = (1..5).map { StackTraceElement("Cls", "m$it", "Cls.kt", it) }

        val line = StartupWindowTimer.formatStallLine(waitedMs = 4_000L, frames = frames, maxFrames = 2)

        val lines = line.lines()
        assertEquals("Startup info: stall main thread waited=4000ms at:", lines.first())
        assertEquals(3, lines.size)
        assertTrue(lines[1].contains("m1"))
        assertTrue(lines[2].contains("m2"))
    }
}
