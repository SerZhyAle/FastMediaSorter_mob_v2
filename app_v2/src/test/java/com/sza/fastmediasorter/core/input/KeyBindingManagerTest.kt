package com.sza.fastmediasorter.core.input

import com.sza.fastmediasorter.data.input.InputBindingRepository
import com.sza.fastmediasorter.domain.input.BindingSource
import com.sza.fastmediasorter.domain.input.InputBinding
import com.sza.fastmediasorter.domain.input.InputSurface
import com.sza.fastmediasorter.domain.input.InputTrigger
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyBindingManagerTest {

    private val triggerPause = InputTrigger.Key(62, 0)
    private val triggerSpeed = InputTrigger.Key(85, 0)
    private val triggerGamepad = InputTrigger.GamepadButton(96)
    private val triggerUnknown = InputTrigger.Key(9999, 0)

    private val defaults = listOf(
        InputBinding("playback.pause_play", triggerPause, BindingSource.DEFAULT),
        InputBinding("playback.speed_up", triggerSpeed, BindingSource.DEFAULT),
        InputBinding("playback.stop", triggerGamepad, BindingSource.DEFAULT)
    )

    private val withOverride = listOf(
        InputBinding("playback.navigate_next", triggerPause, BindingSource.OVERRIDE),
        InputBinding("playback.speed_up", triggerSpeed, BindingSource.DEFAULT),
        InputBinding("playback.stop", triggerGamepad, BindingSource.DEFAULT)
    )

    @Test
    fun `resolve returns overridden commandId when trigger is remapped`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = mockk<InputBindingRepository>()
            every { repo.observeResolvedBindings() } returns flowOf(withOverride)
            val manager = KeyBindingManager(repo, this)
            assertEquals("playback.navigate_next", manager.resolve(triggerPause, InputSurface.PLAYER))
        }

    @Test
    fun `resolve returns default commandId for non-overridden trigger`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = mockk<InputBindingRepository>()
            every { repo.observeResolvedBindings() } returns flowOf(defaults)
            val manager = KeyBindingManager(repo, this)
            assertEquals("playback.speed_up", manager.resolve(triggerSpeed, InputSurface.PLAYER))
        }

    @Test
    fun `resolve returns null for unbound trigger`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = mockk<InputBindingRepository>()
            every { repo.observeResolvedBindings() } returns flowOf(defaults)
            val manager = KeyBindingManager(repo, this)
            assertNull(manager.resolve(triggerUnknown, InputSurface.PLAYER))
        }
}
