package com.sza.fastmediasorter.ui.networkmonitor.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.networkmonitor.HostProbe
import com.sza.fastmediasorter.domain.networkmonitor.HostProbeResult
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.networkmonitor.MeasureThroughputUseCase
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ThroughputMode
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ThroughputState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResourceSpeedUiState(
    val resources: List<MediaResource> = emptyList(),
    val selectedResourceId: Long? = null, // null means Internet
    val selectedResourceName: String = "Internet",
    val isWritable: Boolean = false,
    val isRunning: Boolean = false,
    val progressFraction: Float = 0f,
    val downMbps: Double? = null,
    val upMbps: Double? = null,
    val isMeteredWarning: Boolean = false,
    val error: String? = null,
    val statusText: String? = null
)

@HiltViewModel
class ResourceSpeedSectionViewModel @Inject constructor(
    private val measureThroughputUseCase: MeasureThroughputUseCase,
    private val resourceRepository: ResourceRepository,
    private val hostProbe: HostProbe
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResourceSpeedUiState())
    val uiState: StateFlow<ResourceSpeedUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    init {
        viewModelScope.launch {
            resourceRepository.getAllResources().collect { list ->
                _uiState.update { it.copy(resources = list) }
            }
        }
    }

    fun selectResource(resourceId: Long?) {
        if (resourceId == null) {
            _uiState.update {
                it.copy(
                    selectedResourceId = null,
                    selectedResourceName = "Internet",
                    isWritable = true,
                    error = null
                )
            }
        } else {
            val res = _uiState.value.resources.firstOrNull { it.id == resourceId }
            val name = res?.name ?: "Resource #$resourceId"
            val writable = res?.isWritable ?: false
            _uiState.update {
                it.copy(
                    selectedResourceId = resourceId,
                    selectedResourceName = name,
                    isWritable = writable,
                    error = null
                )
            }
        }
    }

    fun startSpeedTest(networkLabel: String, allowMetered: Boolean = false) {
        val current = _uiState.value
        val mode = if (current.selectedResourceId == null) {
            ThroughputMode.Internet
        } else {
            ThroughputMode.Resource(current.selectedResourceId)
        }

        activeJob?.cancel()
        _uiState.update {
            it.copy(
                isRunning = true,
                progressFraction = 0f,
                downMbps = null,
                upMbps = null,
                isMeteredWarning = false,
                error = null,
                statusText = "Checking reachability..."
            )
        }

        activeJob = viewModelScope.launch {
            // Step 05.4: Ask host probe first before starting transfer
            val targetHost = getTargetHostForMode(mode)
            if (targetHost != null) {
                val reachability = hostProbe.probe(targetHost, 3000L)
                if (reachability is HostProbeResult.NotMeasurable) {
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            error = "Could not measure: ${reachability.cause.name}",
                            statusText = null
                        )
                    }
                    return@launch
                }
            }

            _uiState.update { it.copy(statusText = "Measuring speed...") }

            measureThroughputUseCase(mode, networkLabel, allowMetered).collect { state ->
                when (state) {
                    ThroughputState.MeteredNetwork -> {
                        _uiState.update {
                            it.copy(isRunning = false, isMeteredWarning = true, statusText = null)
                        }
                    }
                    is ThroughputState.Progress -> {
                        _uiState.update { it.copy(progressFraction = state.fraction) }
                    }
                    is ThroughputState.Complete -> {
                        _uiState.update {
                            it.copy(
                                isRunning = false,
                                downMbps = state.downMbps,
                                upMbps = state.upMbps,
                                statusText = "Measurement complete"
                            )
                        }
                    }
                    ThroughputState.ResourceMissing -> {
                        _uiState.update {
                            it.copy(
                                isRunning = false,
                                error = "Resource no longer exists",
                                statusText = null
                            )
                        }
                    }
                    is ThroughputState.Failed -> {
                        _uiState.update {
                            it.copy(
                                isRunning = false,
                                error = state.reason.ifBlank { "Measurement failed" },
                                statusText = null
                            )
                        }
                    }
                }
            }
        }
    }

    fun cancelTest() {
        activeJob?.cancel()
        activeJob = null
        _uiState.update {
            it.copy(isRunning = false, statusText = "Cancelled")
        }
    }

    private suspend fun getTargetHostForMode(mode: ThroughputMode): String? = when (mode) {
        ThroughputMode.Internet -> "speed.cloudflare.com"
        is ThroughputMode.Resource -> {
            val res = resourceRepository.getResourceById(mode.resourceId)
            res?.path?.split("/")?.firstOrNull { it.isNotBlank() }
        }
    }
}
