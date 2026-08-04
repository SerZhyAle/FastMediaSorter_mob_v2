package com.sza.fastmediasorter.domain.model.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S1176: the `contact:` target is a persistence format from the first pinned cell, and the one field
 * that can carry anything - the display name - is user data.
 *
 * These are the cases a fixed-field split would silently corrupt, and the ones an older build must
 * survive when it meets a record it does not understand. None of them is visible to a static gate: a
 * broken codec still compiles and only shows up as a cell that opens the wrong thing, or a crash.
 */
class LauncherContactCommandCodecTest {

    private fun roundTrip(target: LauncherContactTarget): LauncherContactTarget? {
        val encoded = LauncherCellCommand.Contact(target).encode()
        return (LauncherCellCommand.decode(encoded) as? LauncherCellCommand.Contact)?.target
    }

    @Test
    fun `a display name containing the separator round-trips unchanged`() {
        val target = LauncherContactTarget(
            action = LauncherContactAction.DIAL,
            phoneNumber = "+41 44 668 18 00",
            displayName = "Bob: work",
        )
        assertEquals("Bob: work", roundTrip(target)?.displayName)
        assertEquals(target, roundTrip(target))
    }

    @Test
    fun `a display name of separators alone survives`() {
        val target = LauncherContactTarget(
            action = LauncherContactAction.PROFILE,
            lookupKey = "0r1-2A",
            displayName = ":::::",
        )
        assertEquals(":::::", roundTrip(target)?.displayName)
    }

    @Test
    fun `non-latin names and emoji survive`() {
        val target = LauncherContactTarget(
            action = LauncherContactAction.PROFILE,
            lookupKey = "0r9-3B",
            displayName = "Ольга 🌻",
        )
        assertEquals("Ольга 🌻", roundTrip(target)?.displayName)
    }

    @Test
    fun `a plus sign in a name is not swallowed by the encoding`() {
        // URL form-encoding maps space to '+', so a literal '+' must come back as a '+', not a space.
        val target = LauncherContactTarget(
            action = LauncherContactAction.DIAL,
            phoneNumber = "+380501234567",
            displayName = "A + B",
        )
        val decoded = roundTrip(target)
        assertEquals("A + B", decoded?.displayName)
        assertEquals("+380501234567", decoded?.phoneNumber)
    }

    @Test
    fun `every action round-trips`() {
        LauncherContactAction.entries.forEach { action ->
            val target = LauncherContactTarget(
                action = action,
                lookupKey = "key",
                phoneNumber = "123",
                messageDataId = 7L,
                messagePackage = "com.example.chat",
                displayName = "Someone",
            )
            assertEquals("action $action must survive", target, roundTrip(target))
        }
    }

    @Test
    fun `a truncated contact target decodes to null`() {
        assertNull(LauncherCellCommand.decode("contact:DIAL:key"))
    }

    @Test
    fun `an empty contact target decodes to null`() {
        assertNull(LauncherCellCommand.decode("contact:"))
    }

    @Test
    fun `an unknown action decodes to null`() {
        assertNull(LauncherCellCommand.decode("contact:VIDEO_CALL:a:b:1:c:d"))
    }

    @Test
    fun `a non-numeric data id decodes to null`() {
        assertNull(LauncherCellCommand.decode("contact:MESSAGE:a:b:not-a-number:c:d"))
    }

    @Test
    fun `an extra field decodes to null rather than being truncated silently`() {
        assertNull(LauncherCellCommand.decode("contact:DIAL:a:b:1:c:d:extra"))
    }

    @Test
    fun `a snapshot missing its own action's data is not usable`() {
        val dialWithoutNumber = LauncherContactTarget(action = LauncherContactAction.DIAL)
        val messageWithoutPackage = LauncherContactTarget(
            action = LauncherContactAction.MESSAGE,
            messageDataId = 3L,
        )
        assertEquals(false, dialWithoutNumber.isUsable)
        assertEquals(false, messageWithoutPackage.isUsable)
        assertEquals(
            true,
            LauncherContactTarget(action = LauncherContactAction.PROFILE, lookupKey = "k").isUsable,
        )
    }
}
