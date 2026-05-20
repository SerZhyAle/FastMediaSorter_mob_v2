package com.sza.fastmediasorter.domain.input.usecase

import com.sza.fastmediasorter.domain.input.InputBinding
import com.sza.fastmediasorter.domain.input.InputTrigger
import javax.inject.Inject

/**
 * Pure-function use case: detects bindings where two or more commands share the same trigger
 * on the same device. Device equality is derived from the trigger type.
 *
 * No I/O - safe to call on the main thread.
 */
class DetectConflictsUseCase @Inject constructor() {

    /** @return map of conflicting trigger → list of commandIds that share it (only entries with ≥ 2 commands). */
    operator fun invoke(bindings: List<InputBinding>): Map<InputTrigger, List<String>> {
        // Key: "device:serialized_trigger" - same trigger on same device is a conflict
        val grouped = bindings.groupBy { deviceOf(it.trigger) + ":" + it.trigger.serialize() }
        return grouped
            .filter { (_, list) -> list.distinctBy { it.commandId }.size >= 2 }
            .values
            .associate { list -> list.first().trigger to list.map { it.commandId }.distinct() }
    }

    private fun deviceOf(trigger: InputTrigger): String = when (trigger) {
        is InputTrigger.Key -> "keyboard"
        is InputTrigger.MouseButton -> "mouse"
        is InputTrigger.GamepadButton, is InputTrigger.GamepadAxis -> "gamepad"
        is InputTrigger.VrEvent -> "vr"
    }
}
