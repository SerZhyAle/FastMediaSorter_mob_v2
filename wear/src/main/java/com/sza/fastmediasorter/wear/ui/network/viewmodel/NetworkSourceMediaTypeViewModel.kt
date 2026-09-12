package com.sza.fastmediasorter.wear.ui.network.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [com.sza.fastmediasorter.wear.ui.network.NetworkSourceMediaTypeScreen].
 * S2487: Resolves the selected [NetworkSource] to query its `supportedMediaTypes` and `allFiles` configuration.
 */
@HiltViewModel
class NetworkSourceMediaTypeViewModel @Inject constructor(
    private val networkSourceRepository: NetworkSourceRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val sourceId: String = savedStateHandle.get<String>(WearRoutes.ARG_SOURCE_ID).orEmpty()
    val sourceName: String = savedStateHandle.get<String>(WearRoutes.ARG_SOURCE_NAME).orEmpty()

    private val _source = MutableStateFlow<NetworkSource?>(null)
    val source: StateFlow<NetworkSource?> = _source.asStateFlow()

    init {
        if (sourceId.isNotBlank()) {
            viewModelScope.launch {
                _source.value = networkSourceRepository.getSourceById(sourceId)
            }
        }
    }
}
