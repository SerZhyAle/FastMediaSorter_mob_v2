package com.sza.fastmediasorter.data.input

import com.sza.fastmediasorter.domain.input.BindingSource
import com.sza.fastmediasorter.domain.input.InputBinding
import com.sza.fastmediasorter.domain.input.InputTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class InputBindingRepository @Inject constructor(
    private val dao: InputBindingDao,
    private val defaultsMapLoader: DefaultsMapLoader
) {

    fun observeResolvedBindings(): Flow<List<InputBinding>> {
        val defaults = defaultsMapLoader.loadDefaults()
        return dao.observeAll().map { overrideEntities ->
            merge(defaults, overrideEntities)
        }
    }

    suspend fun setOverride(commandId: String, device: String, slot: Int, trigger: InputTrigger) {
        dao.upsert(
            InputBindingEntity(
                commandId = commandId,
                device = device,
                slot = slot,
                trigger = trigger.serialize(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearOverride(commandId: String, device: String) {
        dao.deleteByCommandAndDevice(commandId, device)
    }

    /** Clears all override rows for [commandId] regardless of device. */
    suspend fun clearAllOverrides(commandId: String) {
        dao.deleteByCommand(commandId)
    }

    /** Clears all override rows for all commands in the group identified by [prefix] (e.g. "playback."). */
    suspend fun clearAllOverridesForGroup(prefix: String) {
        dao.deleteByCommandPrefix("$prefix%")
    }

    suspend fun clearAll() {
        dao.deleteAll()
    }

    suspend fun hasOverrides(): Boolean = dao.observeAll().first().isNotEmpty()

    suspend fun insertAllAsOverrides(bindings: List<InputBinding>) {
        bindings.forEach { binding ->
            val device = when (binding.trigger) {
                is InputTrigger.Key -> "keyboard"
                is InputTrigger.MouseButton -> "mouse"
                is InputTrigger.GamepadButton, is InputTrigger.GamepadAxis -> "gamepad"
                is InputTrigger.VrEvent -> "vr"
            }
            dao.upsert(InputBindingEntity(
                commandId = binding.commandId,
                device = device,
                slot = 0,
                trigger = binding.trigger.serialize(),
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    private fun merge(
        defaults: List<InputBinding>,
        overrides: List<InputBindingEntity>
    ): List<InputBinding> {
        val overridesByKey = overrides.groupBy { it.commandId to it.device }
        val defaultsByKey = defaults.groupBy { it.commandId to deviceOf(it.trigger) }
        val result = mutableListOf<InputBinding>()

        for ((key, defaultBindings) in defaultsByKey) {
            val overrideList = overridesByKey[key]
            if (overrideList != null) {
                overrideList.forEach { entity ->
                    result.add(
                        InputBinding(
                            commandId = entity.commandId,
                            trigger = InputTrigger.deserialize(entity.trigger),
                            source = BindingSource.OVERRIDE
                        )
                    )
                }
            } else {
                result.addAll(defaultBindings)
            }
        }

        val defaultKeys = defaultsByKey.keys
        for ((key, overrideList) in overridesByKey) {
            if (key !in defaultKeys) {
                overrideList.forEach { entity ->
                    result.add(
                        InputBinding(
                            commandId = entity.commandId,
                            trigger = InputTrigger.deserialize(entity.trigger),
                            source = BindingSource.OVERRIDE
                        )
                    )
                }
            }
        }

        return result
    }

    private fun deviceOf(trigger: InputTrigger): String = when (trigger) {
        is InputTrigger.Key -> "keyboard"
        is InputTrigger.MouseButton -> "mouse"
        is InputTrigger.GamepadButton, is InputTrigger.GamepadAxis -> "gamepad"
        is InputTrigger.VrEvent -> "vr"
    }
}
