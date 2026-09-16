package com.sza.fastmediasorter.wear.ui.apps.systeminfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.data.wear.WearLogReportRefusalReasons
import com.sza.fastmediasorter.wear.domain.repository.WearSystemInfoReportOutcome
import com.sza.fastmediasorter.wear.domain.usecase.GatherWearSystemInfoUseCase
import com.sza.fastmediasorter.wear.domain.usecase.SendWearSystemInfoReportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Reads the watch's system information when the screen opens, and again whenever the user asks.
 *
 * The read used to be a one-shot on the reasoning that these facts change on the scale of a reboot or
 * a pairing. That stopped being true once the report gained the health section: thermal state, battery
 * voltage, uptime and the background-restriction flag all move while the screen sits open, and the
 * owner chose a manual refresh over continuous polling (S2165 §6 question 6) - a watch that re-polled
 * on a timer would spend battery on a screen opened precisely because the battery is behaving oddly.
 */
@HiltViewModel
class SystemInfoViewModel @Inject constructor(
    private val gatherWearSystemInfo: GatherWearSystemInfoUseCase,
    private val sendWearSystemInfoReport: SendWearSystemInfoReportUseCase
) : ViewModel() {

    private val state = MutableStateFlow(SystemInfoUiState())
    val uiState: StateFlow<SystemInfoUiState> = state.asStateFlow()

    init {
        read()
    }

    /** Ignored while a read is already in flight - the report cannot be more current than the read. */
    fun refresh() {
        if (state.value.refreshing) {
            return
        }
        state.update { current -> current.copy(refreshing = true) }
        read()
    }

    /**
     * Sends the report currently on screen to the paired phone (S3108).
     *
     * Ignored while a send is in flight, for [refresh]'s reason: a second tap would open a second
     * round trip whose answer would overwrite the first one's for no gain.
     */
    fun sendToPhone() {
        val current = state.value
        if (current.sending || current.sections.isEmpty()) {
            return
        }
        Timber.d("S3108: system info send to phone requested")
        state.update { shown -> shown.copy(sending = true, sendOutcomeRes = null) }
        viewModelScope.launch {
            val outcome = sendWearSystemInfoReport(current.sections)
            Timber.i("System info report: %s", outcome::class.java.simpleName)
            state.update { shown -> shown.copy(sending = false, sendOutcomeRes = wording(outcome)) }
        }
    }

    /**
     * The refusal reason is matched as a literal because the phone writes it from its own copy of the
     * constant - the two modules share no code, so the string itself is the contract.
     */
    private fun wording(outcome: WearSystemInfoReportOutcome): Int = when (outcome) {
        is WearSystemInfoReportOutcome.Delivered -> R.string.system_info_send_sent
        is WearSystemInfoReportOutcome.NoConnectedPhone -> R.string.system_info_send_no_phone
        is WearSystemInfoReportOutcome.PhoneDidNotAnswer -> R.string.system_info_send_no_answer
        is WearSystemInfoReportOutcome.PhoneRefused ->
            if (outcome.reason == WearLogReportRefusalReasons.NOTIFICATIONS_DISABLED) {
                R.string.system_info_send_notifications_off
            } else {
                R.string.system_info_send_refused
            }
    }

    private fun read() {
        viewModelScope.launch {
            val sections = gatherWearSystemInfo()
            state.update { shown ->
                shown.copy(loading = false, refreshing = false, sections = sections)
            }
        }
    }
}
