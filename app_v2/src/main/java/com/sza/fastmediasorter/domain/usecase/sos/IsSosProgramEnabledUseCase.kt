package com.sza.fastmediasorter.domain.usecase.sos

import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * S3216: whether the phone's distress signal is a program on this device at all.
 *
 * Read on exactly one path - a start arriving from the paired watch. The owner's switch decides whether
 * the phone may RAISE a signal, and a paired watch may not overrule it; the watch then signals alone,
 * which is the same autonomous answer ADR-2 gives for a companion out of range. A stop is never asked
 * this question, because a signal running from a moment ago must stay stoppable.
 */
class IsSosProgramEnabledUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {

    suspend operator fun invoke(): Boolean = settingsRepository.getSettings().first().enableSos
}
