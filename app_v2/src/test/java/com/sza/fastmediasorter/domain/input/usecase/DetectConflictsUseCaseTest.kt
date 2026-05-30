package com.sza.fastmediasorter.domain.input.usecase

import com.sza.fastmediasorter.domain.input.BindingSource
import com.sza.fastmediasorter.domain.input.InputBinding
import com.sza.fastmediasorter.domain.input.InputTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DetectConflictsUseCase] - pure trigger-collision detection.
 */
class DetectConflictsUseCaseTest {

    private val useCase = DetectConflictsUseCase()

    private fun binding(commandId: String, trigger: InputTrigger) =
        InputBinding(commandId = commandId, trigger = trigger, source = BindingSource.DEFAULT)

    @Test
    fun `no conflict when triggers are unique`() {
        val result = useCase(
            listOf(
                binding("play", InputTrigger.Key(1)),
                binding("pause", InputTrigger.Key(2)),
            ),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `same trigger on same device for two commands is a conflict`() {
        val trigger = InputTrigger.Key(40)
        val result = useCase(
            listOf(
                binding("play", trigger),
                binding("pause", trigger),
            ),
        )
        assertEquals(1, result.size)
        assertEquals(listOf("play", "pause"), result[trigger])
    }

    @Test
    fun `the same command bound twice to the same trigger is not a conflict`() {
        val trigger = InputTrigger.Key(40)
        val result = useCase(
            listOf(
                binding("play", trigger),
                binding("play", trigger),
            ),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `equal raw codes on different devices do not conflict`() {
        // Key(5) and GamepadButton(5) serialize to different device buckets.
        val result = useCase(
            listOf(
                binding("a", InputTrigger.Key(5)),
                binding("b", InputTrigger.GamepadButton(5)),
            ),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `conflicting command ids are de-duplicated in the result`() {
        val trigger = InputTrigger.MouseButton(1)
        val result = useCase(
            listOf(
                binding("a", trigger),
                binding("a", trigger),
                binding("b", trigger),
            ),
        )
        assertEquals(listOf("a", "b"), result[trigger])
    }
}
