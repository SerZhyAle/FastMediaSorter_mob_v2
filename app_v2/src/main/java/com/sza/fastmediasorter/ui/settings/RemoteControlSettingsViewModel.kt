package com.sza.fastmediasorter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.remotecontrol.ManageRemoteControlUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class RemoteControlSettingsViewModel @Inject constructor(
    private val manage: ManageRemoteControlUseCase,
) : ViewModel() {
    val listening = manage.listening

    private val edits = Mutex()
    private val toggles = Channel<RemoteControlSettingsResult>(Channel.BUFFERED)
    val results = toggles.receiveAsFlow()

    fun setListening(on: Boolean) {
        Timber.d("S4701: setListening on=%b", on)
        viewModelScope.launch {
            edits.withLock {
                try {
                    manage.setListening(on)
                    if (on) toggles.send(RemoteControlSettingsResult.OpenControllers)
                } catch (error: IOException) {
                    Timber.w(error, "Could not save the External control switch")
                    toggles.send(RemoteControlSettingsResult.SaveFailed)
                }
            }
        }
    }
}

enum class RemoteControlSettingsResult {
    OpenControllers,
    SaveFailed,
}
