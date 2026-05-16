package com.sza.fastmediasorter.core.init

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirstFrameSignalTest {

    @Test
    fun `signal fires only once`() {
        val signal = FirstFrameSignal()

        assertTrue(signal.signal())
        assertFalse(signal.signal())
        assertTrue(signal.hasFired())
    }

    @Test
    fun `await returns immediately after signal fires`() = runTest {
        val signal = FirstFrameSignal()

        assertTrue(signal.signal())
        signal.await(timeoutMs = 1)
        assertTrue(signal.hasFired())
    }
}