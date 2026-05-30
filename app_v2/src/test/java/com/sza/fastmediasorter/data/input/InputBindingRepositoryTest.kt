package com.sza.fastmediasorter.data.input

import com.sza.fastmediasorter.domain.input.BindingSource
import com.sza.fastmediasorter.domain.input.InputBinding
import com.sza.fastmediasorter.domain.input.InputTrigger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [InputBindingRepository]: default/override merge by (commandId, device) key -
 * an override replaces all defaults for the same key, override-only commands are appended, and
 * device classification (keyboard/mouse/gamepad/vr) drives both the merge key and the persisted
 * entity device. DAO + defaults loader are mocked; InputTrigger (de)serialize is the real pure impl.
 */
class InputBindingRepositoryTest {

    private val dao = mockk<InputBindingDao>(relaxed = true)
    private val defaultsLoader = mockk<DefaultsMapLoader>()
    private val repository = InputBindingRepository(dao, defaultsLoader)

    private fun keyDefault(commandId: String, code: Int) =
        InputBinding(commandId, InputTrigger.Key(code), BindingSource.DEFAULT)

    @Test
    fun `default surfaces when no override exists`() = runBlocking {
        every { defaultsLoader.loadDefaults() } returns listOf(keyDefault("playback.pause", 62))
        every { dao.observeAll() } returns flowOf(emptyList())

        val resolved = repository.observeResolvedBindings().first()

        assertEquals(1, resolved.size)
        assertEquals("playback.pause", resolved[0].commandId)
        assertEquals(BindingSource.DEFAULT, resolved[0].source)
    }

    @Test
    fun `override replaces default for same command and device`() = runBlocking {
        every { defaultsLoader.loadDefaults() } returns listOf(keyDefault("playback.pause", 62))
        val overrideEntity = InputBindingEntity(
            commandId = "playback.pause",
            device = "keyboard",
            slot = 0,
            trigger = InputTrigger.Key(66).serialize(),
            updatedAt = 0L
        )
        every { dao.observeAll() } returns flowOf(listOf(overrideEntity))

        val resolved = repository.observeResolvedBindings().first()

        assertEquals(1, resolved.size)
        assertEquals(BindingSource.OVERRIDE, resolved[0].source)
        assertEquals(InputTrigger.Key(66), resolved[0].trigger)
    }

    @Test
    fun `override-only command is appended`() = runBlocking {
        every { defaultsLoader.loadDefaults() } returns listOf(keyDefault("playback.pause", 62))
        val extra = InputBindingEntity(
            commandId = "custom.action",
            device = "keyboard",
            slot = 0,
            trigger = InputTrigger.Key(40).serialize(),
            updatedAt = 0L
        )
        every { dao.observeAll() } returns flowOf(listOf(extra))

        val resolved = repository.observeResolvedBindings().first()

        // default (untouched) + override-only command
        assertEquals(2, resolved.map { it.commandId }.toSet().size)
        assertTrue(resolved.any { it.commandId == "custom.action" && it.source == BindingSource.OVERRIDE })
        assertTrue(resolved.any { it.commandId == "playback.pause" && it.source == BindingSource.DEFAULT })
    }

    @Test
    fun `setOverride persists device-classified entity`() = runBlocking {
        val slot = slot<InputBindingEntity>()
        coEvery { dao.upsert(capture(slot)) } returns Unit

        repository.setOverride("audio.vol_up", "keyboard", 1, InputTrigger.Key(24))

        assertEquals("audio.vol_up", slot.captured.commandId)
        assertEquals("keyboard", slot.captured.device)
        assertEquals(1, slot.captured.slot)
    }

    @Test
    fun `insertAllAsOverrides maps trigger type to device`() = runBlocking {
        val captured = mutableListOf<InputBindingEntity>()
        coEvery { dao.upsert(capture(captured)) } returns Unit

        repository.insertAllAsOverrides(
            listOf(
                InputBinding("a", InputTrigger.Key(1), BindingSource.OVERRIDE),
                InputBinding("b", InputTrigger.MouseButton(2), BindingSource.OVERRIDE),
            )
        )

        assertEquals("keyboard", captured.first { it.commandId == "a" }.device)
        assertEquals("mouse", captured.first { it.commandId == "b" }.device)
    }

    @Test
    fun `clearAllOverridesForGroup uses LIKE prefix pattern`() = runBlocking {
        repository.clearAllOverridesForGroup("playback.")
        coVerify { dao.deleteByCommandPrefix("playback.%") }
    }

    @Test
    fun `hasOverrides reflects dao content`() = runBlocking {
        every { dao.observeAll() } returns flowOf(
            listOf(InputBindingEntity("a", "keyboard", 0, InputTrigger.Key(1).serialize(), 0L))
        )
        assertTrue(repository.hasOverrides())
    }
}
