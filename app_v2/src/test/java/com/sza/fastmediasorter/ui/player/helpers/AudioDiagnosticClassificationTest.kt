package com.sza.fastmediasorter.ui.player.helpers

import android.util.Log
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioDiagnosticClassificationTest {

    private val none = Player.PLAYBACK_SUPPRESSION_REASON_NONE

    @Test
    fun `underrun while playing is an active fault logged at warning`() {
        val context = AudioDiagnosticClassifier.classifyUnderrun(true, Player.STATE_READY, none)

        assertEquals(UnderrunContext.ACTIVE_PLAYBACK, context)
        assertEquals(Log.WARN, AudioDiagnosticClassifier.underrunLogPriority(context))
    }

    @Test
    fun `underrun after an explicit pause is a drain logged below warning`() {
        val context = AudioDiagnosticClassifier.classifyUnderrun(false, Player.STATE_READY, none)

        assertEquals(UnderrunContext.PAUSED, context)
        assertEquals(Log.INFO, AudioDiagnosticClassifier.underrunLogPriority(context))
    }

    @Test
    fun `underrun during a focus suppression counts as paused`() {
        val context = AudioDiagnosticClassifier.classifyUnderrun(
            true,
            Player.STATE_READY,
            Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS,
        )

        assertEquals(UnderrunContext.PAUSED, context)
    }

    @Test
    fun `underrun while buffering is recorded below warning`() {
        val context = AudioDiagnosticClassifier.classifyUnderrun(true, Player.STATE_BUFFERING, none)

        assertEquals(UnderrunContext.BUFFERING, context)
        assertEquals(Log.INFO, AudioDiagnosticClassifier.underrunLogPriority(context))
    }

    @Test
    fun `underrun with no playable item is stopped`() {
        val context = AudioDiagnosticClassifier.classifyUnderrun(true, Player.STATE_IDLE, none)

        assertEquals(UnderrunContext.STOPPED, context)
        assertEquals(Log.INFO, AudioDiagnosticClassifier.underrunLogPriority(context))
    }
}
