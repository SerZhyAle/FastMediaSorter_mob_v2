package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.core.launcher.LauncherSectionCatalog
import com.sza.fastmediasorter.data.launcher.LiveContactDataSource
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.networkmonitor.NetworkMonitorContract
import com.sza.fastmediasorter.domain.radio.RadioControlContract
import com.sza.fastmediasorter.domain.radio.RadioKind
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S1745: verifies that section titles update immediately on language change without app restart,
 * while user-defined custom section labels (labelOverride) remain untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResolveLauncherDesktopUseCaseTest {

    private val app = RuntimeEnvironment.getApplication()
    private val desktopRepository = mockk<LauncherDesktopRepository>()
    private val radioControl = mockk<RadioControlContract>()
    private val liveContactDataSource = mockk<LiveContactDataSource>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val networkMonitorContract = mockk<NetworkMonitorContract>()

    private val resolveVisual = ResolveLauncherCommandLabelUseCase(
        context = app,
        resourceRepository = mockk(relaxed = true),
        streamSourceRepository = mockk(relaxed = true),
        scheduledOperationRepository = mockk(relaxed = true),
        resourceIconProvider = mockk(relaxed = true),
        appShortcutDataSource = mockk(relaxed = true),
        liveContactDataSource = liveContactDataSource,
        faviconAtlasStore = mockk(relaxed = true),
    )

    private val useCase = ResolveLauncherDesktopUseCase(
        desktopRepository = desktopRepository,
        resolveVisual = resolveVisual,
        radioControl = radioControl,
        liveContactDataSource = liveContactDataSource,
        settingsRepository = settingsRepository,
        networkMonitorContract = networkMonitorContract,
    )

    @Test
    fun `system section title updates when language changes`() = runTest {
        val sectionCell = LauncherCell(
            id = 1L,
            orientation = LauncherOrientation.PORTRAIT,
            rowIndex = 0,
            colIndex = 0,
            spanW = 4,
            spanH = 1,
            kind = LauncherCellKind.SECTION,
            target = LauncherCellCommand.Section(LauncherCellCommand.SECTION_MAIN).encode(),
            labelOverride = null,
            addedAt = 0L,
        )

        val settingsFlow = MutableStateFlow(AppSettings(language = "en"))
        every { desktopRepository.observeCells(LauncherOrientation.PORTRAIT) } returns flowOf(listOf(sectionCell))
        every { radioControl.state(RadioKind.WIFI) } returns flowOf(null)
        every { radioControl.state(RadioKind.BLUETOOTH) } returns flowOf(null)
        every { liveContactDataSource.changes() } returns flowOf(Unit)
        every { settingsRepository.getSettings() } returns settingsFlow
        every { networkMonitorContract.isAvailableInBuild } returns false

        val initialResolved = useCase(LauncherOrientation.PORTRAIT).first()
        assertEquals(1, initialResolved.size)
        assertEquals("Main", initialResolved[0].visual?.label)

        settingsFlow.value = AppSettings(language = "ru")
        val ruResolved = useCase(LauncherOrientation.PORTRAIT).first()
        assertEquals(1, ruResolved.size)
        assertEquals("Главное", ruResolved[0].visual?.label)
    }

    @Test
    fun `user custom section label is preserved across language changes`() = runTest {
        val customSectionCell = LauncherCell(
            id = 2L,
            orientation = LauncherOrientation.PORTRAIT,
            rowIndex = 0,
            colIndex = 0,
            spanW = 4,
            spanH = 1,
            kind = LauncherCellKind.SECTION,
            target = LauncherCellCommand.Section(LauncherCellCommand.SECTION_MAIN).encode(),
            labelOverride = "Мои любимые инструменты",
            addedAt = 0L,
        )

        val settingsFlow = MutableStateFlow(AppSettings(language = "en"))
        every { desktopRepository.observeCells(LauncherOrientation.PORTRAIT) } returns flowOf(listOf(customSectionCell))
        every { radioControl.state(RadioKind.WIFI) } returns flowOf(null)
        every { radioControl.state(RadioKind.BLUETOOTH) } returns flowOf(null)
        every { liveContactDataSource.changes() } returns flowOf(Unit)
        every { settingsRepository.getSettings() } returns settingsFlow
        every { networkMonitorContract.isAvailableInBuild } returns false

        val initialResolved = useCase(LauncherOrientation.PORTRAIT).first()
        assertEquals("Мои любимые инструменты", initialResolved[0].visual?.label)

        settingsFlow.value = AppSettings(language = "uk")
        val ukResolved = useCase(LauncherOrientation.PORTRAIT).first()
        assertEquals("Мои любимые инструменты", ukResolved[0].visual?.label)
    }

    /**
     * S1742: a section the user created carries a key this build's catalogue has never heard of, so the
     * resolver answers nothing for it. Before this ticket the caption was consulted only on top of a
     * resolved visual, and the header drew "unavailable" under any name.
     */
    @Test
    fun `a user created section renders its own name`() = runTest {
        val userSection = sectionCell(
            id = 3L,
            target = LauncherCellCommand.Section(LauncherSectionCatalog.mintUserKey(1_700_000_000_000L)).encode(),
            labelOverride = "Работа",
        )
        stubDesktop(userSection)

        val resolved = useCase(LauncherOrientation.PORTRAIT).first()

        assertEquals(1, resolved.size)
        assertEquals("Работа", resolved[0].visual?.label)
    }

    /**
     * The other half of the same rule: the caption is what rescues an unknown key, so without one the
     * header stays unresolvable. A blanket "sections always resolve" would hide a genuinely broken cell.
     */
    @Test
    fun `a section with an unknown key and no name stays unresolved`() = runTest {
        val nameless = sectionCell(
            id = 4L,
            target = LauncherCellCommand.Section(LauncherSectionCatalog.mintUserKey(1_700_000_000_001L)).encode(),
            labelOverride = null,
        )
        stubDesktop(nameless)

        val resolved = useCase(LauncherOrientation.PORTRAIT).first()

        assertEquals(1, resolved.size)
        assertNull(resolved[0].visual)
    }

    private fun sectionCell(id: Long, target: String, labelOverride: String?) = LauncherCell(
        id = id,
        orientation = LauncherOrientation.PORTRAIT,
        rowIndex = 0,
        colIndex = 0,
        spanW = 4,
        spanH = 1,
        kind = LauncherCellKind.SECTION,
        target = target,
        labelOverride = labelOverride,
        addedAt = 0L,
    )

    private fun stubDesktop(cell: LauncherCell) {
        every { desktopRepository.observeCells(LauncherOrientation.PORTRAIT) } returns flowOf(listOf(cell))
        every { radioControl.state(RadioKind.WIFI) } returns flowOf(null)
        every { radioControl.state(RadioKind.BLUETOOTH) } returns flowOf(null)
        every { liveContactDataSource.changes() } returns flowOf(Unit)
        every { settingsRepository.getSettings() } returns MutableStateFlow(AppSettings(language = "en"))
        every { networkMonitorContract.isAvailableInBuild } returns false
    }
}
