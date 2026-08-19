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

    private companion object {
        const val CHAT = "com.example.chat"
        const val MAIL = "com.example.mail"
    }
}
