package com.sza.fastmediasorter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.usecase.launcher.ResetLauncherToDefaultsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S1400: the launcher settings dialog's own ViewModel, holding the one action that is not a settings
 * row. It is separate from [SettingsViewModel] rather than another dependency of it: that constructor
 * already carries an accepted `LongParameterList`, and adding a parameter would resurface the finding
 * against this ticket without making the class any better.
 */
@HiltViewModel
class LauncherSettingsViewModel @Inject constructor(
    private val resetLauncherToDefaultsUseCase: ResetLauncherToDefaultsUseCase,
) : ViewModel() {

    private val _resetResult = Channel<Boolean>(Channel.BUFFERED)

    /** One outcome per reset attempt, consumed by the launcher settings dialog. */
    val resetResult: Flow<Boolean> = _resetResult.receiveAsFlow()

    /** Puts the launcher back to its as-installed state; the whole decision lives in the use case. */
    fun resetToDefaults() {
        viewModelScope.launch {
            _resetResult.send(resetLauncherToDefaultsUseCase())
        }
    }
}
