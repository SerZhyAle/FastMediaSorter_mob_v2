package com.sza.fastmediasorter.identity

import com.sza.fastmediasorter.domain.identity.GoogleScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * S4487: a consent refusal is remembered per scope set for a bounded window, so a background poll does not
 * put the same question to Play services every cycle, and it never outlives the user's decision.
 */
class ConsentRefusalMemoryTest {

    private val start = Instant.parse("2026-10-29T10:00:00Z")
    private var now = start
    private val window = Duration.ofMinutes(15)
    private val memory = ConsentRefusalMemory(clock = { now }, memory = window)

    private val appData = setOf(GoogleScope.DRIVE_APPDATA)
    private val browsing = setOf(GoogleScope.DRIVE, GoogleScope.DRIVE_READONLY)

    @Test
    fun `nothing is remembered before a refusal`() {
        assertFalse(memory.isRemembered(appData))
    }

    @Test
    fun `a refusal is remembered inside the window`() {
        memory.remember(appData)
        now = start.plus(window).minusSeconds(1)

        assertTrue(memory.isRemembered(appData))
    }

    @Test
    fun `a refusal expires with the window`() {
        memory.remember(appData)
        now = start.plus(window)

        assertFalse(memory.isRemembered(appData))
    }

    @Test
    fun `an expired refusal stays gone`() {
        memory.remember(appData)
        now = start.plus(window).plusSeconds(1)
        memory.isRemembered(appData)
        now = start

        assertFalse(memory.isRemembered(appData))
    }

    @Test
    fun `scope sets are remembered independently`() {
        memory.remember(appData)

        assertFalse(memory.isRemembered(browsing))
        assertFalse(memory.isRemembered(appData + browsing))
    }

    @Test
    fun `forget clears every remembered refusal`() {
        memory.remember(appData)
        memory.remember(browsing)

        memory.forget()

        assertFalse(memory.isRemembered(appData))
        assertFalse(memory.isRemembered(browsing))
    }

    @Test
    fun `a repeated refusal extends the window`() {
        memory.remember(appData)
        now = start.plus(window).minusSeconds(1)
        memory.remember(appData)
        now = start.plus(window).plusSeconds(1)

        assertTrue(memory.isRemembered(appData))
    }
}
