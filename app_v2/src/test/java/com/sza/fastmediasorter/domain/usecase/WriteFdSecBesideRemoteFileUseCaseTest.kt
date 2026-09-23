package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.data.cloud.CloudDownloadUseCase
import com.sza.fastmediasorter.data.security.fdsec.FdSecCredentialRepository
import com.sza.fastmediasorter.data.transfer.SiblingFolderResolver
import com.sza.fastmediasorter.domain.model.FdSecResult
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.transfer.SiblingFolder
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class WriteFdSecBesideRemoteFileUseCaseTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    private lateinit var share: File
    private lateinit var cache: File
    private lateinit var useCase: WriteFdSecBesideRemoteFileUseCase

    @Before
    fun setUp() {
        share = folder.newFolder("share")
        cache = folder.newFolder("cache")
        val context = mockk<Context> { every { cacheDir } returns cache }
        val network = mockk<DownloadNetworkFileUseCase>()
        // The share directory stands in for the server: a network path names a file in it.
        coEvery { network.execute(any(), any(), any()) } answers {
            File(share, firstArg<String>().substringAfterLast('/')).copyTo(secondArg(), overwrite = true)
            true
        }
        val resolver = mockk<SiblingFolderResolver> {
            coEvery { folderBeside(any(), any()) } returns DirectoryFolder(share)
        }
        useCase = WriteFdSecBesideRemoteFileUseCase(
            context = context,
            localize = LocalizeFdSecContainerUseCase(
                context = context,
                downloadNetworkFile = Lazy { network },
                cloudDownload = Lazy { mockk<CloudDownloadUseCase>() },
            ),
            secureFile = SecureFileToFdSecUseCase(),
            unsecureFile = UnsecureFdSecFileUseCase(mockk<FdSecCredentialRepository>(relaxed = true)),
            siblingFolders = resolver,
            place = PlaceVerifiedFileBesideUseCase(),
        )
    }

    @Test
    fun `a remote file goes out as a container and comes back byte for byte under its name`() = runTest {
        File(share, "holiday.png").writeBytes(ORIGINAL)

        val packed = useCase.encrypt(remote("holiday.png"), null, CREDENTIAL.toCharArray())
        assertEquals(FdSecResult.Placed("holiday.fd-sec"), packed)

        File(share, "holiday.png").renameTo(File(share, "moved-away.png"))
        val restored = useCase.decrypt(remote("holiday.fd-sec"), null, CREDENTIAL.toCharArray())

        assertEquals(FdSecResult.Placed("holiday.png"), restored)
        assertArrayEquals(ORIGINAL, File(share, "holiday.png").readBytes())
        assertArrayEquals(ORIGINAL, File(share, "moved-away.png").readBytes())
    }

    @Test
    fun `a restore beside the untouched original is suffixed, never an overwrite`() = runTest {
        File(share, "holiday.png").writeBytes(ORIGINAL)
        useCase.encrypt(remote("holiday.png"), null, CREDENTIAL.toCharArray())

        val restored = useCase.decrypt(remote("holiday.fd-sec"), null, CREDENTIAL.toCharArray())

        assertEquals(FdSecResult.Placed("holiday-1.png"), restored)
        assertArrayEquals(ORIGINAL, File(share, "holiday-1.png").readBytes())
    }

    @Test
    fun `a wrong credential writes nothing beside the container`() = runTest {
        File(share, "holiday.png").writeBytes(ORIGINAL)
        useCase.encrypt(remote("holiday.png"), null, CREDENTIAL.toCharArray())
        val before = share.list()?.toSet()

        val restored = useCase.decrypt(remote("holiday.fd-sec"), null, "not it".toCharArray())

        assertEquals(FdSecResult.WrongCredentialOrTamper, restored)
        assertEquals(before, share.list()?.toSet())
    }

    @Test
    fun `no staging copy survives an operation`() = runTest {
        File(share, "holiday.png").writeBytes(ORIGINAL)

        useCase.encrypt(remote("holiday.png"), null, CREDENTIAL.toCharArray())
        useCase.decrypt(remote("holiday.fd-sec"), null, CREDENTIAL.toCharArray())

        assertTrue(File(cache, "fdsec-place").listFiles().isNullOrEmpty())
    }

    @Test
    fun `the pair is offered for local, document-tree, network and cloud paths`() {
        assertTrue(WriteFdSecBesideRemoteFileUseCase.canWriteBeside("/storage/emulated/0/a.png"))
        assertTrue(WriteFdSecBesideRemoteFileUseCase.canWriteBeside("content://provider/tree/x/document/y"))
        assertTrue(WriteFdSecBesideRemoteFileUseCase.canWriteBeside("smb://nas/share/a.png"))
        assertTrue(WriteFdSecBesideRemoteFileUseCase.canWriteBeside("sftp://host/a.png"))
        assertTrue(WriteFdSecBesideRemoteFileUseCase.canWriteBeside("ftp://host/a.png"))
        assertTrue(WriteFdSecBesideRemoteFileUseCase.canWriteBeside("cloud://google_drive/abc"))
    }

    private fun remote(name: String) = MediaFile(
        name = name,
        path = "smb://nas/share/$name",
        type = MediaType.IMAGE,
        size = 0L,
        createdDate = MODIFIED_AT,
    )

    /** A local directory as the remote folder; an address is the child's absolute path. */
    private class DirectoryFolder(private val root: File) : SiblingFolder {

        override suspend fun contains(name: String): Boolean = File(root, name).exists()

        override suspend fun write(source: File, name: String): String {
            val target = File(root, name)
            if (target.exists()) throw IOException("taken")
            source.copyTo(target)
            return target.absolutePath
        }

        override suspend fun read(address: String, target: File) {
            File(address).copyTo(target, overwrite = true)
        }

        override suspend fun rename(address: String, newName: String): String {
            val target = File(root, newName)
            if (target.exists() || !File(address).renameTo(target)) throw IOException("refused")
            return target.absolutePath
        }

        override suspend fun delete(address: String) {
            if (!File(address).delete()) throw IOException("refused")
        }
    }

    private companion object {
        const val CREDENTIAL = "correct horse"
        const val MODIFIED_AT = 1_700_000_000_000L
        val ORIGINAL = ByteArray(9_000) { (it * 7).toByte() }
    }
}
