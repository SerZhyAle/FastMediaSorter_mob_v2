package com.sza.fastmediasorter.domain.usecase.sos

import com.sza.fastmediasorter.domain.model.sos.SosMode
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * S3216: the mode the phone's distress signal last ran in, which is also the mode the next one starts in.
 *
 * A use case rather than a settings read from the screen's view model: the SOS window is on the UI side of
 * the layer arrow, and a view model importing a repository is what the `viewmodel-imports-repository`
 * gate refuses (S2103).
 */
class GetSosModeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {

    suspend operator fun invoke(): SosMode = settingsRepository.getSettings().first().sosMode
}
