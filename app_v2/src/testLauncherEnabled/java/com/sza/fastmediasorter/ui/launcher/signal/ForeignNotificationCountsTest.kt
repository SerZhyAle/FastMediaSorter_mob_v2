package com.sza.fastmediasorter.ui.launcher.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S1465 phase 02: the counting rule the KDoc of [ForeignNotificationCounts] states, asserted.
 */
@Suppress("FunctionNaming") // backtick test names, project convention
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForeignNotificationCountsTest {

    private lateinit var counts: ForeignNotificationCounts
    private lateinit var ownPackage: String

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        ownPackage = context.packageName
        counts = ForeignNotificationCounts(context)
        // S1793: the counter starts disabled (S1465 ADR-4 - nothing about another app's notifications is
        // held while the capability is off), so every case below describes the enabled state and has to
        // say so. Without this the class counts nothing and each assertion here reads an empty map.
        counts.setEnabled(true)
    }

    @Test
    fun `posting and removing a notification raises and clears its package count`() {
        counts.onPosted(CHAT, key = "a", isGroupSummary = false)

        assertEquals(mapOf(CHAT to 1), counts.counts.value)

        counts.onRemoved(CHAT, key = "a")

        assertTrue(counts.counts.value.isEmpty())
    }

    @Test
    fun `two applications are counted apart`() {
        counts.onPosted(CHAT, key = "a", isGroupSummary = false)
        counts.onPosted(MAIL, key = "b", isGroupSummary = false)
        counts.onPosted(MAIL, key = "c", isGroupSummary = false)

        assertEquals(mapOf(CHAT to 1, MAIL to 2), counts.counts.value)
    }

    /**
     * The system re-posts an existing notification to update it - a download refreshing its progress does
     * this once a second - so a repeated key must not climb the count.
     */
    @Test
    fun `re-posting the same key does not raise the count`() {
        counts.onPosted(CHAT, key = "a", isGroupSummary = false)
        counts.onPosted(CHAT, key = "a", isGroupSummary = false)

        assertEquals(mapOf(CHAT to 1), counts.counts.value)
    }

    @Test
    fun `a group summary is not counted beside its children`() {
        counts.onPosted(CHAT, key = "child", isGroupSummary = false)
        counts.onPosted(CHAT, key = "summary", isGroupSummary = true)

        assertEquals(mapOf(CHAT to 1), counts.counts.value)
    }

    @Test
    fun `this application's own notifications are ignored`() {
        counts.onPosted(ownPackage, key = "a", isGroupSummary = false)

        assertTrue(counts.counts.value.isEmpty())
    }

    /**
     * What the listener does on connect. A replacement, not a merge: anything posted or dismissed while the
     * app had no access was never seen, so carrying the old counts over would describe a stale shade.
     */
    @Test
    fun `reset replaces everything known with the currently posted set`() {
        counts.onPosted(CHAT, key = "old", isGroupSummary = false)

        counts.reset(
            listOf(
                ForeignNotificationCounts.PostedNotification(MAIL, key = "new", isGroupSummary = false),
                ForeignNotificationCounts.PostedNotification(MAIL, key = "sum", isGroupSummary = true),
                ForeignNotificationCounts.PostedNotification(ownPackage, key = "mine", isGroupSummary = false),
            ),
        )

        assertEquals(mapOf(MAIL to 1), counts.counts.value)
    }

    @Test
    fun `clear empties the counts, as a lost grant must`() {
        counts.onPosted(CHAT, key = "a", isGroupSummary = false)

        counts.clear()

        assertTrue(counts.counts.value.isEmpty())
    }

    /**
     * S1793: the capability's own switch, asserted rather than assumed. Every other case here enables it in
     * setUp, so without this one the disabled state - the state the class ships in - would be covered by
     * nothing, and an edit that dropped the guard would keep the suite green while holding data the owner
     * ruled must not be held (S1465 ADR-4).
     */
    @Test
    fun `nothing is counted while the capability is switched off`() {
        counts.setEnabled(false)

        counts.onPosted(CHAT, key = "a", isGroupSummary = false)

        assertTrue(counts.counts.value.isEmpty())
    }

    /**
     * S1908: a dismissal addresses notifications by key, so the accessor has to return every key of the
     * package it was asked about and nothing from its neighbour - cancelling by a key that belongs to
     * another application would clear notifications the user never touched.
     */
    @Test
    fun `keysFor returns every key of that package and none of another`() {
        counts.onPosted(CHAT, key = "a", isGroupSummary = false)
        counts.onPosted(CHAT, key = "b", isGroupSummary = false)
        counts.onPosted(MAIL, key = "c", isGroupSummary = false)

        assertEquals(setOf("a", "b"), counts.keysFor(CHAT))
        assertEquals(setOf("c"), counts.keysFor(MAIL))
    }

    @Test
    fun `keysFor is empty for a package that posted nothing`() {
        counts.onPosted(CHAT, key = "a", isGroupSummary = false)

        assertTrue(counts.keysFor(MAIL).isEmpty())
    }

    @Test
    fun `allKeys spans every counted package`() {
        counts.onPosted(CHAT, key = "a", isGroupSummary = false)
        counts.onPosted(MAIL, key = "b", isGroupSummary = false)

        assertEquals(setOf("a", "b"), counts.allKeys())
    }

    /**
     * S1465 ADR-4 holds nothing at all while the capability is off, and that has to include the keys: an
     * accessor that still answered would let a dismissal reach notifications the user opted out of counting.
     */
    @Test
    fun `keys are unreachable while the capability is disabled`() {
        counts.onPosted(CHAT, key = "a", isGroupSummary = false)
        counts.setEnabled(false)

        assertTrue(counts.keysFor(CHAT).isEmpty())
        assertTrue(counts.allKeys().isEmpty())
    }

    /**
     * The returned set must be a copy. The system delivers listener callbacks on its own thread while the
     * panel reads these keys on another, so handing out the live set would let a caller observe - or corrupt -
     * a set that the next callback is writing.
     */
    @Test
    fun `a returned key set is a copy and cannot mutate the counter`() {
        counts.onPosted(CHAT, key = "a", isGroupSummary = false)

        val handedOut = counts.keysFor(CHAT).toMutableSet()
        handedOut.add("injected")
        handedOut.remove("a")

        assertEquals(setOf("a"), counts.keysFor(CHAT))
        assertEquals(mapOf(CHAT to 1), counts.counts.value)
    }

    private companion object {
        const val CHAT = "com.example.chat"
        const val MAIL = "com.example.mail"
    }
}
