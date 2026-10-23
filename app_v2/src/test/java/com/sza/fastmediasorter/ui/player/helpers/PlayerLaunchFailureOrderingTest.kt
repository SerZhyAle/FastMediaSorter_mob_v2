package com.sza.fastmediasorter.ui.player.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A launch failure emits ShowError and FinishActivity back to back. The player may close only after
 * the error surface has ended, or the user is left with no reason for the vanished player.
 */
class PlayerLaunchFailureOrderingTest {

    private var finishCount = 0
    private val sequencer = PlayerErrorFinishSequencer { finishCount++ }

    @Test
    fun `finish waits for an error whose settings load completes late`() {
        val errorDone = sequencer.beginPresentation()
        sequencer.requestFinish()

        assertEquals(0, finishCount)

        errorDone()

        assertEquals(1, finishCount)
    }

    @Test
    fun `finish without any error runs at once`() {
        sequencer.requestFinish()

        assertEquals(1, finishCount)
    }

    @Test
    fun `an error that ended before the finish request does not delay it`() {
        val errorDone = sequencer.beginPresentation()
        errorDone()

        assertEquals(0, finishCount)

        sequencer.requestFinish()

        assertEquals(1, finishCount)
    }

    @Test
    fun `repeated launch failures finish once after the last error ends`() {
        val firstDone = sequencer.beginPresentation()
        val secondDone = sequencer.beginPresentation()
        sequencer.requestFinish()
        sequencer.requestFinish()

        firstDone()
        assertEquals(0, finishCount)

        secondDone()
        assertEquals(1, finishCount)
    }

    @Test
    fun `a completion reported twice counts once`() {
        val firstDone = sequencer.beginPresentation()
        val secondDone = sequencer.beginPresentation()
        sequencer.requestFinish()

        firstDone()
        firstDone()

        assertEquals(0, finishCount)

        secondDone()
        assertEquals(1, finishCount)
    }

    @Test
    fun `a dismissal after the finish already ran does not finish again`() {
        val errorDone = sequencer.beginPresentation()
        sequencer.requestFinish()
        errorDone()
        val lateDone = sequencer.beginPresentation()
        lateDone()
        sequencer.requestFinish()

        assertEquals(1, finishCount)
    }
}
