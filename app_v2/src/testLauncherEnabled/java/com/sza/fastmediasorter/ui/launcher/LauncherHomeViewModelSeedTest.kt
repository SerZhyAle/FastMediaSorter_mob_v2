package com.sza.fastmediasorter.ui.launcher

import androidx.lifecycle.SavedStateHandle
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.testing.MainDispatcherRule
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTrayComposition
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S1680: every settings-derived flow of [LauncherHomeViewModel] must start from the domain defaults
 * rather than from a copied literal, so a changed default cannot leave a seed behind rendering the
 * previous one.
 *
 * The storage flow deliberately never emits, which parks each flow on its seed - the same window the
 * launcher spends its first ~100 ms in on a cold start.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherHomeViewModelSeedTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val defaults = AppSettings()

    private fun viewModel(): LauncherHomeViewModel {
        val settingsRepository = mockk<SettingsRepository>()
        every { settingsRepository.getSettings() } returns MutableSharedFlow<AppSettings>()
        return LauncherHomeViewModel(
            visibility = mockk(relaxed = true),
            desktopDependencies = mockk(relaxed = true),
            taskbarDependencies = mockk(relaxed = true),
            shortcutDependencies = mockk(relaxed = true),
            cellMenuDependencies = mockk(relaxed = true),
            executeCommand = mockk(relaxed = true),
            settingsRepository = settingsRepository,
            observeStreams = mockk(relaxed = true),
            executeScheduledOperation = mockk(relaxed = true),
            isCameraWallpaperAvailable = mockk(relaxed = true),
            savedStateHandle = SavedStateHandle(),
        )
    }

    @Test
    fun `scalar seeds equal the domain defaults`() {
        val viewModel = viewModel()

        assertEquals(defaults.launcherDensityFactor, viewModel.densityFactor.value, 0f)
        assertEquals(defaults.launcherDesktopLocked, viewModel.desktopLocked.value)
        assertEquals(defaults.launcherReplaceSystemStatusArea, viewModel.replaceSystemStatusArea.value)
        assertEquals(
            defaults.launcherTaskbarPlacement == AppSettings.LAUNCHER_TASKBAR_PLACEMENT_TOP,
            viewModel.taskbarAtTop.value,
        )
    }

    @Test
    fun `composed seeds equal what the defaults would render`() {
        val viewModel = viewModel()

        assertEquals(
            defaults.launcherTopStatusStripMode && defaults.launcherReplaceSystemStatusArea,
            viewModel.topStatusStripMode.value,
        )
        assertEquals(
            defaults.launcherReplaceSystemStatusArea && !defaults.launcherTopStatusStripMode,
            viewModel.taskbarTrayContentVisible.value,
        )
        assertEquals(LauncherTrayComposition.from(defaults), viewModel.trayComposition.value)
        // The seed cannot probe the disk, so it resolves as if the stored image were unavailable - which
        // decides nothing while the default mode is not IMAGE.
        assertEquals(
            resolveLauncherWallpaper(
                mode = defaults.launcherWallpaperMode,
                imagePath = defaults.launcherWallpaperImagePath,
                imageAvailable = false,
                cameraId = defaults.launcherWallpaperCameraId,
                cameraAvailable = false,
            ),
            viewModel.wallpaper.value,
        )
    }
}
