package com.sza.fastmediasorter.domain.input.usecase

import com.sza.fastmediasorter.data.input.InputBindingRepository
import com.sza.fastmediasorter.domain.input.CommandGroup
import timber.log.Timber
import javax.inject.Inject

class ResetGroupUseCase @Inject constructor(private val repo: InputBindingRepository) {

    suspend operator fun invoke(group: CommandGroup) {
        val prefix = prefixForGroup(group)
        Timber.d("ResetGroup: group=%s prefix=%s", group.name, prefix)
        repo.clearAllOverridesForGroup(prefix)
    }

    private fun prefixForGroup(group: CommandGroup): String = when (group) {
        CommandGroup.PLAYBACK_CORE -> "playback."
        CommandGroup.NAVIGATION -> "navigation."
        CommandGroup.VIEW_ZOOM -> "view."
        CommandGroup.AUDIO_SUBTITLES -> "audio."
        CommandGroup.SYSTEM_UI -> "system."
        CommandGroup.SORTING_ACTIONS -> "sorting."
        CommandGroup.VR_ONLY -> "vr."
    }
}
