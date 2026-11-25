package com.sza.fastmediasorter.wear.ui.streams

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastStartState
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearStreamPlaybackTarget
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import com.sza.fastmediasorter.wear.domain.usecase.PrepareWearStreamPlaybackUseCase
import com.sza.fastmediasorter.wear.domain.usecase.WatchWearBroadcastStartUseCase
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * S4692: the waiting screen of a handed-off link whose broadcast has not started (LIVE-BROADCAST item S).
 * [startState] is cold on purpose: the screen collects it only while STARTED, so the relay is asked only while the
 * waiting state is visible, and a return to the screen asks again at once.
 */
@HiltViewModel
class BroadcastWaitingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val channelRepository: WearStreamChannelRepository,
    private val preparePlayback: PrepareWearStreamPlaybackUseCase,
    watchStart: WatchWearBroadcastStartUseCase,
) : ViewModel() {

    private val channelUrl: String = savedStateHandle.get<String>(WearRoutes.ARG_CHANNEL_URL).orEmpty()

    val startState: Flow<WearBroadcastStartState> = watchStart(channelUrl)

    suspend fun title(): String? = channel()?.name

    /** Playing stays the person's action: the preparation runs only on the Play chip, never on the start itself. */
    suspend fun play(): WearStreamPlaybackTarget? = channel()?.let { preparePlayback(it) }

    private suspend fun channel(): WearStreamChannel? =
        channelRepository.getAllChannels().firstOrNull { it.url == channelUrl }
}
