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
import timber.log.Timber
import javax.inject.Inject

/** S2347: how far along the measurement is. The fragment words it in the user's locale. */
enum class ResourceSpeedStatus { CHECKING, MEASURING, COMPLETE, STOPPED }

/**
 * S2347: why a measurement produced no numbers.
 *
 * Each arm owns one sentence and none of them carries raw text: `COMMUNICATION_POLICY` §2.2 keeps
 * exception messages and enum names out of the headline, and the underlying cause is logged instead.
 */
enum class ResourceSpeedError { UNREACHABLE, RESOURCE_MISSING, MEASUREMENT_FAILED, METERED_NETWORK }

data class ResourceSpeedUiState(
    val resources: List<MediaResource> = emptyList(),
    val selectedResourceId: Long? = null, // null means Internet
    val isWritable: Boolean = false,
    val isRunning: Boolean = false,
    val progressFraction: Float = 0f,
    val downMbps: Double? = null,
    val upMbps: Double? = null,
    // S2348: the "run anyway?" question is still open. Separate from the METERED_NETWORK error arm so
    // that answering the question closes the dialog without also erasing the result line behind it.
    val isMeteredWarning: Boolean = false,
    val error: ResourceSpeedError? = null,
    val status: ResourceSpeedStatus? = null
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
        // S2348: the pending question names the target it was asked about, so switching target withdraws it
        // along with the result it refused - otherwise the dialog outlives the selection that raised it.
        if (resourceId == null) {
            _uiState.update {
                it.copy(selectedResourceId = null, isWritable = true, isMeteredWarning = false, error = null)
            }
        } else {
            val writable = _uiState.value.resources.firstOrNull { it.id == resourceId }?.isWritable ?: false
            _uiState.update {
                it.copy(
                    selectedResourceId = resourceId,
                    isWritable = writable,
                    isMeteredWarning = false,
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

        Timber.d("S2347: resource speed test started as typed state: %s", mode)
        activeJob?.cancel()
        _uiState.update {
            it.copy(
                isRunning = true,
                progressFraction = 0f,
                downMbps = null,
                upMbps = null,
                isMeteredWarning = false,
                error = null,
                status = ResourceSpeedStatus.CHECKING
            )
        }

        activeJob = viewModelScope.launch {
            // Step 05.4: Ask host probe first before starting transfer
            val targetHost = getTargetHostForMode(mode)
            if (targetHost != null) {
                val reachability = hostProbe.probe(targetHost, REACHABILITY_TIMEOUT_MS)
                if (reachability is HostProbeResult.NotMeasurable) {
                    // The cause and its detail are log material by their own KDoc, never screen text.
                    Timber.d(
                        "Resource speed probe unavailable: %s (%s)",
                        reachability.cause,
                        reachability.detail ?: "no detail"
                    )
                    _uiState.update {
                        it.copy(isRunning = false, error = ResourceSpeedError.UNREACHABLE, status = null)
                    }
                    return@launch
                }
            }

            _uiState.update { it.copy(status = ResourceSpeedStatus.MEASURING) }

            measureThroughputUseCase(mode, networkLabel, allowMetered).collect { state ->
                onThroughputState(state)
            }
        }
    }

    private fun onThroughputState(state: ThroughputState) {
        when (state) {
            ThroughputState.MeteredNetwork -> _uiState.update {
                Timber.d("S2348: metered refusal reached the section state, raising warning and result")
                it.copy(
                    isRunning = false,
                    isMeteredWarning = true,
                    error = ResourceSpeedError.METERED_NETWORK,
                    status = null
                )
            }
            is ThroughputState.Progress -> _uiState.update { it.copy(progressFraction = state.fraction) }
            is ThroughputState.Complete -> _uiState.update {
                it.copy(
                    isRunning = false,
                    downMbps = state.downMbps,
                    upMbps = state.upMbps,
                    status = ResourceSpeedStatus.COMPLETE
                )
            }
            ThroughputState.ResourceMissing -> _uiState.update {
                it.copy(isRunning = false, error = ResourceSpeedError.RESOURCE_MISSING, status = null)
            }
            is ThroughputState.Failed -> {
                Timber.d("Resource speed measurement failed: %s", state.reason.ifBlank { "no reason given" })
                _uiState.update {
                    it.copy(isRunning = false, error = ResourceSpeedError.MEASUREMENT_FAILED, status = null)
                }
            }
        }
    }

    /**
     * S2348: the screen raises the metered dialog from [ResourceSpeedUiState.isMeteredWarning] alone, so a
     * declined question has to lower the flag or the next state emission rebuilds the same dialog. The
     * [ResourceSpeedError.METERED_NETWORK] arm deliberately survives: the refusal is still the last result.
     */
    fun dismissMeteredWarning() {
        _uiState.update { it.copy(isMeteredWarning = false) }
    }

    fun cancelTest() {
        activeJob?.cancel()
        activeJob = null
        _uiState.update { it.copy(isRunning = false, status = ResourceSpeedStatus.STOPPED) }
    }

    private suspend fun getTargetHostForMode(mode: ThroughputMode): String? = when (mode) {
        ThroughputMode.Internet -> INTERNET_PROBE_HOST
        is ThroughputMode.Resource -> {
            val res = resourceRepository.getResourceById(mode.resourceId)
            res?.path?.split("/")?.firstOrNull { it.isNotBlank() }
        }
    }

    private companion object {
        const val REACHABILITY_TIMEOUT_MS = 3000L
        const val INTERNET_PROBE_HOST = "speed.cloudflare.com"
    }
}
