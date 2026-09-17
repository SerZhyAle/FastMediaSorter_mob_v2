package com.sza.fastmediasorter.domain.usecase.sos

import com.sza.fastmediasorter.domain.model.sos.SosMode
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * S3216: records the mode the owner chose during an emergency as the next signal's starting mode.
 *
 * Written through the transform overload rather than by copying a whole snapshot: the SOS screen holds no
 * other settings field, and a full write from it would put back whatever the rest of the app changed while
 * the siren was running.
 */
class SaveSosModeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {

    suspend operator fun invoke(mode: SosMode) {
        settingsRepository.updateSettings { it.copy(sosMode = mode) }
    }
}
