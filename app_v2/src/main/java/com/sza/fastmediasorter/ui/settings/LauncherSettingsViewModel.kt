package com.sza.fastmediasorter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.usecase.launcher.ImportSystemShortcutsUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.ResetLauncherToDefaultsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S1400: the launcher settings dialog's own ViewModel.
 */
@HiltViewModel
class LauncherSettingsViewModel @Inject constructor(
    private val resetLauncherToDefaultsUseCase: ResetLauncherToDefaultsUseCase,
    private val importSystemShortcutsUseCase: ImportSystemShortcutsUseCase,
) : ViewModel() {

    private val _resetResult = Channel<Boolean>(Channel.BUFFERED)
    private val _importResult = Channel<Boolean>(Channel.BUFFERED)

    /** One outcome per reset attempt, consumed by the launcher settings dialog. */
    val resetResult: Flow<Boolean> = _resetResult.receiveAsFlow()

    /** One outcome per import attempt, consumed by the launcher settings dialog. */
    val importResult: Flow<Boolean> = _importResult.receiveAsFlow()

    /** Puts the launcher back to its as-installed state; the whole decision lives in the use case. */
    fun resetToDefaults() {
        viewModelScope.launch {
            _resetResult.send(resetLauncherToDefaultsUseCase())
        }
    }

    /** Imports system desktop shortcuts onto the launcher desktop grid. */
    fun importSystemShortcuts() {
        viewModelScope.launch {
            _importResult.send(importSystemShortcutsUseCase())
        }
    }
}
