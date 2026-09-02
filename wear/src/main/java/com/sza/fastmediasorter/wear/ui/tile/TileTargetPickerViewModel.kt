package com.sza.fastmediasorter.wear.ui.tile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.model.WearTileTargetRef
import com.sza.fastmediasorter.wear.domain.model.streamTargetRef
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import com.sza.fastmediasorter.wear.domain.repository.WearTileAssignmentRepository
import com.sza.fastmediasorter.wear.domain.usecase.RequestWearTileRefreshUseCase
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PickerRow {
    data class ResourceRow(val source: NetworkSource) : PickerRow
    data class StreamRow(val channel: WearStreamChannel) : PickerRow
}

data class TileTargetPickerUiState(
    val kind: WearTileKind = WearTileKind.RESOURCE,
    val rows: List<PickerRow> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * S1955: ViewModel for choosing a tile target (resource or stream).
 */
@HiltViewModel
class TileTargetPickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val networkSourceRepository: NetworkSourceRepository,
    private val wearStreamChannelRepository: WearStreamChannelRepository,
    private val wearTileAssignmentRepository: WearTileAssignmentRepository,
    private val requestWearTileRefreshUseCase: RequestWearTileRefreshUseCase
) : ViewModel() {

    private val kindString: String? = savedStateHandle.get<String>(WearRoutes.ARG_TILE_KIND)
    val kind: WearTileKind = kindString?.let { name ->
        runCatching { WearTileKind.valueOf(name) }.getOrNull()
    } ?: WearTileKind.RESOURCE

    private val _uiState = MutableStateFlow(TileTargetPickerUiState(kind = kind))
    val uiState: StateFlow<TileTargetPickerUiState> = _uiState.asStateFlow()

    private val _doneEvent = MutableSharedFlow<Unit>()
    val doneEvent: SharedFlow<Unit> = _doneEvent.asSharedFlow()

    init {
        if (kind == WearTileKind.FAVOURITES) {
            viewModelScope.launch {
                _doneEvent.emit(Unit)
            }
        } else {
            loadRows()
        }
    }

    private fun loadRows() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val rows = when (kind) {
                WearTileKind.RESOURCE -> {
                    networkSourceRepository.getAllSources().map { PickerRow.ResourceRow(it) }
                }
                WearTileKind.STREAM -> {
                    wearStreamChannelRepository.getAllChannels().map { PickerRow.StreamRow(it) }
                }
                WearTileKind.FAVOURITES -> emptyList()
            }
            _uiState.value = TileTargetPickerUiState(
                kind = kind,
                rows = rows,
                isLoading = false
            )
        }
    }

    fun selectResource(source: NetworkSource) {
        val ref = WearTileTargetRef.Resource(
            id = source.id,
            type = source.type,
            server = source.server,
            port = source.port,
            shareName = source.shareName,
            basePath = source.basePath
        )
        selectTarget(WearTileKind.RESOURCE, ref)
    }

    fun selectStream(channel: WearStreamChannel) {
        val ref = streamTargetRef(channel.url)
        selectTarget(WearTileKind.STREAM, ref)
    }

    private fun selectTarget(kind: WearTileKind, ref: WearTileTargetRef) {
        viewModelScope.launch {
            wearTileAssignmentRepository.assign(kind, ref)
            requestWearTileRefreshUseCase(kind)
            _doneEvent.emit(Unit)
        }
    }
}
