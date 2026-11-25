package com.sza.fastmediasorter.data.ticket

import android.content.Context
import com.sza.fastmediasorter.domain.model.ticket.TicketDestination
import com.sza.fastmediasorter.domain.model.ticket.TicketOutboxState
import io.mockk.every
import io.mockk.mockk
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * S4806: the destination of a staged packet is read from `state.json` by an explicit key first and by the
 * older resource key second, so a packet written by any earlier version leaves by the path it was staged for.
 * S4808: the same holds for the waiting state, stored under its pre-rename name by earlier versions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TicketOutboxStoreDestinationTest {

    @get:Rule val temp = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var store: TicketOutboxStore

    @Before
    fun setUp() {
        filesDir = temp.newFolder("files")
        val context = mockk<Context>()
        every { context.filesDir } returns filesDir
        store = TicketOutboxStore(context, mockk(relaxed = true))
    }

    private fun writePacket(id: String, extraState: String = "", state: String = "QUEUED"): File {
        val folder = File(File(filesDir, TicketOutboxStore.ROOT_DIR), id).apply { mkdirs() }
        File(folder, "manifest.json").writeText("{}")
        File(folder, "state.json").writeText(
            """{"createdAtEpochMs":1,"title":"t","project":"","state":"$state","tickets":[],"failure":"","files":[]""" +
                "$extraState}"
        )
        return folder
    }

    private fun destinationOf(id: String): TicketDestination =
        store.list().single { it.folder.name == id }.entry.destination

    private fun stateOf(folder: File) = JSONObject(File(folder, "state.json").readText())

    @Test
    fun `a state file with no destination and no resource key reads as Drive`() {
        writePacket("a1")
        assertEquals(TicketDestination.Drive, destinationOf("a1"))
    }

    @Test
    fun `a state file with a resource key reads as that folder`() {
        writePacket("a2", ""","inboxResourceId":7""")
        assertEquals(TicketDestination.Folder(7L), destinationOf("a2"))
    }

    @Test
    fun `a state file with the exchange-direct key reads as the direct channel`() {
        writePacket("a3", ""","destination":"exchange-direct"""")
        assertEquals(TicketDestination.ExchangeDirect, destinationOf("a3"))
    }

    @Test
    fun `a rewritten entry keeps the keys earlier versions read`() {
        val drive = writePacket("a4")
        val folder = writePacket("a5", ""","inboxResourceId":7""")
        val direct = writePacket("a6", ""","destination":"exchange-direct"""")
        store.list().forEach { packet -> store.update(packet, packet.entry) }

        val driveState = stateOf(drive)
        assertFalse(driveState.has("destination"))
        assertFalse(driveState.has("inboxResourceId"))

        val folderState = stateOf(folder)
        assertEquals(7L, folderState.getLong("inboxResourceId"))
        assertFalse(folderState.has("destination"))

        val directState = stateOf(direct)
        assertEquals("exchange-direct", directState.getString("destination"))
        assertFalse(directState.has("inboxResourceId"))
    }

    @Test
    fun `a state file written before the waiting rename reads as waiting and is rewritten with the new name`() {
        val folder = writePacket("a7", ""","inboxResourceId":7""", state = "WAITING_ON_DRIVE")
        val packet = store.list().single { it.folder.name == "a7" }
        assertEquals(TicketOutboxState.WAITING, packet.entry.state)

        store.update(packet, packet.entry)
        assertEquals("WAITING", stateOf(folder).getString("state"))
    }

    @Test
    fun `an unknown state name still reads as queued`() {
        writePacket("a8", state = "NOT_A_STATE")
        assertEquals(TicketOutboxState.QUEUED, store.list().single { it.folder.name == "a8" }.entry.state)
    }
}
