package com.sza.fastmediasorter.ui.companionimport.qr

import androidx.lifecycle.ViewModel
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * S1045: owns the `secureSensitiveScreens` setting for the QR share screen. S1195: the Activity used to
 * read [SettingsRepository] directly, which CLAUDE.md Rule 3 forbids.
 */
@HiltViewModel
class CompanionQrShareViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Emits only on an actual flip, so the window flags are not rewritten on every unrelated setting write. */
    val secureSensitiveScreens: Flow<Boolean> = settingsRepository.getSettings()
        .map { it.secureSensitiveScreens }
        .distinctUntilChanged()
}
