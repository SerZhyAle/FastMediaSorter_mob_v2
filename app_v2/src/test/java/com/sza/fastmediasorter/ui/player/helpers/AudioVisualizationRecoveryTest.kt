package com.sza.fastmediasorter.ui.player.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioVisualizationRecoveryTest {

    @Test
    fun `failure of the current attempt is accepted once`() {
        val policy = AudioVisualizationRecoveryPolicy()
        val token = policy.begin()

        assertTrue(policy.onFailure(token))
        assertFalse(policy.onFailure(token))
    }

    @Test
    fun `late error of a superseded player is ignored`() {
        val policy = AudioVisualizationRecoveryPolicy()
        val old = policy.begin()
        policy.invalidate()
        val current = policy.begin()

        assertFalse(policy.onFailure(old))
        assertTrue(policy.isCurrent(current))
    }

    @Test
    fun `prepare of a released player is ignored`() {
        val policy = AudioVisualizationRecoveryPolicy()
        val token = policy.begin()
        policy.invalidate()

        assertFalse(policy.onPrepared(token))
    }

    @Test
    fun `fresh attempt after a failure is current`() {
        val policy = AudioVisualizationRecoveryPolicy()
        policy.onFailure(policy.begin())

        val next = policy.begin()

        assertTrue(policy.isCurrent(next))
        assertTrue(policy.onPrepared(next))
    }

    @Test
    fun `lifecycle recovery stops after repeated failures`() {
        val policy = AudioVisualizationRecoveryPolicy(maxLifecycleRecoveries = 3)
        repeat(2) { policy.onFailure(policy.begin()) }
        assertTrue(policy.mayRecoverFromLifecycle())

        policy.onStartFailure()

        assertFalse(policy.mayRecoverFromLifecycle())
    }

    @Test
    fun `successful prepare restores lifecycle recovery`() {
        val policy = AudioVisualizationRecoveryPolicy(maxLifecycleRecoveries = 1)
        policy.onFailure(policy.begin())
        assertFalse(policy.mayRecoverFromLifecycle())

        policy.onPrepared(policy.begin())

        assertTrue(policy.mayRecoverFromLifecycle())
    }

    @Test
    fun `reset after close restores lifecycle recovery`() {
        val policy = AudioVisualizationRecoveryPolicy(maxLifecycleRecoveries = 1)
        policy.onFailure(policy.begin())

        policy.resetFailures()

        assertTrue(policy.mayRecoverFromLifecycle())
    }
}
