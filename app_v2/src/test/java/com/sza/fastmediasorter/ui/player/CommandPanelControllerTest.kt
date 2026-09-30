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
import com.sza.fastmediasorter.ui.player.helpers.PlayerBindingSafeViews
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertFalse
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
class CommandPanelControllerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var binding: ActivityPlayerUnifiedBinding
    private lateinit var safeViews: PlayerBindingSafeViews
    private lateinit var controller: CommandPanelController

    @Before
    fun setUp() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_FastMediaSorter_App)
        binding = ActivityPlayerUnifiedBinding.inflate(LayoutInflater.from(context))
        safeViews = PlayerBindingSafeViews(binding)
        controller = CommandPanelController(
            binding = binding,
            settingsRepository = mockk<SettingsRepository>(relaxed = true),
            // Never advanced: async passes stay queued so each test judges the synchronous decision.
            coroutineScope = TestScope(StandardTestDispatcher()),
            callback = mockk(relaxed = true),
            mediaCapabilities = MediaCapabilities(
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
            ),
        )
    }

    private fun state(path: String = "/nonexistent/sample.jpg", enableMoving: Boolean = true) =
        PlayerViewModel.PlayerState(
            files = listOf(
                MediaFile(name = "sample.jpg", path = path, type = MediaType.IMAGE, size = 1L, createdDate = 0L)
            ),
            enableMoving = enableMoving,
        )

    private fun addDestinationButtons() {
        safeViews.copyToButtonsGrid.addView(View(binding.root.context))
        safeViews.moveToButtonsGrid.addView(View(binding.root.context))
    }

    @Test
    fun `copy and move are unavailable before the first state`() {
        addDestinationButtons()

        assertFalse(controller.isCopyAvailable())
        assertFalse(controller.isMoveAvailable())
    }

    @Test
    fun `copy availability matches the copy panel visibility`() {
        controller.updateCommandAvailability(state())
        assertFalse(controller.isCopyAvailable())
        assertFalse(safeViews.copyToPanel.isVisible)

        addDestinationButtons()
        controller.updateCommandAvailability(state())
        assertTrue(controller.isCopyAvailable())
        assertTrue(safeViews.copyToPanel.isVisible)
    }

    @Test
    fun `move availability follows write permission like the move panel`() {
        addDestinationButtons()

        controller.updateCommandAvailability(state())
        assertFalse(controller.isMoveAvailable())
        assertFalse(safeViews.moveToPanel.isVisible)

        val writable = tempFolder.newFile("writable.jpg").absolutePath
        controller.updateCommandAvailability(state(writable))
        assertTrue(controller.isMoveAvailable())
        assertTrue(safeViews.moveToPanel.isVisible)
    }

    @Test
    fun `disabled moving makes move unavailable for a writable file`() {
        addDestinationButtons()
        val writable = tempFolder.newFile("locked.jpg").absolutePath

        controller.updateCommandAvailability(state(writable, enableMoving = false))

        assertFalse(controller.isMoveAvailable())
        assertFalse(safeViews.moveToPanel.isVisible)
    }
}
