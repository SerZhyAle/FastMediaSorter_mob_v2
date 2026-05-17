package com.sza.fastmediasorter.data.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IdleDisconnectPolicyImplTest {

    @Test
    fun `arm fires timeout callback once after idle window`() = runTest {
        val policy = IdleDisconnectPolicyImpl(StandardTestDispatcher(testScheduler))
        var firedCount = 0

        policy.arm("sftp@test", 1_000) {
            firedCount += 1
        }

        advanceTimeBy(999)
        runCurrent()
        assertEquals(0, firedCount)

        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals(1, firedCount)
    }

    @Test
    fun `touch restarts existing timer`() = runTest {
        val policy = IdleDisconnectPolicyImpl(StandardTestDispatcher(testScheduler))
        var firedCount = 0

        policy.arm("ftp@test", 1_000) {
            firedCount += 1
        }
        advanceTimeBy(900)
        policy.touch("ftp@test")

        advanceTimeBy(100)
        runCurrent()
        assertEquals(0, firedCount)

        advanceTimeBy(900)
        advanceUntilIdle()
        assertEquals(1, firedCount)
    }

    @Test
    fun `touch suppresses stale timeout callback from older generation`() = runTest {
        val policy = IdleDisconnectPolicyImpl(StandardTestDispatcher(testScheduler))
        var firedCount = 0

        policy.arm("smb@test", 1_000) {
            firedCount += 1
        }

        advanceTimeBy(900)
        policy.touch("smb@test")

        advanceTimeBy(100)
        runCurrent()
        assertEquals(0, firedCount)

        advanceTimeBy(900)
        advanceUntilIdle()
        assertEquals(1, firedCount)
    }

    @Test
    fun `disarm cancels latest generation after touch`() = runTest {
        val policy = IdleDisconnectPolicyImpl(StandardTestDispatcher(testScheduler))
        var firedCount = 0

        policy.arm("smb@test", 1_000) {
            firedCount += 1
        }

        advanceTimeBy(500)
        policy.touch("smb@test")
        advanceTimeBy(100)
        policy.disarm("smb@test")

        advanceUntilIdle()
        assertEquals(0, firedCount)
    }

    @Test
    fun `disarm cancels pending timeout`() = runTest {
        val policy = IdleDisconnectPolicyImpl(StandardTestDispatcher(testScheduler))
        var firedCount = 0

        policy.arm("smb@test", 1_000) {
            firedCount += 1
        }
        policy.disarm("smb@test")

        advanceTimeBy(1_000)
        advanceUntilIdle()

        assertEquals(0, firedCount)
    }
}