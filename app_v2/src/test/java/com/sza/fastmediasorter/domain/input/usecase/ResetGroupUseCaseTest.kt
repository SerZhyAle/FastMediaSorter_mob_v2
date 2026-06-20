package com.sza.fastmediasorter.domain.input.usecase

import com.sza.fastmediasorter.data.input.InputBindingRepository
import com.sza.fastmediasorter.domain.input.CommandGroup
import com.sza.fastmediasorter.testing.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [ResetGroupUseCase] - verifies the [CommandGroup] to command-id-prefix mapping
 * forwarded to the repository.
 */
class ResetGroupUseCaseTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val repo = mockk<InputBindingRepository>(relaxed = true)
    private val useCase = ResetGroupUseCase(repo)

    @Test
    fun `each command group maps to its documented prefix`() = runTest {
        val expected = mapOf(
            CommandGroup.PLAYBACK_CORE to "playback.",
            CommandGroup.NAVIGATION to "navigation.",
            CommandGroup.VIEW_ZOOM to "view.",
            CommandGroup.AUDIO_SUBTITLES to "audio.",
            CommandGroup.SYSTEM_UI to "system.",
            CommandGroup.SORTING_ACTIONS to "sorting.",
            CommandGroup.BROWSER_ACTIONS to "browser.",
            CommandGroup.VR_ONLY to "vr.",
        )

        expected.forEach { (group, prefix) ->
            useCase(group)
            coVerify(exactly = 1) { repo.clearAllOverridesForGroup(prefix) }
        }
    }
}
