package com.sza.fastmediasorter.ui.player

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.player.helpers.CommandPanelLayoutPlanner
import com.sza.fastmediasorter.ui.player.helpers.PlayerBigButtonsModeManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerBindingSafeViews
import com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommandPanelAvailabilityUpdaterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var binding: ActivityPlayerUnifiedBinding
    private lateinit var safeViews: PlayerBindingSafeViews
    private val capabilities = MediaCapabilities(
        supportsVideo = true,
        supportsAudio = true,
        supportsImages = true,
        supportsDocuments = true,
        supportsEpub = true,
        supportsCloud = false,
        supportsLocalNetworkSources = false,
        supportsDefaultPlayer = false,
        supportsCast = false,
        supportsMicRecording = false,
        supportsVrPlayer = false,
        supportsWearCompanion = false,
    )

    // Never advanced: the async permission probe and settings pass stay queued, so each test judges
    // the synchronous availability decision alone.
    private val scope = TestScope(StandardTestDispatcher())
    private var cachedState: PlayerViewModel.PlayerState? = null
    private var overflowCommands: List<CommandPanelLayoutPlanner.PlayerCommand>? = null
    private var bigButtonsBarCommands: List<CommandPanelLayoutPlanner.PlayerCommand>? = null

    @Before
    fun setUp() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_FastMediaSorter_App)
        binding = ActivityPlayerUnifiedBinding.inflate(LayoutInflater.from(context))
        safeViews = PlayerBindingSafeViews(binding)
    }

    private fun updater(landscape: Boolean = true) = CommandPanelAvailabilityUpdater(
        binding = binding,
        safeViews = safeViews,
        planner = CommandPanelLayoutPlanner(capabilities),
        mediaCapabilities = capabilities,
        bigButtonsModeManager = PlayerBigButtonsModeManager(binding.root.context),
        settingsRepository = mockk<SettingsRepository>(relaxed = true),
        coroutineScope = scope,
        bigButtonsMode = false,
        getIsLandscapeMode = { landscape },
        getCastMediaManager = { null },
        getAllowVrLaunch = { false },
        shouldShowRandomNavigation = { false },
        isWifiConnected = { false },
        getOverflowableButtons = { emptyList() },
        barViewForCommand = { null },
        resolveAvailableCenterWidthPx = { 0 },
        resolveBigButtonsTopPanelSlotCount = { 0 },
        bigButtonsFixedButtons = { emptyList() },
        updateBigButtonsTopPanelContentDescriptions = {},
        updateSlideshowButtonColor = {},
        syncBigButtonsTopPanelLayout = {},
        onCachedStateChange = { cachedState = it },
        getLastKnownFavoriteVisible = { false },
        setLastKnownFavoriteVisible = {},
        getLastKnownAllowSeparateWindow = { false },
        setLastKnownAllowSeparateWindow = {},
        setLatestBigButtonsBarCommands = { bigButtonsBarCommands = it },
        setLatestOverflowCommands = { overflowCommands = it },
        reTriggerUpdate = {},
    )

    private fun file(type: MediaType, path: String = "/nonexistent/sample") =
        MediaFile(name = "sample", path = path, type = type, size = 1L, createdDate = 0L)

    private fun state(
        file: MediaFile?,
        block: PlayerViewModel.PlayerState.() -> PlayerViewModel.PlayerState = { this },
    ) = PlayerViewModel.PlayerState(files = listOfNotNull(file)).block()

    private fun addDestinationButtons() {
        safeViews.copyToButtonsGrid.addView(View(binding.root.context))
        safeViews.moveToButtonsGrid.addView(View(binding.root.context))
    }

    @Test
    fun `a state without a current file is cached and changes nothing else`() {
        val empty = state(null)

        updater().update(empty)

        assertSame(empty, cachedState)
        assertNull(overflowCommands)
    }

    @Test
    fun `a hidden panel hides navigation, overflow and destination panels`() {
        addDestinationButtons()

        updater().update(state(file(MediaType.IMAGE)) { copy(showCommandPanel = false) })

        assertFalse(binding.btnBack.isVisible)
        assertFalse(safeViews.btnOverflowMenu.isVisible)
        assertFalse(safeViews.copyToPanel.isVisible)
        assertFalse(safeViews.moveToPanel.isVisible)
        assertEquals(emptyList<CommandPanelLayoutPlanner.PlayerCommand>(), overflowCommands)
        assertEquals(emptyList<CommandPanelLayoutPlanner.PlayerCommand>(), bigButtonsBarCommands)
    }

    @Test
    fun `an audio file forces the panel even when the state hides it`() {
        updater().update(state(file(MediaType.AUDIO)) { copy(showCommandPanel = false) })

        assertTrue(binding.btnBack.isVisible)
        assertTrue(binding.btnNextCmd.isVisible)
    }

    @Test
    fun `copy panel needs destination buttons`() {
        updater().update(state(file(MediaType.IMAGE)))
        assertFalse(safeViews.copyToPanel.isVisible)

        addDestinationButtons()
        updater().update(state(file(MediaType.IMAGE)))
        assertTrue(safeViews.copyToPanel.isVisible)
    }

    @Test
    fun `move panel needs write permission while copy panel does not`() {
        addDestinationButtons()

        updater().update(state(file(MediaType.IMAGE)))

        assertTrue(safeViews.copyToPanel.isVisible)
        assertFalse(safeViews.moveToPanel.isVisible)
    }

    @Test
    fun `move panel shows for a writable file`() {
        addDestinationButtons()
        val writable = tempFolder.newFile("writable.jpg")

        updater().update(state(file(MediaType.IMAGE, writable.absolutePath)))

        assertTrue(safeViews.moveToPanel.isVisible)
    }

    @Test
    fun `an active image edit overlay keeps both destination panels hidden`() {
        addDestinationButtons()
        val writable = tempFolder.newFile("editing.jpg")

        updater().update(
            state(file(MediaType.IMAGE, writable.absolutePath)) { copy(imageEditMode = PlayerImageEditMode.DRAW) }
        )

        assertFalse(safeViews.copyToPanel.isVisible)
        assertFalse(safeViews.moveToPanel.isVisible)
    }

    @Test
    fun `disabled copying and moving hide their panels`() {
        addDestinationButtons()
        val writable = tempFolder.newFile("disabled.jpg")

        updater().update(
            state(file(MediaType.IMAGE, writable.absolutePath)) { copy(enableCopying = false, enableMoving = false) }
        )

        assertFalse(safeViews.copyToPanel.isVisible)
        assertFalse(safeViews.moveToPanel.isVisible)
    }
}
