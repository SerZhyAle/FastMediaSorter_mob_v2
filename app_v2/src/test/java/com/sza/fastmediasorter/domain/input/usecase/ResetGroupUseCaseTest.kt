package com.sza.fastmediasorter.domain.input.usecase

import com.sza.fastmediasorter.data.input.InputBindingRepository
import com.sza.fastmediasorter.domain.input.CommandGroup
import com.sza.fastmediasorter.domain.input.CommandId
import com.sza.fastmediasorter.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [ResetGroupUseCase] - the reset deletes exactly the overridden commands that
 * [CommandGroup.of] files under the group, the same mapping the remap screen groups rows with.
 */
class ResetGroupUseCaseTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val repo = mockk<InputBindingRepository>(relaxed = true)
    private val useCase = ResetGroupUseCase(repo)

    private val overridden = listOf(
        "playback.play_pause",
        "sorting.copy",
        CommandId.OPERATION_SLOT_1,
        CommandId.OPERATION_SLOT_9,
        "system.show_help",
        CommandId.BLACK_SCREEN,
    )

    @Test
    fun `sorting actions reset keeps operation slot overrides`() = runTest {
        coEvery { repo.overriddenCommandIds() } returns overridden

        useCase(CommandGroup.SORTING_ACTIONS)

        coVerify(exactly = 1) { repo.clearAllOverrides(listOf("sorting.copy")) }
    }

    @Test
    fun `operation slots reset deletes only operation slot overrides`() = runTest {
        coEvery { repo.overriddenCommandIds() } returns overridden

        useCase(CommandGroup.OPERATION_SLOTS)

        coVerify(exactly = 1) {
            repo.clearAllOverrides(listOf(CommandId.OPERATION_SLOT_1, CommandId.OPERATION_SLOT_9))
        }
    }

    @Test
    fun `system ui reset includes the black screen override`() = runTest {
        coEvery { repo.overriddenCommandIds() } returns overridden

        useCase(CommandGroup.SYSTEM_UI)

        coVerify(exactly = 1) {
            repo.clearAllOverrides(listOf("system.show_help", CommandId.BLACK_SCREEN))
        }
    }

    @Test
    fun `group without overrides deletes nothing`() = runTest {
        coEvery { repo.overriddenCommandIds() } returns overridden

        useCase(CommandGroup.VR_ONLY)

        coVerify(exactly = 0) { repo.clearAllOverrides(any<Collection<String>>()) }
    }
}
