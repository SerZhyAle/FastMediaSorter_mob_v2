package com.sza.fastmediasorter.data.input

import androidx.room.withTransaction
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.domain.input.BindingSource
import com.sza.fastmediasorter.domain.input.InputBinding
import com.sza.fastmediasorter.domain.input.InputTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class InputBindingRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: InputBindingDao,
    private val defaultsMapLoader: DefaultsMapLoader
) {

    fun observeResolvedBindings(): Flow<List<InputBinding>> {
        // Both callers start this on the main thread (KeyBindingManager's init, the remap ViewModel),
        // so the asset read and JSON parse are deferred into the flow and moved off it.
        val defaults by lazy(LazyThreadSafetyMode.NONE) { defaultsMapLoader.loadDefaults() }
        return dao.observeAll()
            .map { overrideEntities -> merge(defaults, overrideEntities) }
            .flowOn(Dispatchers.IO)
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

    /** Clears all override rows for every id in [commandIds] regardless of device. */
    suspend fun clearAllOverrides(commandIds: Collection<String>) {
        dao.deleteByCommands(commandIds.toList())
    }

    suspend fun overriddenCommandIds(): List<String> = dao.distinctCommandIds()

    suspend fun clearAll() {
        dao.deleteAll()
    }

    suspend fun hasOverrides(): Boolean = dao.hasAny()

    suspend fun insertAllAsOverrides(bindings: List<InputBinding>) {
        // One transaction so a kill mid-seed can't leave a partial override set: a partial apply would
        // flip hasOverrides() to true permanently and skip the startup seed (AppStartupInitializer).
        db.withTransaction {
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
