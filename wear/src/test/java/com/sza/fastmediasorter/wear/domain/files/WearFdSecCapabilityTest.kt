package com.sza.fastmediasorter.wear.domain.files

import com.sza.fastmediasorter.wear.domain.model.WearFileOperationKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * S3383: when the file menu offers the FileDO pair, and what a viewed container leaves behind.
 *
 * The switch, the one-file rule and the write permission each withhold both entries on their own;
 * the file's name alone decides which of the two is offered.
 */
class WearFdSecCapabilityTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val useCase = WearFdSecUseCase()

    @Test
    fun `an ordinary file is offered encryption only`() {
        assertEquals(
            setOf(WearFileOperationKind.ENCRYPT_FILEDO),
            useCase.offerFor(singleName = "holiday.jpg", enabled = true, writable = true)
        )
    }

    @Test
    fun `a container is offered decryption only, whatever the case of its suffix`() {
        assertEquals(
            setOf(WearFileOperationKind.DECRYPT_FILEDO),
            useCase.offerFor(singleName = "holiday.FD-SEC", enabled = true, writable = true)
        )
    }

    @Test
    fun `the switch off withholds both`() {
        assertTrue(useCase.offerFor(singleName = "holiday.jpg", enabled = false, writable = true).isEmpty())
        assertTrue(useCase.offerFor(singleName = "holiday.fd-sec", enabled = false, writable = true).isEmpty())
    }

    @Test
    fun `a selection of more than one file withholds both`() {
        assertTrue(useCase.offerFor(singleName = null, enabled = true, writable = true).isEmpty())
    }

    @Test
    fun `a source that cannot be written beside withholds both`() {
        assertTrue(useCase.offerFor(singleName = "holiday.jpg", enabled = true, writable = false).isEmpty())
    }

    @Test
    fun `a shared folder takes a container only under Download and Documents`() {
        assertTrue(useCase.sharedFolderTakesContainer("Download/"))
        assertTrue(useCase.sharedFolderTakesContainer("Documents/letters/"))
        assertFalse(useCase.sharedFolderTakesContainer("DCIM/Camera/"))
        assertFalse(useCase.sharedFolderTakesContainer("Music/"))
        assertFalse(useCase.sharedFolderTakesContainer("Downloads/"))
        assertFalse("a row with no relative path is not reachable by one", useCase.sharedFolderTakesContainer(null))
    }

    @Test
    fun `discarding empties the viewing workspace and nothing beside it`() = runTest {
        val cacheRoot = folder.root
        val workspace = useCase.openWorkspace(cacheRoot)
        File(workspace, "1").mkdirs()
        File(workspace, "1/recovered.jpg").writeText("plaintext")
        val neighbour = File(cacheRoot, "thumbnails").apply { mkdirs() }

        useCase.discardOpened(cacheRoot)

        assertTrue(workspace.listFiles().orEmpty().isEmpty())
        assertTrue(neighbour.isDirectory)
        assertFalse(File(workspace, "1/recovered.jpg").exists())
    }
}
