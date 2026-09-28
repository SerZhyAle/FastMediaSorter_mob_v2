package com.sza.fastmediasorter.ui.cloudfolders

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sza.fastmediasorter.data.cloud.AuthResult
import com.sza.fastmediasorter.data.cloud.CloudFile
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.AddResourceUseCase
import com.sza.fastmediasorter.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DropboxFolderPickerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dropboxClient: DropboxClient = mockk()
    private val resourceRepository: ResourceRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val addResourceUseCase: AddResourceUseCase = mockk()
    private val savedStateHandle = SavedStateHandle()

    private lateinit var viewModel: DropboxFolderPickerViewModel

    @Before
    fun setUp() {
        coEvery { dropboxClient.authenticate() } returns AuthResult.Success("test", "{}")
        viewModel = DropboxFolderPickerViewModel(
            context = context,
            dropboxClient = dropboxClient,
            resourceRepository = resourceRepository,
            settingsRepository = settingsRepository,
            addResourceUseCase = addResourceUseCase,
            savedStateHandle = savedStateHandle
        )
    }

    @Test
    fun `loadFolders cancels previous request and does not overwrite newer folder list`() = runTest {
        val slowDeferred = CompletableDeferred<CloudResult<List<CloudFile>>>()
        val fastResult = CloudResult.Success(
            listOf(
                CloudFile(id = "/sub/folderA", name = "Folder A", path = "/sub/folderA", isFolder = true)
            )
        )

        coEvery { dropboxClient.listFolders("") } coAnswers { slowDeferred.await() }
        coEvery { dropboxClient.listFolders("/sub") } returns fastResult

        // Initial load (root - folderId = "")
        viewModel.loadFolders()

        // Navigate into subfolder before initial load completes
        viewModel.navigateIntoFolder(CloudFolderItem(id = "/sub", name = "Sub", mimeType = null, isSelected = false))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.folders.size)
        assertEquals("Folder A", viewModel.state.value.folders[0].name)

        // Complete the slow initial request late
        slowDeferred.complete(
            CloudResult.Success(
                listOf(
                    CloudFile(id = "/rootFolder", name = "Root Folder", path = "/rootFolder", isFolder = true)
                )
            )
        )
        advanceUntilIdle()

        // Should STILL be Folder A from the newer request
        assertEquals(1, viewModel.state.value.folders.size)
        assertEquals("Folder A", viewModel.state.value.folders[0].name)
        assertFalse(viewModel.state.value.isLoading)
    }
}
