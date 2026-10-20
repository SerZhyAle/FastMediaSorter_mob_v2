package com.sza.fastmediasorter.wear.ui.exchange

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeEntry
import com.sza.fastmediasorter.wear.domain.model.WearAccountFolderRow
import com.sza.fastmediasorter.wear.domain.usecase.AttachAccountFolderUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ListAccountFoldersUseCase
import com.sza.fastmediasorter.wear.ui.settings.SettingsRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** What the account folders screen draws. */
sealed interface WearAccountFoldersUiState {
    data object Loading : WearAccountFoldersUiState
    data object Failed : WearAccountFoldersUiState
    data class Listed(val rows: List<WearAccountFolderRow>) : WearAccountFoldersUiState
}

/** One-shot outcome of a tap, shown once by the screen. */
enum class WearAccountFolderEvent { ADDED, ADD_FAILED }

/**
 * S4228: the account's folders of one exchange entry, without the phone (contract DEVICE-EXCHANGE 23 item CF). The
 * list is read once on entry; a tap adds the folder to the watch's network sources and the list is read again so
 * its "already on the watch" marks follow.
 */
@HiltViewModel
class WearAccountFoldersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val listFolders: ListAccountFoldersUseCase,
    private val attachFolder: AttachAccountFolderUseCase,
) : ViewModel() {

    private val entryIdentity: String = savedStateHandle.get<String>(SettingsRoutes.ARG_EXCHANGE_ENTRY).orEmpty()

    private val mutableState = MutableStateFlow<WearAccountFoldersUiState>(WearAccountFoldersUiState.Loading)
    val uiState: StateFlow<WearAccountFoldersUiState> = mutableState.asStateFlow()

    private val channel = Channel<WearAccountFolderEvent>(Channel.BUFFERED)
    val events: Flow<WearAccountFolderEvent> = channel.receiveAsFlow()

    private var entry: WearExchangeEntry? = null
    private var attaching: Job? = null

    init {
        viewModelScope.launch { reload() }
    }

    fun retry() {
        mutableState.value = WearAccountFoldersUiState.Loading
        viewModelScope.launch { reload() }
    }

    /** A second tap while the first is being written is a no-op, so one tap never writes twice. */
    fun attach(row: WearAccountFolderRow) {
        val target = entry ?: return
        if (attaching?.isActive == true) return
        attaching = viewModelScope.launch {
            val added = attachFolder(row.folder, target).isSuccess
            channel.send(if (added) WearAccountFolderEvent.ADDED else WearAccountFolderEvent.ADD_FAILED)
            if (added) reload()
        }
    }

    private suspend fun reload() {
        val target = entry ?: listFolders.entry(entryIdentity)?.also { entry = it }
        val listed = target?.let { listFolders(it) }
        mutableState.value = listed?.getOrNull()?.let(WearAccountFoldersUiState::Listed)
            ?: WearAccountFoldersUiState.Failed.also {
                Timber.w(listed?.exceptionOrNull(), "Account folders unavailable (entry known=%s)", target != null)
            }
    }
}
