package com.sza.fastmediasorter.ui.browse

import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.SavedStateHandle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.UiState
import com.sza.fastmediasorter.data.network.exceptions.WifiRequiredException
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * Unit coverage for [BrowseViewModel]'s own coordination logic - construction/state exposure,
 * error-message mapping and selection coordination. Delegate `Browse*Manager` classes each carry
 * their own dedicated test (`BrowseFileListManagerTest`, `BrowseNavigationManagerNavigateToDepthTest`,
 * etc.) - this file does not re-test them, only the wiring that lives directly in the ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    /**
     * `BrowseInlineAudioManager` -> `AudioFocusManager` casts the system-service lookup directly to
     * `AudioManager` (no `as?`) - a relaxed `Context` mock returns a generic relaxed `Object` for an
     * unstubbed `getSystemService(String)`, which throws `ClassCastException` at construction time.
     * Returned (not just built inline) so error-mapping tests can keep a reference for `verify`.
     */
    private fun mockContext(): Context {
        val context = mockk<Context>(relaxed = true)
        every { context.getSystemService(Context.AUDIO_SERVICE) } returns mockk<AudioManager>(relaxed = true)
        return context
    }

    /**
     * Builds a [BrowseViewModel] with every dependency holder relaxed-mocked. Two Flow-returning
     * calls are stubbed explicitly rather than left to the relaxed default: both are collected via
     * `.first()` during `init {}` (the eager `settings` field and `BrowseLifecycleSetupManager`'s
     * `restoreFilterState()`), and an unstubbed relaxed mock does not reliably behave like a
     * one-shot-emitting Flow, so `.first()` can throw. `contentDiscovery.getResourcesUseCase.getById`
     * is left unstubbed by default - its nullable return type resolves `null` on a relaxed mock,
     * which `BrowseResourceLoadManager.loadResource()` already treats as a clean "resource not found"
     * branch (event + `setLoading(false)`, no throw). `contentDiscovery` is a parameter (not built
     * internally, mirroring `context`) so error-mapping tests can pre-stub `getById` to throw before
     * construction - `loadResource()` runs from `init {}` inside `scope.launch(ioDispatcher +
     * exceptionHandler)` with no surrounding try/catch, so a thrown exception there is the natural,
     * already-wired path into the protected `handleError`/`resolveFriendlyBrowseErrorRes`.
     */
    private fun createViewModel(
        context: Context = mockContext(),
        contentDiscovery: BrowseContentDiscoveryDependencies = mockk(relaxed = true)
    ): BrowseViewModel {
        val remoteAccess = mockk<BrowseRemoteAccessDependencies>(relaxed = true)
        val cleanupUseCases = mockk<BrowseCleanupUseCases>(relaxed = true)
        val persistedState = mockk<BrowsePersistedStateDependencies>(relaxed = true)
        val contentAuthoringUseCases = mockk<BrowseContentAuthoringUseCases>(relaxed = true)
        val fileMutation = mockk<BrowseFileMutationDependencies>(relaxed = true)

        every { fileMutation.settingsRepository.getSettings() } returns flowOf(AppSettings())
        every { persistedState.browseStateDataStore.filter } returns flowOf(null)

        return BrowseViewModel(
            context = context,
            remoteAccess = remoteAccess,
            cleanupUseCases = cleanupUseCases,
            contentDiscovery = contentDiscovery,
            persistedState = persistedState,
            contentAuthoringUseCases = contentAuthoringUseCases,
            fileMutation = fileMutation,
            ioDispatcher = dispatcherRule.testDispatcher,
            savedStateHandle = SavedStateHandle(mapOf("resourceId" to 1L))
        )
    }

    private fun mediaFile(name: String, createdDate: Long): MediaFile {
        return MediaFile(
            name = name,
            path = "/$name",
            type = MediaType.IMAGE,
            size = createdDate * 100,
            createdDate = createdDate
        )
    }

    @Test
    fun `construction does not throw and settles to Empty state`() = runTest(dispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(false, viewModel.loading.value)
        assertTrue(viewModel.state.value.mediaFiles.isEmpty())
        assertEquals(UiState.Empty, viewModel.fileListUiState.value)
    }

    @Test
    fun `settings StateFlow starts with default AppSettings`() = runTest(dispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(AppSettings(), viewModel.settings.value)
    }

    @Test
    fun `markListAsSubmitted stores the list on lastEmittedMediaFiles`() = runTest(dispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val files = listOf(mediaFile("a.jpg", 1L), mediaFile("b.jpg", 2L))

        viewModel.markListAsSubmitted(files)

        assertEquals(files, viewModel.lastEmittedMediaFiles)
    }

    // -- Error mapping (protected handleError -> resolveFriendlyBrowseErrorRes,
    // BrowseViewModel.kt:543-595) - exercised through the real init-time path: `loadResource()` runs
    // from `init {}` inside `scope.launch(ioDispatcher + exceptionHandler)` with no surrounding
    // try/catch, so making `getResourcesUseCase.getById` throw reaches `handleError` exactly the way
    // production code would. A relaxed `context.getString(any())` returns "" regardless of id, so
    // these tests verify the *id passed to the mock*, not `viewModel.error.value` - the only way to
    // distinguish branches.

    @Test
    fun `handleError maps WifiRequiredException to wifi-required message`() = runTest(dispatcherRule.testDispatcher) {
        val context = mockContext()
        val contentDiscovery = mockk<BrowseContentDiscoveryDependencies>(relaxed = true)
        coEvery { contentDiscovery.getResourcesUseCase.getById(any()) } throws WifiRequiredException()

        createViewModel(context, contentDiscovery)
        advanceUntilIdle()

        verify(exactly = 1) { context.getString(R.string.error_wifi_required_smb) }
    }

    @Test
    fun `handleError maps auth-failure to the auth-failed message`() = runTest(dispatcherRule.testDispatcher) {
        val context = mockContext()
        val contentDiscovery = mockk<BrowseContentDiscoveryDependencies>(relaxed = true)
        coEvery { contentDiscovery.getResourcesUseCase.getById(any()) } throws IOException("Authentication failed")

        createViewModel(context, contentDiscovery)
        advanceUntilIdle()

        verify(exactly = 1) { context.getString(R.string.friendly_copy_error_auth_failed) }
    }

    @Test
    fun `handleError maps a timeout message to the network-timeout message`() = runTest(dispatcherRule.testDispatcher) {
        val context = mockContext()
        val contentDiscovery = mockk<BrowseContentDiscoveryDependencies>(relaxed = true)
        coEvery { contentDiscovery.getResourcesUseCase.getById(any()) } throws IOException("Connection timed out")

        createViewModel(context, contentDiscovery)
        advanceUntilIdle()

        verify(exactly = 1) { context.getString(R.string.error_network_timeout) }
    }

    @Test
    fun `handleError maps an unrecognized message to the fallback message`() = runTest(dispatcherRule.testDispatcher) {
        val context = mockContext()
        val contentDiscovery = mockk<BrowseContentDiscoveryDependencies>(relaxed = true)
        coEvery { contentDiscovery.getResourcesUseCase.getById(any()) } throws IOException("some unmapped detail")

        createViewModel(context, contentDiscovery)
        advanceUntilIdle()

        verify(exactly = 1) { context.getString(R.string.friendly_copy_error_generic) }
    }

    // -- Selection coordination (selectFile/selectFileRange/selectAll/clearSelection) --

    @Test
    fun `selectFile toggles a path into and out of currentSelectedPaths`() = runTest(dispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.replaceMediaFiles(listOf(mediaFile("a.jpg", 1L), mediaFile("b.jpg", 2L), mediaFile("c.jpg", 3L)))

        viewModel.selectFile("/a.jpg")
        assertEquals(setOf("/a.jpg"), viewModel.currentSelectedPaths())

        viewModel.selectFile("/a.jpg")
        assertTrue(viewModel.currentSelectedPaths().isEmpty())
    }

    @Test
    fun `selectAll selects every seeded path`() = runTest(dispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val files = listOf(mediaFile("a.jpg", 1L), mediaFile("b.jpg", 2L), mediaFile("c.jpg", 3L))
        viewModel.replaceMediaFiles(files)

        viewModel.selectAll()

        assertEquals(files.map { it.path }.toSet(), viewModel.currentSelectedPaths())
    }

    @Test
    fun `clearSelection after selectAll empties currentSelectedPaths`() = runTest(dispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.replaceMediaFiles(listOf(mediaFile("a.jpg", 1L), mediaFile("b.jpg", 2L)))
        viewModel.selectAll()

        viewModel.clearSelection()

        assertTrue(viewModel.currentSelectedPaths().isEmpty())
    }

    @Test
    fun `selectFileRange selects the contiguous range from the last anchor`() = runTest(dispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val files = listOf(
            mediaFile("a.jpg", 1L),
            mediaFile("b.jpg", 2L),
            mediaFile("c.jpg", 3L),
            mediaFile("d.jpg", 4L)
        )
        viewModel.replaceMediaFiles(files)
        // BrowseSelectionManager.selectRange needs a prior anchor (lastSelectedPath) - without one it
        // just selects the given path alone, so selectFile("/a.jpg") establishes the anchor first.
        viewModel.selectFile("/a.jpg")

        viewModel.selectFileRange("/c.jpg")

        assertEquals(setOf("/a.jpg", "/b.jpg", "/c.jpg"), viewModel.currentSelectedPaths())
    }

    // -- replaceMediaFiles state mutation --

    @Test
    fun `replaceMediaFiles updates mediaFiles and totalFileCount together`() = runTest(dispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val files = listOf(mediaFile("a.jpg", 1L), mediaFile("b.jpg", 2L), mediaFile("c.jpg", 3L))

        viewModel.replaceMediaFiles(files)

        assertEquals(files, viewModel.state.value.mediaFiles)
        assertEquals(3, viewModel.state.value.totalFileCount)
    }
}
