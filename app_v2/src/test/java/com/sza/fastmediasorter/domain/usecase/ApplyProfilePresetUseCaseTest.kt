package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.core.xr.VrProfileSettingsSync
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.data.preset.DeviceProfilePresetApplier
import com.sza.fastmediasorter.data.preset.DeviceProfilePresetCsvDataSource
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import com.sza.fastmediasorter.domain.model.ScreenshotGestureSettings
import com.sza.fastmediasorter.domain.model.StreamDefaultSort
import com.sza.fastmediasorter.domain.repository.DeviceProfileRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApplyProfilePresetUseCaseTest {

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val profileRepository = mockk<DeviceProfileRepository>(relaxed = true)
    private val dataSource = mockk<DeviceProfilePresetCsvDataSource>()
    private val vrProfileSettingsSync = mockk<VrProfileSettingsSync>()

    // Real applier with a relaxed Context: the coercion paths exercised here never touch the
    // PackageManager (only the `true_if_capable` branch does).
    // S1216: the launcher seam reports available, so launcher rows are exercised; the
    // unavailable-build case builds its own applier below.
    private val launcherMode = mockk<LauncherModeContract> { every { isAvailableInBuild } returns true }
    private val applier = DeviceProfilePresetApplier(mockk<Context>(relaxed = true), launcherMode)

    private fun applierWithoutLauncher(): DeviceProfilePresetApplier {
        val seam = mockk<LauncherModeContract> { every { isAvailableInBuild } returns false }
        return DeviceProfilePresetApplier(mockk<Context>(relaxed = true), seam)
    }

    private lateinit var useCase: ApplyProfilePresetUseCase

    @Before
    fun setup() {
        useCase = ApplyProfilePresetUseCase(
            dataSource,
            applier,
            settingsRepository,
            profileRepository,
            vrProfileSettingsSync,
            // S1216: real counter over the same mocked data source, so overrideCount() and apply()
            // are proven to read one map rather than two.
            CountProfilePresetOverridesUseCase(dataSource),
        )
        coEvery { vrProfileSettingsSync.align(any(), any()) } answers { secondArg() }
    }

    // S0876: applySettingsOnly() now writes through the transform overload (mutex-serialized in the
    // real repository) - each test captures the transform and applies it to a starting AppSettings()
    // itself (mirrors the old getSettings() stub, which always returned AppSettings() by default).

    @Test
    fun `applies overrides for a profile and persists the updated settings`() = runTest {
        // A representative slice of TV_MEDIA_BOX overrides: a boolean, an int, an enum, and the
        // colorTheme BLACK -> DARK mapping.
        every { dataSource.load() } returns mapOf(
            DeviceProfileType.TV_MEDIA_BOX to linkedMapOf(
                "preventSleep" to "TRUE",
                "networkParallelism" to "4",
                "defaultSortMode" to "DATE_DESC",
                "colorTheme" to "BLACK"
            )
        )
        coEvery { profileRepository.updatePresetApplied(1) } returns Result.success(Unit)

        val transform = slot<suspend (AppSettings) -> AppSettings>()
        coEvery { settingsRepository.updateSettings(capture(transform)) } returns Unit

        val result = useCase.apply(DeviceProfileType.TV_MEDIA_BOX, presetVersion = 1)
        val saved = transform.captured(AppSettings())

        assertTrue(result.isSuccess)
        assertTrue(saved.preventSleep)
        assertEquals(4, saved.networkParallelism)
        assertEquals(com.sza.fastmediasorter.domain.model.SortMode.DATE_DESC, saved.defaultSortMode)
        assertEquals("DARK", saved.colorTheme)
        coVerify(exactly = 1) { vrProfileSettingsSync.align(DeviceProfileType.TV_MEDIA_BOX, any()) }
        coVerify(exactly = 1) { settingsRepository.updateSettings(any<suspend (AppSettings) -> AppSettings>()) }
        coVerify(exactly = 1) { profileRepository.updatePresetApplied(1) }
    }

    @Test
    fun `applies newly-covered preset fields (bool, enum, string-set, string)`() = runTest {
        every { dataSource.load() } returns mapOf(
            DeviceProfileType.VR_HEADSET to linkedMapOf(
                "smbEnabled" to "FALSE",
                "googleDriveEnabled" to "FALSE",
                "gestureOverlayEnabled" to "TRUE",
                "playerFollowSystemRotation" to "TRUE",
                "copyScreenshotToClipboard" to "TRUE",
                // S1216: the pre-S0847 alias `screenshotGestureActionDown` was removed with its
                // dead branch (ADR-3); the band is now addressed by its own field name.
                "screenshotGestureLeftTopDown" to "OPEN_IN_PLAYER",
                "enabledShareTargets" to "email;telegram",
                "disabledShareTargets" to "print",
                "videoSnapshotFormat" to "PNG"
            )
        )
        coEvery { profileRepository.updatePresetApplied(1) } returns Result.success(Unit)

        val transform = slot<suspend (AppSettings) -> AppSettings>()
        coEvery { settingsRepository.updateSettings(capture(transform)) } returns Unit

        val result = useCase.apply(DeviceProfileType.VR_HEADSET, presetVersion = 1)
        val saved = transform.captured(AppSettings())

        assertTrue(result.isSuccess)
        assertEquals(false, saved.smbEnabled)
        assertEquals(false, saved.googleDriveEnabled)
        assertTrue(saved.gestureOverlayEnabled)
        assertTrue(saved.playerFollowSystemRotation)
        assertTrue(saved.copyScreenshotToClipboard)
        assertEquals(ScreenshotGestureAction.OPEN_IN_PLAYER, saved.screenshotGesture.leftTopDown)
        assertEquals(setOf("email", "telegram"), saved.enabledShareTargets)
        assertEquals(setOf("print"), saved.disabledShareTargets)
        assertEquals("PNG", saved.videoSnapshotFormat)
    }

    @Test
    fun `applies enableStreams per device profile (S0575)`() = runTest {
        every { dataSource.load() } returns mapOf(
            DeviceProfileType.TV_MEDIA_BOX to linkedMapOf("enableStreams" to "TRUE"),
            DeviceProfileType.PHOTO_FRAME to linkedMapOf("enableStreams" to "FALSE")
        )
        coEvery { profileRepository.updatePresetApplied(1) } returns Result.success(Unit)

        val transformTv = slot<suspend (AppSettings) -> AppSettings>()
        coEvery { settingsRepository.updateSettings(capture(transformTv)) } returns Unit
        assertTrue(useCase.apply(DeviceProfileType.TV_MEDIA_BOX, presetVersion = 1).isSuccess)
        assertTrue(transformTv.captured(AppSettings()).enableStreams)

        val transformFrame = slot<suspend (AppSettings) -> AppSettings>()
        coEvery { settingsRepository.updateSettings(capture(transformFrame)) } returns Unit
        assertTrue(useCase.apply(DeviceProfileType.PHOTO_FRAME, presetVersion = 1).isSuccess)
        assertEquals(false, transformFrame.captured(AppSettings()).enableStreams)
    }

    @Test
    fun `does not apply state or credential fields even when present in the csv`() = runTest {
        // Defaults of these fields, to assert they survive a profile apply unchanged.
        val defaults = AppSettings()
        every { dataSource.load() } returns mapOf(
            DeviceProfileType.HOME_TABLET to linkedMapOf(
                "preventSleep" to "TRUE", // one real override so the use case persists
                "defaultUser" to "should-not-apply",
                "defaultPassword" to "should-not-apply",
                "lastUsedResourceId" to "999",
                "enableStatistics" to "TRUE"
            )
        )
        coEvery { profileRepository.updatePresetApplied(1) } returns Result.success(Unit)

        val transform = slot<suspend (AppSettings) -> AppSettings>()
        coEvery { settingsRepository.updateSettings(capture(transform)) } returns Unit

        val result = useCase.apply(DeviceProfileType.HOME_TABLET, presetVersion = 1)
        val saved = transform.captured(AppSettings())

        assertTrue(result.isSuccess)
        assertTrue(saved.preventSleep)
        assertEquals(defaults.defaultUser, saved.defaultUser)
        assertEquals(defaults.defaultPassword, saved.defaultPassword)
        assertEquals(defaults.lastUsedResourceId, saved.lastUsedResourceId)
        assertEquals(defaults.enableStatistics, saved.enableStatistics)
    }

    @Test
    fun `skips an unknown enum value and keeps the current field value`() = runTest {
        val defaults = AppSettings()
        every { dataSource.load() } returns mapOf(
            DeviceProfileType.HOME_TABLET to linkedMapOf(
                // S1216: must name a field that still has a branch, otherwise this would pass as an
                // unknown-field skip and stop testing the unparseable-value path it is named for.
                "screenshotGestureLeftTopDown" to "NOT_A_REAL_ACTION"
            )
        )
        coEvery { profileRepository.updatePresetApplied(1) } returns Result.success(Unit)

        val transform = slot<suspend (AppSettings) -> AppSettings>()
        coEvery { settingsRepository.updateSettings(capture(transform)) } returns Unit

        val result = useCase.apply(DeviceProfileType.HOME_TABLET, presetVersion = 1)
        val saved = transform.captured(AppSettings())

        assertTrue(result.isSuccess)
        assertEquals(defaults.screenshotGesture.leftTopDown, saved.screenshotGesture.leftTopDown)
    }

    @Test
    fun `applies identity transform and skips preset marker when no overrides and vr sync is noop`() = runTest {
        every { dataSource.load() } returns mapOf(
            DeviceProfileType.OTHER to emptyMap()
        )

        val transform = slot<suspend (AppSettings) -> AppSettings>()
        coEvery { settingsRepository.updateSettings(capture(transform)) } returns Unit

        val result = useCase.apply(DeviceProfileType.OTHER, presetVersion = 1)
        val saved = transform.captured(AppSettings())

        assertTrue(result.isSuccess)
        // S0876: the use case now always routes through the transform overload; "skip when nothing
        // changed" moved into the repository (S0018 idempotency guard), so the observable contract
        // here is an identity transform plus no preset-marker write - not "no call at all".
        assertEquals(AppSettings(), saved)
        coVerify(exactly = 1) { settingsRepository.updateSettings(any<suspend (AppSettings) -> AppSettings>()) }
        coVerify(exactly = 0) { profileRepository.updatePresetApplied(any()) }
    }

    @Test
    fun `persists vr sync changes even when csv overrides are empty`() = runTest {
        every { dataSource.load() } returns mapOf(
            DeviceProfileType.OTHER to emptyMap()
        )
        coEvery {
            vrProfileSettingsSync.align(DeviceProfileType.OTHER, any())
        } answers {
            secondArg<AppSettings>().copy(disable3dVr = true)
        }

        val transform = slot<suspend (AppSettings) -> AppSettings>()
        coEvery { settingsRepository.updateSettings(capture(transform)) } returns Unit

        val result = useCase.apply(DeviceProfileType.OTHER, presetVersion = 1)
        val saved = transform.captured(AppSettings())

        assertTrue(result.isSuccess)
        assertTrue(saved.disable3dVr)
        coVerify(exactly = 1) { settingsRepository.updateSettings(any<suspend (AppSettings) -> AppSettings>()) }
        coVerify(exactly = 0) { profileRepository.updatePresetApplied(any()) }
    }

    // ── S1216: branches added for the preset matrix coverage gate ───────────────

    @Test
    fun `override count equals the number of non-empty cells the apply would write`() = runTest {
        every { dataSource.load() } returns mapOf(
            DeviceProfileType.HOME_TABLET to linkedMapOf(
                "preventSleep" to "TRUE",
                "smbEnabled" to "FALSE",
                "colorTheme" to ""
            )
        )

        assertEquals(2, useCase.overrideCount(DeviceProfileType.HOME_TABLET))
        assertEquals(0, useCase.overrideCount(DeviceProfileType.CAR_HEAD_UNIT))
    }

    @Test
    fun `enum branch keeps the current value on an unknown name`() {
        val current = AppSettings(streamsDefaultSort = StreamDefaultSort.RECENT)

        val result = applier.applyOverride(current, "streamsDefaultSort", "NOT_AN_ENUM_CONSTANT")

        assertEquals(StreamDefaultSort.RECENT, result.streamsDefaultSort)
    }

    @Test
    fun `enum branch applies a known name`() {
        val result = applier.applyOverride(AppSettings(), "streamsCatalogRefreshPolicy", "MANUAL")

        assertEquals("MANUAL", result.streamsCatalogRefreshPolicy.name)
    }

    @Test
    fun `launcher field applies when the home surface is compiled in`() {
        val result = applier.applyOverride(AppSettings(), "launcherDesktopLocked", "TRUE")

        assertTrue(result.launcherDesktopLocked)
    }

    @Test
    fun `launcher field is ignored when the build has no home surface`() {
        val result = applierWithoutLauncher().applyOverride(AppSettings(), "launcherDesktopLocked", "TRUE")

        assertEquals(AppSettings().launcherDesktopLocked, result.launcherDesktopLocked)
    }

    @Test
    fun `launcher wallpaper mode rejects a token outside the known set`() {
        val result = applier.applyOverride(AppSettings(), "launcherWallpaperMode", "NEON_GRADIENT")

        assertEquals(AppSettings().launcherWallpaperMode, result.launcherWallpaperMode)
    }

    @Test
    fun `secureSensitiveScreens applies in both directions`() {
        val off = applier.applyOverride(AppSettings(secureSensitiveScreens = true), "secureSensitiveScreens", "FALSE")
        val on = applier.applyOverride(AppSettings(secureSensitiveScreens = false), "secureSensitiveScreens", "TRUE")

        assertEquals(false, off.secureSensitiveScreens)
        assertEquals(true, on.secureSensitiveScreens)
    }

    @Test
    fun `per-zone gesture action applies to its own band`() {
        val result = applier.applyOverride(AppSettings(), "screenshotGestureRightBottomUp", "DO_NOT_USE")

        assertEquals(ScreenshotGestureAction.DO_NOT_USE, result.screenshotGesture.rightBottomUp)
    }

    @Test
    fun `gesture payload cell is skipped - it is a registered non-presettable pointer`() {
        val current = AppSettings(screenshotGesture = ScreenshotGestureSettings(payloadLeftTopDown = "kept"))

        val result = applier.applyOverride(current, "screenshotGesturePayloadLeftTopDown", "com.example/Activity")

        assertEquals("kept", result.screenshotGesture.payloadLeftTopDown)
    }
}
