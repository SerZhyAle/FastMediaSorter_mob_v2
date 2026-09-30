package com.sza.fastmediasorter.ui.settings.helpers

import android.net.Uri
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * S3887: the import loop sees the rows it writes itself, so a file naming one server or one path twice
 * reuses the row, and a cancelled import cancels instead of reporting a failure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class SzaResourcesImporterTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val resourceRepository = mockk<ResourceRepository>(relaxed = true)
    private val credentialsRepository = mockk<NetworkCredentialsRepository>(relaxed = true)
    private val importer = SzaResourcesImporter(
        context = RuntimeEnvironment.getApplication(),
        resourceRepository = resourceRepository,
        credentialsRepository = credentialsRepository,
        getDestinationsUseCase = mockk<GetDestinationsUseCase>(relaxed = true),
        mediaCapabilities = mockk<MediaCapabilities>(relaxed = true),
    )

    init {
        coEvery { resourceRepository.getAllResourcesSync() } returns emptyList()
        every { credentialsRepository.getAllCredentials() } returns flowOf(emptyList())
        coEvery { credentialsRepository.insert(any()) } returns 1L
        coEvery { resourceRepository.addResource(any()) } returns 1L
    }

    @Test
    fun `two shares of one server and user create one credential row`() = runBlocking {
        val result = importer.importFromUri(
            shareFile(entry("A", "smb://10.0.0.5/media/a"), entry("B", "smb://10.0.0.5/media/b")),
        )

        assertEquals(SzaResourcesImporter.ImportResult.Success(imported = 2, updated = 0, skipped = 0), result)
        coVerify(exactly = 1) { credentialsRepository.insert(any<NetworkCredentialsEntity>()) }
    }

    @Test
    fun `two entries with one path create one resource and update it`() = runBlocking {
        val result = importer.importFromUri(
            shareFile(entry("A", "smb://10.0.0.5/media/a"), entry("A2", "smb://10.0.0.5/media/a")),
        )

        assertEquals(SzaResourcesImporter.ImportResult.Success(imported = 1, updated = 1, skipped = 0), result)
        coVerify(exactly = 1) { resourceRepository.addResource(any<MediaResource>()) }
        coVerify(exactly = 1) { resourceRepository.updateResource(match { it.name == "A2" && it.id == 1L }) }
    }

    @Test
    fun `a cancelled write cancels the import instead of skipping the entry`() {
        coEvery { resourceRepository.addResource(any()) } throws CancellationException("stopped")
        var thrown: Throwable? = null

        try {
            runBlocking { importer.importFromUri(shareFile(entry("A", "smb://10.0.0.5/media/a"))) }
        } catch (e: CancellationException) {
            thrown = e
        }

        assertTrue(thrown is CancellationException)
    }

    private fun entry(name: String, path: String): String =
        """<resource name="$name" path="$path" type="SMB" username="user" />"""

    private fun shareFile(vararg entries: String): Uri {
        val file = File(folder.root, "share.xml")
        file.writeText("<media-resources>${entries.joinToString("")}</media-resources>")
        return Uri.fromFile(file)
    }
}
