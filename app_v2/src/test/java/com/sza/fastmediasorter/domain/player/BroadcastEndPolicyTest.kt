package com.sza.fastmediasorter.domain.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastEndPolicyTest {

    @Test
    fun theDirectoryRemovalAndARelayGone_endWithoutAQuestion() {
        assertFalse(BroadcastEndPolicy.needsCheck(BroadcastStopSignal.DirectoryRemoved))
        assertFalse(BroadcastEndPolicy.needsCheck(BroadcastStopSignal.HttpStatus(GONE)))
        assertEquals(BroadcastEndDecision.Ended, BroadcastEndPolicy.decide(BroadcastStopSignal.DirectoryRemoved, null))
        assertEquals(BroadcastEndDecision.Ended, BroadcastEndPolicy.decide(BroadcastStopSignal.HttpStatus(GONE), null))
    }

    @Test
    fun aStreamEndOrNotFound_asksTheOneReconnect() {
        assertTrue(BroadcastEndPolicy.needsCheck(BroadcastStopSignal.StreamEnded))
        assertTrue(BroadcastEndPolicy.needsCheck(BroadcastStopSignal.HttpStatus(NOT_FOUND)))
        assertTrue(BroadcastEndPolicy.needsCheck(BroadcastStopSignal.HttpStatus(null)))
    }

    @Test
    fun theReconnectAnswer_decides() {
        val signal = BroadcastStopSignal.StreamEnded

        assertEquals(BroadcastEndDecision.Ended, BroadcastEndPolicy.decide(signal, AfterEndAnswer.ENDED))
        assertEquals(BroadcastEndDecision.LostConnection, BroadcastEndPolicy.decide(signal, AfterEndAnswer.NO_ANSWER))
        assertNull(BroadcastEndPolicy.decide(signal, AfterEndAnswer.ALIVE))
    }

    @Test
    fun theBudget_allowsThreeWithGrowingDelaysThenRefusesUntilReset() {
        val budget = BroadcastReconnectBudget()

        val delays = List(BUDGET) { budget.tryConsume().also(::assertTrue).let { budget.delayMs() } }

        assertEquals(listOf(FIRST_DELAY_MS, SECOND_DELAY_MS, THIRD_DELAY_MS), delays)
        assertFalse(budget.tryConsume())
        budget.reset()
        assertTrue(budget.tryConsume())
    }

    @Test
    fun aRemovalWhileDelivering_isHeldAndReadAtTheStop() {
        val hold = BroadcastRemovalHold()
        hold.onDelivering()

        assertFalse(hold.onDirectory(true))
        assertEquals(BroadcastStopSignal.DirectoryRemoved, hold.atStop(BroadcastStopSignal.StreamEnded))
    }

    @Test
    fun aRemovalWhileNotDelivering_isReadAtOnce() {
        val hold = BroadcastRemovalHold()
        hold.onDelivering()
        hold.atStop(BroadcastStopSignal.HttpStatus(null))

        assertTrue(hold.onDirectory(true))
        assertTrue(BroadcastRemovalHold().onDirectory(true))
    }

    @Test
    fun aStopWithNoRemoval_keepsItsSignal_andAReListingClearsTheHold() {
        val hold = BroadcastRemovalHold()
        hold.onDelivering()
        assertEquals(BroadcastStopSignal.StreamEnded, hold.atStop(BroadcastStopSignal.StreamEnded))

        hold.onDelivering()
        hold.onDirectory(true)
        assertFalse(hold.onDirectory(false))
        assertEquals(BroadcastStopSignal.StreamEnded, hold.atStop(BroadcastStopSignal.StreamEnded))

        hold.onDirectory(true)
        hold.reset()
        assertEquals(BroadcastStopSignal.StreamEnded, hold.atStop(BroadcastStopSignal.StreamEnded))
    }

    private companion object {
        const val GONE = 410
        const val NOT_FOUND = 404
        const val BUDGET = 3
        const val FIRST_DELAY_MS = 2_000L
        const val SECOND_DELAY_MS = 4_000L
        const val THIRD_DELAY_MS = 8_000L
    }
}
