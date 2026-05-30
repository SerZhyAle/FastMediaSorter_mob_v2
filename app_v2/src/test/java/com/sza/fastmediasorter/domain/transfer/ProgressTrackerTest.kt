package com.sza.fastmediasorter.domain.transfer

import com.sza.fastmediasorter.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [ProgressTracker] percentage maths, first/last reporting, tracking lifecycle,
 * and the [generateOperationId] helper. Updates marshal onto Dispatchers.Main, so the test
 * installs [MainDispatcherRule].
 */
class ProgressTrackerTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `null callback is a no-op and does not start tracking`() = runTest {
        val tracker = ProgressTracker()
        tracker.reportProgress("op", current = 5, total = 10, onProgress = null)
        assertEquals(0, tracker.getTrackedOperationCount())
    }

    @Test
    fun `first progress at zero is always reported`() = runTest {
        val tracker = ProgressTracker()
        var reported: Int? = null
        tracker.reportProgress("op", current = 0, total = 100, onProgress = { reported = it })
        assertEquals(0, reported)
        assertEquals(1, tracker.getTrackedOperationCount())
    }

    @Test
    fun `completion is always reported at one hundred percent`() = runTest {
        val tracker = ProgressTracker()
        var reported: Int? = null
        tracker.reportProgress("op", current = 100, total = 100, onProgress = { reported = it })
        assertEquals(100, reported)
    }

    @Test
    fun `percentage is clamped to one hundred when current exceeds total`() = runTest {
        val tracker = ProgressTracker()
        var reported: Int? = null
        tracker.reportProgress("op", current = 150, total = 100, onProgress = { reported = it })
        assertEquals(100, reported)
    }

    @Test
    fun `zero total yields zero percent on the first report`() = runTest {
        val tracker = ProgressTracker()
        var reported: Int? = null
        tracker.reportProgress("op", current = 0, total = 0, onProgress = { reported = it })
        assertEquals(0, reported)
    }

    @Test
    fun `reportProgressBytes forwards raw byte counts on the first report`() = runTest {
        val tracker = ProgressTracker()
        var seen: Pair<Long, Long>? = null
        tracker.reportProgressBytes("op", current = 0, total = 2048, onProgress = { c, t -> seen = c to t })
        assertEquals(0L to 2048L, seen)
    }

    @Test
    fun `clearOperation and clearAll prune tracked operations`() = runTest {
        val tracker = ProgressTracker()
        tracker.reportProgress("a", 0, 10, onProgress = {})
        tracker.reportProgress("b", 0, 10, onProgress = {})
        assertEquals(2, tracker.getTrackedOperationCount())

        tracker.clearOperation("a")
        assertEquals(1, tracker.getTrackedOperationCount())

        tracker.clearAll()
        assertEquals(0, tracker.getTrackedOperationCount())
    }

    @Test
    fun `generateOperationId embeds type and is unique per path combination`() {
        val withDest = generateOperationId("copy", "/a", "/b")
        assertTrue(withDest.startsWith("copy_"))

        val a = generateOperationId("move", "/src")
        val b = generateOperationId("move", "/other")
        // Different source paths hash differently, so the ids differ.
        assertTrue(a.startsWith("move_"))
        assertTrue(b.startsWith("move_"))
        assertTrue(a.substringBeforeLast('_') != b.substringBeforeLast('_'))
    }
}
