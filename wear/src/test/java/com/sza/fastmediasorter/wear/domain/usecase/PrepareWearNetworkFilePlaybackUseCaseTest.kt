package com.sza.fastmediasorter.wear.domain.usecase

import android.net.Uri
import com.sza.fastmediasorter.wear.domain.model.WearNetworkFileOpenRequest
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2694: a file row in a network walk must resolve to an id a player can read back.
 *
 * Before this use case the walk handed every row to the host's local resolution, which answers with
 * "nothing selected" for an address that is neither a `file://` uri nor a MediaStore id - so the
 * player opened empty and neither the compile nor any gate saw it.
 *
 * The SMB case is the one a scheme test silently gets wrong: its address is a bare share-relative
 * path carrying no scheme at all, and it is the protocol the owner's own share uses.
 */
class PrepareWearNetworkFilePlaybackUseCaseTest {

    private val selectedMediaManager = SelectedMediaManager()
    private val useCase = PrepareWearNetworkFilePlaybackUseCase(selectedMediaManager)

    @Test
    fun `an ftp row resolves to a playable id`() {
        assertResolves("ftp://192.168.1.10:21/music/track.mp3")
    }

    @Test
    fun `an sftp row resolves to a playable id`() {
        assertResolves("sftp://192.168.1.10:22/music/track.mp3")
    }

    @Test
    fun `an smb share-relative row resolves to a playable id`() {
        assertResolves("music/track.mp3")
    }

    @Test
    fun `a file at the smb share root resolves to a playable id`() {
        assertResolves("track.mp3")
    }

    @Test
    fun `the published entry carries the source and the address a player reconnects with`() {
        val target = useCase(requestFor("ftp://192.168.1.10:21/music/track.mp3"))

        val published = selectedMediaManager.getSelectedFileById(target.fileId)

        assertNotNull(published)
        assertTrue("a walk row was published as a local file", published!!.isNetworkSource)
        assertEquals("source-42", published.sourceId)
        assertEquals("ftp://192.168.1.10:21/music/track.mp3", published.streamUri)
        assertEquals("track.mp3", published.file.name)
    }

    /**
     * The walk re-reads a level on every ascent, so an id minted per listing would name a different
     * file after the wearer stepped up and back down. Two resolutions of the same address must agree.
     */
    @Test
    fun `the same address resolves to the same id twice`() {
        val first = useCase(requestFor("sftp://192.168.1.10:22/a.mp3")).fileId
        val second = useCase(requestFor("sftp://192.168.1.10:22/a.mp3")).fileId

        assertEquals(first, second)
    }

    @Test
    fun `two files in one level resolve to different ids`() {
        val first = useCase(requestFor("music/a.mp3")).fileId
        val second = useCase(requestFor("music/b.mp3")).fileId

        assertTrue("two addresses in one level collided on one id", first != second)
    }

    /**
     * The other half of the dispatch, asserted from this side only.
     *
     * The local branch cannot be exercised here: `PrepareWearFilePlaybackUseCase` calls
     * `Uri.fromFile`, which is an android.jar stub returning null under `isReturnDefaultValues`, and
     * this module carries no Robolectric (S2437). What is provable without it is that a walk row
     * resolved as network never lands in the holder wearing the local shape - a player reconnecting
     * to a share for a file already on the watch is the failure that costs a real device to see.
     */
    @Test
    fun `a network row never publishes as a local file`() {
        val target = useCase(requestFor("music/track.mp3"))

        val published = selectedMediaManager.getSelectedFileById(target.fileId)

        assertTrue("a network walk row was published as a local file", published!!.isNetworkSource)
        assertTrue("a network walk row reached a player with no source to reconnect to", published.sourceId != null)
    }

    private fun assertResolves(address: String) {
        val target = useCase(requestFor(address))

        assertNotNull(
            "$address did not resolve to an entry the players can read back",
            selectedMediaManager.getSelectedFileById(target.fileId)
        )
    }

    private fun requestFor(address: String): WearNetworkFileOpenRequest {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.toString() } returns address
        return WearNetworkFileOpenRequest(
            sourceId = "source-42",
            uri = uri,
            name = address.substringAfterLast('/'),
            mimeType = "audio/mpeg",
            sizeBytes = 1024L,
            dateModifiedEpochSeconds = 1_700_000_000L
        )
    }
}
