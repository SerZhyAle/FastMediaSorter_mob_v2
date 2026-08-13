package com.sza.fastmediasorter.ui.player

import android.content.Context
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.data.local.db.StereoFormatOverrideDao
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.FavoritesUseCase
import com.sza.fastmediasorter.domain.usecase.MaterializeUriToFileUseCase
import com.sza.fastmediasorter.domain.usecase.ResolveLocalPathFromUriUseCase
import com.sza.fastmediasorter.domain.usecase.SearchLyricsUseCase
import dagger.Lazy
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StandalonePlayerViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: StandalonePlayerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = StandalonePlayerViewModel(
            context = mockk<Context>(relaxed = true),
            favoritesUseCase = mockk<FavoritesUseCase>(relaxed = true),
            resolveLocalPathFromUriUseCase = mockk<ResolveLocalPathFromUriUseCase>(relaxed = true),
            materializeUriToFileUseCase = mockk<MaterializeUriToFileUseCase>(relaxed = true),
            localMediaScanner = mockk<LocalMediaScanner>(relaxed = true),
            settingsRepository = mockk<SettingsRepository>(relaxed = true),
            searchLyricsUseCase = mockk<Lazy<SearchLyricsUseCase>>(relaxed = true),
            stereoFormatOverrideDao = mockk<StereoFormatOverrideDao>(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onRenameComplete is no-op when mediaFile is null`() {
        assertNull(viewModel.state.value.mediaFile)

        viewModel.onRenameComplete(Uri.parse("content://document/renamed.pdf"), "renamed.pdf")

        assertNull(viewModel.state.value.mediaFile)
    }

    @Test
    fun `onRenameComplete updates mediaFile name and path`() {
        val originalUri = Uri.parse("content://document/original.pdf")
        viewModel.loadFromUri(originalUri, "application/pdf", "original.pdf")

        assertNotNull(viewModel.state.value.mediaFile)
        assertEquals("original.pdf", viewModel.state.value.mediaFile?.name)

        val newUri = Uri.parse("content://document/renamed.pdf")
        viewModel.onRenameComplete(newUri, "renamed.pdf")

        val updated = viewModel.state.value.mediaFile
        assertEquals("renamed.pdf", updated?.name)
        assertEquals("content://document/renamed.pdf", updated?.path)
        assertEquals("content://document/renamed.pdf", updated?.contentUri)
    }

    @Test
    fun `onRenameComplete preserves mediaFile type and size`() {
        val originalUri = Uri.parse("content://document/original.epub")
        viewModel.loadFromUri(originalUri, "application/epub+zip", "original.epub")

        val originalType = viewModel.state.value.mediaFile?.type
        assertNotNull(originalType)

        viewModel.onRenameComplete(
            Uri.parse("content://document/renamed.epub"),
            "renamed.epub"
        )

        val updated = viewModel.state.value.mediaFile
        assertEquals(originalType, updated?.type)
        assertEquals(0L, updated?.size)
    }
}
