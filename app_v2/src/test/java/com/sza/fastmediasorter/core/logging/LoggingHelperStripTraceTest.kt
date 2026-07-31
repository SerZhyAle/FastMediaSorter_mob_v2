package com.sza.fastmediasorter.core.logging

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S1248: `Timber.Tree.prepareLog` appends the throwable's stack trace to the message before any
 * tree runs, so a tree that renders the trace itself must strip the glued copy first - otherwise
 * every ERROR lands in the session file with two identical traces (the reported defect).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class LoggingHelperStripTraceTest {

    private val throwable = IllegalStateException("socket is not established")

    /** What a tree actually receives for `Timber.e(t, text)` on the pinned Timber. */
    private fun timberMessage(text: String): String = text + "\n" + Log.getStackTraceString(throwable)

    @Test
    fun `strips the glued trace back to the caller text`() {
        val received = timberMessage("Connection test failed")
        assertEquals("Connection test failed", stripTimberAppendedTrace(received, throwable))
    }

    @Test
    fun `no throwable leaves the message untouched`() {
        assertEquals("plain warning", stripTimberAppendedTrace("plain warning", null))
    }

    @Test
    fun `text-less throwable call collapses to empty so the tree logs the trace once`() {
        // prepareLog for Timber.e(t) with no text: the message IS the trace.
        val received = Log.getStackTraceString(throwable)
        assertEquals("", stripTimberAppendedTrace(received, throwable))
    }

    @Test
    fun `caller newlines inside the text survive the strip`() {
        val received = timberMessage("line one\nline two")
        assertEquals("line one\nline two", stripTimberAppendedTrace(received, throwable))
    }

    @Test
    fun `stripped text plus one rendered trace yields exactly one trace total`() {
        val received = timberMessage("Connection test failed")
        val bare = stripTimberAppendedTrace(received, throwable)
        // What the file tree writes for an ERROR: the bare line, then its own trace rendering.
        val written = bare + "\n" + Log.getStackTraceString(throwable)
        val firstFrame = throwable.stackTrace.first().toString()
        val occurrences = written.split(firstFrame).size - 1
        assertEquals(1, occurrences)
    }
}
