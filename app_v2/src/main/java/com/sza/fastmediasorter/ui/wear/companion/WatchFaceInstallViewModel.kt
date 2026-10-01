package com.sza.fastmediasorter.ui.wear.companion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.domain.model.WatchFaceOpenResult
import com.sza.fastmediasorter.domain.usecase.OpenWatchFaceOnWatchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * S4009: runs "open the watch face's store page on the watch" and hands each outcome to the host
 * once. Shared by the Operations Wear card and the companion window through
 * [com.sza.fastmediasorter.ui.wear.companion.helpers.WatchFaceInstallActionManager].
 */
@HiltViewModel
class WatchFaceInstallViewModel @Inject constructor(
    private val openWatchFaceOnWatch: OpenWatchFaceOnWatchUseCase,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _events = Channel<WatchFaceOpenResult>(Channel.BUFFERED)
    val events: Flow<WatchFaceOpenResult> = _events.receiveAsFlow()

    private var request: Job? = null

    fun install() {
        // A second tap while the watch is still being asked would start the store page twice.
        if (request?.isActive == true) return
        request = viewModelScope.launch {
            _events.send(withContext(ioDispatcher) { openWatchFaceOnWatch() })
        }
    }
}
