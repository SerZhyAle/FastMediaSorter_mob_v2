package com.sza.fastmediasorter.ui.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.data.local.db.StereoFormatOverrideDao
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.LocalPathResolution
import com.sza.fastmediasorter.domain.usecase.ResolveLocalPathFromUriUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StandaloneViewerNavigationTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var viewModel: StandalonePlayerViewModel

    @Before
    fun createPlayer() {
        Dispatchers.setMain(dispatcher)
        val scanner = mockk<LocalMediaScanner>(relaxed = true)
        val resolver = mockk<ResolveLocalPathFromUriUseCase>()
        val settings = mockk<SettingsRepository>()
        every { settings.getSettings() } returns flowOf(AppSettings(slideshowInterval = 8))
        coEvery { resolver(any()) } returns LocalPathResolution.Local("/folder/B.jpg", "/folder")
        coEvery { scanner.scanFolder(any(), any(), any(), any(), any(), any(), any()) } returns
            listOf(file("C"), file("A"), file("B"))
        viewModel = StandalonePlayerViewModel(
            context = mockk<Context>(relaxed = true),
            favoritesUseCase = mockk(relaxed = true),
            resolveLocalPathFromUriUseCase = resolver,
            materializeUriToFileUseCase = mockk(relaxed = true),
            localMediaScanner = scanner,
            settingsRepository = settings,
            searchLyricsUseCase = mockk(relaxed = true),
            stereoFormatOverrideDao = mockk<StereoFormatOverrideDao>(relaxed = true),
        )
        store.put("player", viewModel)
        viewModel.setHostSupportedTypes(setOf(MediaType.IMAGE))
    }

    @After
    fun clearPlayer() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun file(name: String): MediaFile = MediaFile(
        name = "$name.jpg",
        path = "/folder/$name.jpg",
        type = MediaType.IMAGE,
        size = 1L,
        createdDate = 0L,
    )

    private suspend fun openFolder() {
        viewModel.loadFromUri(Uri.parse("content://provider/B.jpg"), "image/jpeg", "B.jpg")
        viewModel.state.first { it.supportsFolderPaging }
    }

    @Test
    fun `external URI retains selected folder identity and steps clamp to list edges`() = runTest {
        openFolder()
        assertEquals(listOf("A.jpg", "B.jpg", "C.jpg"), viewModel.filesForSelection().map { it.name })
        assertEquals("/folder/B.jpg", viewModel.selectedFilePath())
        viewModel.pageByOffset(100)
        assertEquals("C.jpg", viewModel.state.value.mediaFile?.name)
        viewModel.pageByOffset(-100)
        assertEquals("A.jpg", viewModel.state.value.mediaFile?.name)
        viewModel.pageToIndex(1)
        assertEquals("B.jpg", viewModel.state.value.mediaFile?.name)
        viewModel.pageToEdge(last = true)
        assertEquals("C.jpg", viewModel.state.value.mediaFile?.name)
    }

    @Test
    fun `S reschedules a running slideshow and stops accelerating at one second`() = runTest {
        openFolder()
        assertTrue(viewModel.startOrAccelerateSlideshow(random = false))
        runCurrent()
        assertTrue(viewModel.state.value.isSlideshowActive)
        viewModel.startOrAccelerateSlideshow(random = false)
        runCurrent()
        val before = viewModel.state.value.mediaFile?.name
        advanceTimeBy(3_999L)
        runCurrent()
        assertEquals(before, viewModel.state.value.mediaFile?.name)
        advanceTimeBy(1L)
        runCurrent()
        assertNotEquals(before, viewModel.state.value.mediaFile?.name)
        repeat(10) { viewModel.startOrAccelerateSlideshow(random = false) }
        runCurrent()
        val atFloor = viewModel.state.value.mediaFile?.name
        advanceTimeBy(999L)
        runCurrent()
        assertEquals(atFloor, viewModel.state.value.mediaFile?.name)
        advanceTimeBy(1L)
        runCurrent()
        assertNotEquals(atFloor, viewModel.state.value.mediaFile?.name)
        viewModel.toggleSlideshow()
        assertFalse(viewModel.state.value.isSlideshowActive)
    }

    @Test
    fun `I chooses a different file and stopping prevents future auto advances`() = runTest {
        openFolder()
        assertTrue(viewModel.startOrAccelerateSlideshow(random = true))
        runCurrent()
        val original = viewModel.state.value.mediaFile?.name
        advanceTimeBy(8_000L)
        runCurrent()
        assertNotEquals(original, viewModel.state.value.mediaFile?.name)
        viewModel.toggleSlideshow()
        val stopped = viewModel.state.value.mediaFile?.name
        advanceTimeBy(16_000L)
        runCurrent()
        assertEquals(stopped, viewModel.state.value.mediaFile?.name)
    }
}
