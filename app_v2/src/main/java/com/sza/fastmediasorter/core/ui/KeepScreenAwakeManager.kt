package com.sza.fastmediasorter.core.ui

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * S1329: owns the settings dependency that BaseActivity used to hold as a repository field.
 *
 * The host needs the settings stream for two decisions taken in BaseActivity itself - the
 * keep-screen-on flag and the secure-window flag - but a UI host must not reference the data
 * layer directly (CLAUDE.md Rule 3). This manager holds that reference instead and republishes
 * the stream, so BaseActivity and every subclass read settings without naming a repository.
 */
class KeepScreenAwakeManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {

    val settings: Flow<AppSettings> get() = settingsRepository.getSettings()
}
