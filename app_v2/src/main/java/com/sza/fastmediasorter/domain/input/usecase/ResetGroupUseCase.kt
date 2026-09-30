package com.sza.fastmediasorter.domain.input.usecase

import com.sza.fastmediasorter.data.input.InputBindingRepository
import com.sza.fastmediasorter.domain.input.CommandGroup
import timber.log.Timber
import javax.inject.Inject

class ResetGroupUseCase @Inject constructor(private val repo: InputBindingRepository) {

    suspend operator fun invoke(group: CommandGroup) {
        // Selected by the same mapping the remap screen groups with; a SQL prefix cannot express
        // "sorting. except sorting.op_slot_" nor the SYSTEM_UI fallback.
        val commandIds = repo.overriddenCommandIds().filter { CommandGroup.of(it) == group }
        Timber.d("ResetGroup: group=%s commands=%d", group.name, commandIds.size)
        if (commandIds.isNotEmpty()) repo.clearAllOverrides(commandIds)
    }
}
