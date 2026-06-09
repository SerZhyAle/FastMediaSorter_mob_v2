package com.sza.fastmediasorter.ui.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DeliverableSetDownloader
import com.sza.fastmediasorter.domain.delivery.DeliverableSourceDescriptor
import com.sza.fastmediasorter.domain.delivery.DownloadProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the download offer for a requested [DeliverableSet] (S0386 Phase 06, Pillar D): shows the
 * estimated size, runs the [DeliverableSetDownloader], and maps its progress to a UI state machine -
 * offer -> downloading -> verifying -> success / error / refused. On refusal the capability is left
 * off (the toggle call-site keeps it OFF; this layer only signals the outcome).
 */
@HiltViewModel
class DeliveryPromptViewModel @Inject constructor(
    private val downloader: DeliverableSetDownloader,
    private val descriptors: Map<DeliverableSet, @JvmSuppressWildcards DeliverableSourceDescriptor>
) : ViewModel() {

    private val _uiState = MutableStateFlow<DeliveryPromptUiState>(DeliveryPromptUiState.Idle)
    val uiState: StateFlow<DeliveryPromptUiState> = _uiState.asStateFlow()

    private var current: DeliverableSet? = null
    private var downloadJob: Job? = null

    /** Present the offer for [set] with an estimated size from its bundled descriptor. */
    fun start(set: DeliverableSet) {
        if (current == set && _uiState.value != DeliveryPromptUiState.Idle) return
        current = set
        val estimatedBytes = descriptors[set]?.files?.sumOf { it.minSize } ?: 0L
        _uiState.value = DeliveryPromptUiState.Offer(set, estimatedBytes)
    }

    /** User accepted: start the download and stream progress into the UI state. */
    fun confirmDownload() {
        val set = current ?: return
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            downloader.download(set).collect { progress ->
                _uiState.value = when (progress) {
                    DownloadProgress.Queued -> DeliveryPromptUiState.Downloading(0)
                    is DownloadProgress.Running -> DeliveryPromptUiState.Downloading(progress.percent)
                    DownloadProgress.Verifying -> DeliveryPromptUiState.Verifying
                    DownloadProgress.Installed -> DeliveryPromptUiState.Success
                    is DownloadProgress.Failed -> DeliveryPromptUiState.Error(progress.reason)
                }
            }
        }
    }

    /** User declined the download: capability stays off, surface a soft-unavailable outcome. */
    fun refuse() {
        downloadJob?.cancel()
        _uiState.value = DeliveryPromptUiState.Refused
    }
}

/** UI state of the delivery prompt. [Success], [Error], [Refused] are terminal. */
sealed interface DeliveryPromptUiState {
    data object Idle : DeliveryPromptUiState
    data class Offer(val set: DeliverableSet, val estimatedBytes: Long) : DeliveryPromptUiState
    data class Downloading(val percent: Int) : DeliveryPromptUiState
    data object Verifying : DeliveryPromptUiState
    data object Success : DeliveryPromptUiState
    data class Error(val reason: String) : DeliveryPromptUiState
    data object Refused : DeliveryPromptUiState
}
