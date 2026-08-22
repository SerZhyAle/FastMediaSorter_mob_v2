package com.sza.fastmediasorter.wear.core.logging

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * S1802: the buffer carries two contracts the rest of the feature depends on - it never exceeds its
 * ceiling, and it cannot hold an unmasked credential. Both are properties of this class alone, so they
 * are pinned here rather than at the transport or the UI layer.
 */
class WearLogBufferTest {

    @Before
    fun setUp() {
        WearLogBuffer.clear()
    }

    @After
    fun tearDown() {
        WearLogBuffer.clear()
    }

    @Test
    fun `fresh buffer yields an empty snapshot`() {
        assertEquals("", WearLogBuffer.snapshot())
        assertEquals(0, WearLogBuffer.sizeInBytes)
    }

    @Test
    fun `appended lines come back oldest first`() {
        WearLogBuffer.append("first")
        WearLogBuffer.append("second")
        WearLogBuffer.append("third")

        assertEquals("first\nsecond\nthird", WearLogBuffer.snapshot())
    }

    @Test
    fun `exceeding the ceiling drops the oldest line and never exceeds it`() {
        val chunk = "x".repeat(CHUNK_SIZE)
        val appends = (WearLogBuffer.MAX_BYTES / CHUNK_SIZE) + EXTRA_APPENDS

        repeat(appends) { index ->
            WearLogBuffer.append("$index$chunk")
        }

        assertTrue(
            "retained ${WearLogBuffer.sizeInBytes} exceeds ceiling ${WearLogBuffer.MAX_BYTES}",
            WearLogBuffer.sizeInBytes <= WearLogBuffer.MAX_BYTES
        )
        assertFalse("oldest line survived eviction", WearLogBuffer.snapshot().startsWith("0"))
    }

    @Test
    fun `a line longer than the ceiling is truncated rather than dropped`() {
        WearLogBuffer.append("y".repeat(WearLogBuffer.MAX_BYTES * OVERSIZE_FACTOR))

        assertTrue("oversized line was dropped entirely", WearLogBuffer.snapshot().isNotEmpty())
        assertTrue(WearLogBuffer.sizeInBytes <= WearLogBuffer.MAX_BYTES)
    }

    @Test
    fun `an oversized multi-byte line is truncated rather than dropped`() {
        // Cyrillic encodes to two bytes per character, so the ceiling cut lands inside a character
        // unless the truncation walks back to a boundary.
        WearLogBuffer.append("я".repeat(WearLogBuffer.MAX_BYTES * OVERSIZE_FACTOR))

        assertTrue("oversized multi-byte line was dropped entirely", WearLogBuffer.snapshot().isNotEmpty())
        assertTrue(WearLogBuffer.sizeInBytes <= WearLogBuffer.MAX_BYTES)
    }

    @Test
    fun `a credential shaped value is masked before it reaches the buffer`() {
        val masked = WearSecretMasker.sanitize("connecting password=hunter2 to smb://user:s3cret@host/share")

        assertFalse("plain password survived masking", masked.contains("hunter2"))
        assertFalse("uri credential survived masking", masked.contains("s3cret"))
        assertTrue("masking marker missing", masked.contains("****"))
    }

    @Test
    fun `concurrent appends leave the buffer readable and within the ceiling`() {
        val threads = Executors.newFixedThreadPool(THREAD_COUNT)
        val latch = CountDownLatch(THREAD_COUNT)

        repeat(THREAD_COUNT) { worker ->
            threads.execute {
                repeat(APPENDS_PER_THREAD) { index -> WearLogBuffer.append("worker $worker line $index") }
                latch.countDown()
            }
        }

        assertTrue("workers did not finish in time", latch.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        threads.shutdown()

        assertTrue(WearLogBuffer.sizeInBytes <= WearLogBuffer.MAX_BYTES)
        assertTrue(WearLogBuffer.snapshot().isNotEmpty())
    }

    private companion object {
        const val CHUNK_SIZE = 512
        const val EXTRA_APPENDS = 5
        const val OVERSIZE_FACTOR = 2
        const val THREAD_COUNT = 8
        const val APPENDS_PER_THREAD = 200
        const val AWAIT_SECONDS = 10L
    }
}
