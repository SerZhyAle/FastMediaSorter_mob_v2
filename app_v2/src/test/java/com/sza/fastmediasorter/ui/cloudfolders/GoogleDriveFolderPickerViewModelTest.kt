package com.sza.fastmediasorter.ui.cloudfolders

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sza.fastmediasorter.data.cloud.AuthResult
import com.sza.fastmediasorter.data.cloud.CloudFile
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GoogleDriveFolderPickerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val googleDriveClient: GoogleDriveRestClient = mockk()
    private val resourceRepository: ResourceRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val addResourceUseCase: AddResourceUseCase = mockk()
    private val savedStateHandle = SavedStateHandle()

    private lateinit var viewModel: GoogleDriveFolderPickerViewModel

    @Before
    fun setUp() {
        coEvery { googleDriveClient.authenticate() } returns AuthResult.Success("test", "{}")
        viewModel = GoogleDriveFolderPickerViewModel(
            context = context,
            googleDriveClient = googleDriveClient,
            resourceRepository = resourceRepository,
            addResourceUseCase = addResourceUseCase,
            settingsRepository = settingsRepository,
            savedStateHandle = savedStateHandle
        )
    }

    @Test
    fun `loadFolders queries nested folderId when in nested path`() = runTest {
        coEvery { googleDriveClient.listFolders(null) } returns CloudResult.Success(
            listOf(CloudFile(id = "root_child", name = "Root Child", path = "/root_child", isFolder = true))
        )
        coEvery { googleDriveClient.listFolders("nested_123") } returns CloudResult.Success(
            listOf(CloudFile(id = "nested_child", name = "Nested Child", path = "/nested_child", isFolder = true))
        )

        // Navigate into nested folder
        viewModel.navigateIntoFolder(
            CloudFolderItem(id = "nested_123", name = "Nested Folder", mimeType = null, isSelected = false)
        )
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.currentPath.size)
        assertEquals("nested_123", viewModel.state.value.currentPath.last().id)
        assertEquals(1, viewModel.state.value.folders.size)
        assertEquals("Nested Child", viewModel.state.value.folders[0].name)

        // Swipe refresh / reload in nested folder queries the nested folder id, not root
        viewModel.loadFolders()
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.folders.size)
        assertEquals("Nested Child", viewModel.state.value.folders[0].name)
    }

    @Test
    fun `loadFolders cancels previous request and does not overwrite newer folder list`() = runTest {
        val slowDeferred = CompletableDeferred<CloudResult<List<CloudFile>>>()
        val fastResult = CloudResult.Success(
            listOf(
                CloudFile(id = "sub_folder_id", name = "GDrive Sub Folder", path = "/sub", isFolder = true)
            )
        )

        coEvery { googleDriveClient.listFolders(null) } coAnswers { slowDeferred.await() }
        coEvery { googleDriveClient.listFolders("sub_id") } returns fastResult

        viewModel.loadFolders()

        viewModel.navigateIntoFolder(CloudFolderItem(id = "sub_id", name = "Sub", mimeType = null, isSelected = false))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.folders.size)
        assertEquals("GDrive Sub Folder", viewModel.state.value.folders[0].name)

        slowDeferred.complete(
            CloudResult.Success(
                listOf(
                    CloudFile(id = "root_folder_id", name = "GDrive Root Folder", path = "/", isFolder = true)
                )
            )
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.folders.size)
        assertEquals("GDrive Sub Folder", viewModel.state.value.folders[0].name)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `navigateBack pops path and reloads parent folder`() = runTest {
        coEvery { googleDriveClient.listFolders(null) } returns CloudResult.Success(
            listOf(CloudFile(id = "root_folder", name = "Root Folder", path = "/root", isFolder = true))
        )
        coEvery { googleDriveClient.listFolders("sub_id") } returns CloudResult.Success(
            listOf(CloudFile(id = "sub_child", name = "Sub Child", path = "/sub_child", isFolder = true))
        )

        viewModel.navigateIntoFolder(CloudFolderItem(id = "sub_id", name = "Sub", mimeType = null, isSelected = false))
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.currentPath.size)

        val handled = viewModel.navigateBack()
        assertTrue(handled)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.currentPath.size)
        assertEquals("Root Folder", viewModel.state.value.folders[0].name)
        assertFalse(viewModel.state.value.canGoBack)
    }
}
