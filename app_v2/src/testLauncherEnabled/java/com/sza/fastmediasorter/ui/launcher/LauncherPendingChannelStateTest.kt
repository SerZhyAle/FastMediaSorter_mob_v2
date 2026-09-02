package com.sza.fastmediasorter.ui.launcher

import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactAction
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactChannel
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S2102: the channel list has to survive a process kill intact or not at all - a partially readable
 * list would offer the user rows that place nothing, which is the failure the ticket removes.
 */
@Suppress("FunctionNaming") // backtick test names, project convention
class LauncherPendingChannelStateTest {

    @Test
    fun `a stored channel list reads back unchanged`() {
        val channels = listOf(channel(dataId = 11L, label = "Message +7 999 000"))

        assertEquals(channels, decodePendingChannels(labels(channels), targets(channels)))
    }

    @Test
    fun `a label carrying the codec separator survives the round trip`() {
        // The label is whatever the messaging app wrote on the contact's row, so a colon in it is
        // ordinary data - it is stored beside the encoded target rather than joined to it for this.
        val channels = listOf(channel(dataId = 12L, label = "Bob: work"))

        val restored = decodePendingChannels(labels(channels), targets(channels))

        assertEquals("Bob: work", restored?.single()?.label)
        assertEquals(channels, restored)
    }

    @Test
    fun `two halves of different lengths read back as nothing pending`() {
        val channels = listOf(channel(dataId = 13L), channel(dataId = 14L))

        assertNull(decodePendingChannels(listOf("only one label"), targets(channels)))
    }

    @Test
    fun `one undecodable target discards the whole list`() {
        val channels = listOf(channel(dataId = 15L), channel(dataId = 16L))
        val corrupted = targets(channels).toMutableList().apply { this[1] = "not a cell command" }

        assertNull(decodePendingChannels(labels(channels), corrupted))
    }

    @Test
    fun `an empty list reads as no channel step in flight`() {
        assertNull(decodePendingChannels(emptyList(), emptyList()))
    }

    @Test
    fun `an absent key reads as no channel step in flight`() {
        assertNull(decodePendingChannels(null, null))
    }

    private fun channel(dataId: Long, label: String = "Message"): LauncherContactChannel =
        LauncherContactChannel(
            target = LauncherContactTarget(
                action = LauncherContactAction.MESSAGE,
                messageDataId = dataId,
                messagePackage = "com.example.messenger",
                displayName = "Bob",
            ),
            label = label,
        )

    private fun labels(channels: List<LauncherContactChannel>): List<String> = channels.map { it.label }

    private fun targets(channels: List<LauncherContactChannel>): List<String> =
        channels.map { LauncherCellCommand.Contact(it.target).encode() }
}
