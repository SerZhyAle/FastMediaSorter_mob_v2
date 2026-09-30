package com.sza.fastmediasorter.ui.cloudfolders

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sza.fastmediasorter.data.cloud.AuthResult
import com.sza.fastmediasorter.data.cloud.CloudFile
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
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
class OneDriveFolderPickerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val oneDriveClient: OneDriveRestClient = mockk()
    private val resourceRepository: ResourceRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val addResourceUseCase: AddResourceUseCase = mockk()
    private val savedStateHandle = SavedStateHandle()

    private lateinit var viewModel: OneDriveFolderPickerViewModel

    @Before
    fun setUp() {
        coEvery { oneDriveClient.authenticate() } returns AuthResult.Success("test", "{}")
        viewModel = OneDriveFolderPickerViewModel(
            context = context,
            oneDriveClient = oneDriveClient,
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
                CloudFile(id = "sub_folder_id", name = "OneDrive Sub Folder", path = "/sub", isFolder = true)
            )
        )

        coEvery { oneDriveClient.listFolders("root") } coAnswers { slowDeferred.await() }
        coEvery { oneDriveClient.listFolders("sub_id") } returns fastResult

        // Initial load (root)
        viewModel.loadFolders()

        // Navigate into subfolder before initial load completes
        viewModel.navigateIntoFolder(CloudFolderItem(id = "sub_id", name = "Sub", mimeType = null, isSelected = false))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.folders.size)
        assertEquals("OneDrive Sub Folder", viewModel.state.value.folders[0].name)

        // Complete the slow initial request late
        slowDeferred.complete(
            CloudResult.Success(
                listOf(
                    CloudFile(id = "root_folder_id", name = "OneDrive Root Folder", path = "/", isFolder = true)
                )
            )
        )
        advanceUntilIdle()

        // Stale result should NOT overwrite
        assertEquals(1, viewModel.state.value.folders.size)
        assertEquals("OneDrive Sub Folder", viewModel.state.value.folders[0].name)
        assertFalse(viewModel.state.value.isLoading)
    }
}
