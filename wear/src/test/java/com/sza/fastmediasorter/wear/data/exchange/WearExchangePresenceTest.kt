package com.sza.fastmediasorter.wear.data.exchange

import android.app.Activity
import com.sza.fastmediasorter.wear.domain.broadcast.WearBroadcastSessionState
import com.sza.fastmediasorter.wear.domain.broadcast.WearBroadcastSessionStateHolder
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Streams only for a reason until the battery measurement allows always-on ones. */
@OptIn(ExperimentalCoroutinesApi::class)
class WearExchangePresenceTest {

    @Test
    fun `any one reason holds the streams`() {
        assertFalse(WearExchangePresence.isWanted(visible = false, broadcasting = false, castPlaying = false))
        assertTrue(WearExchangePresence.isWanted(visible = true, broadcasting = false, castPlaying = false))
        assertTrue(WearExchangePresence.isWanted(visible = false, broadcasting = true, castPlaying = false))
        assertTrue(WearExchangePresence.isWanted(visible = false, broadcasting = false, castPlaying = true))
    }

    @Test
    fun `leaving the app keeps the streams for the linger and then drops them`() = runTest {
        val visibility = WearAppVisibility()
        val presence = WearExchangePresence(visibility, WearBroadcastSessionStateHolder())
        val seen = mutableListOf<Boolean>()
        val job = launch { presence.wanted(MutableStateFlow(false)).collect(seen::add) }

        runCurrent()
        visibility.onActivityStarted(ACTIVITY)
        runCurrent()
        visibility.onActivityStopped(ACTIVITY)
        advanceTimeBy(WearExchangePresence.VISIBLE_LINGER_MS - 1)
        runCurrent()
        assertEquals(listOf(false, true), seen)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf(false, true, false), seen)
        job.cancel()
    }

    @Test
    fun `a live broadcast holds the streams with the app closed`() = runTest {
        val broadcast = WearBroadcastSessionStateHolder()
        val presence = WearExchangePresence(WearAppVisibility(), broadcast)
        val seen = mutableListOf<Boolean>()
        val job = launch { presence.wanted(MutableStateFlow(false)).collect(seen::add) }

        runCurrent()
        broadcast.publish(WearBroadcastSessionState.Starting)
        runCurrent()

        assertEquals(listOf(false, true), seen)
        job.cancel()
    }

    private companion object {
        val ACTIVITY: Activity = mockk(relaxed = true)
    }
}
