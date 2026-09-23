package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.data.cloud.CloudDownloadUseCase
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalizeFdSecContainerUseCaseTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    private val network = mockk<DownloadNetworkFileUseCase>()
    private val cloud = mockk<CloudDownloadUseCase>()
    private val useCase = LocalizeFdSecContainerUseCase(
        context = mockk<Context>(),
        downloadNetworkFile = Lazy { network },
        cloudDownload = Lazy { cloud },
    )

    @Test
    fun `a local container is read in place and never copied`() = runTest {
        val target = File(folder.root, "source")

        val result = useCase("/storage/emulated/0/Download/holiday.fd-sec", target)

        assertEquals(File("/storage/emulated/0/Download/holiday.fd-sec"), result)
        assertFalse(target.exists())
        coVerify(exactly = 0) { network.execute(any(), any(), any()) }
    }

    @Test
    fun `a network container is downloaded into the target directory`() = runTest {
        val target = File(folder.root, "source")
        coEvery { network.execute("smb://nas/share/holiday.fd-sec", any(), any()) } answers {
            secondArg<File>().writeBytes(ByteArray(CONTAINER_BYTES))
            true
        }

        val result = useCase("smb://nas/share/holiday.fd-sec", target)

        assertEquals(target, result?.parentFile)
        assertEquals(CONTAINER_BYTES.toLong(), result?.length())
    }

    @Test
    fun `a failed network download yields nothing`() = runTest {
        coEvery { network.execute(any(), any(), any()) } returns false

        assertNull(useCase("sftp://host/holiday.fd-sec", File(folder.root, "source")))
    }

    @Test
    fun `an empty download is not a container`() = runTest {
        coEvery { network.execute(any(), any(), any()) } answers {
            secondArg<File>().createNewFile()
            true
        }

        assertNull(useCase("ftp://host/holiday.fd-sec", File(folder.root, "source")))
    }

    @Test
    fun `a cloud download found under the provider's own name is still used`() = runTest {
        val target = File(folder.root, "source")
        coEvery { cloud.downloadToPublic(any(), any(), any(), any()) } answers {
            File(secondArg<String>(), "holiday.fd-sec").writeBytes(ByteArray(CONTAINER_BYTES))
            true
        }

        val result = useCase("cloud://drive/holiday.fd-sec", target)

        assertEquals(File(target, "holiday.fd-sec"), result)
    }

    @Test
    fun `an original is copied under the name it is given, even when empty`() = runTest {
        val target = File(folder.root, "source")
        coEvery { network.execute("smb://nas/share/holiday.png", any(), any()) } answers {
            secondArg<File>().createNewFile()
            true
        }

        val result = useCase.copyOf("smb://nas/share/holiday.png", target, "holiday.png")

        assertEquals(File(target, "holiday.png"), result)
        assertEquals(0L, result?.length())
    }

    @Test
    fun `a source with no fetch primitive yields nothing`() = runTest {
        assertNull(useCase("https://example.com/holiday.fd-sec", File(folder.root, "source")))
    }

    private companion object {
        const val CONTAINER_BYTES = 5632
    }
}
