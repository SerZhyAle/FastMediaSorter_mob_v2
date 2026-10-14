package com.sza.fastmediasorter.wear.ui.apps.clipboard

import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardEntryFailure
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardFailure
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardGetResult
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardReceiveReport
import com.sza.fastmediasorter.wear.ui.common.messageRes

/**
 * S4227: turns how "get clipboard from server" ended into the line under its chip. Every outcome is named
 * (strategic goal 4): received, saved but not shown, empty, no space, no suitable server, no network, and a
 * failure in the words of contract DEVICE-EXCHANGE 25 item CZ followed by the fixed watch line of item DA.
 */
internal object ServerClipboardResultMapper {

    fun map(
        result: WearServerClipboardGetResult,
        shown: ServerClipboardUiState,
        formatSize: (Long) -> String,
    ): ServerClipboardUiState {
        val cleared = shown.copy(outcomeArg = null, showDetailsLine = false, skipped = null)
        return when (val report = result.report) {
            is WearServerClipboardReceiveReport.Received -> cleared.copy(
                outcomeRes = if (result.openRequest != null) {
                    R.string.wear_server_clipboard_received
                } else {
                    R.string.wear_server_clipboard_saved_not_opened
                },
                outcomeArg = report.saved.displayName,
                skipped = labels(report.skipped),
            )
            is WearServerClipboardReceiveReport.Empty -> cleared.copy(
                outcomeRes = R.string.wear_server_clipboard_empty,
                skipped = labels(report.skipped),
            )
            is WearServerClipboardReceiveReport.Declined -> if (result.refusedForSpace) {
                cleared.copy(
                    outcomeRes = R.string.wear_server_clipboard_no_space,
                    outcomeArg = formatSize(report.value.size),
                )
            } else {
                failed(cleared, WearServerClipboardFailure.SAVE_FAILED)
            }
            WearServerClipboardReceiveReport.NoEntry ->
                cleared.copy(outcomeRes = R.string.wear_server_clipboard_get_unavailable)
            WearServerClipboardReceiveReport.NoNetwork -> failed(cleared, WearServerClipboardFailure.NO_NETWORK)
            is WearServerClipboardReceiveReport.Failed -> failed(
                cleared.copy(skipped = labels(report.failures.drop(1))),
                report.failures.firstOrNull()?.failure ?: WearServerClipboardFailure.OTHER_REFUSAL,
            )
        }
    }

    private fun failed(shown: ServerClipboardUiState, failure: WearServerClipboardFailure) =
        shown.copy(outcomeRes = failure.messageRes(), showDetailsLine = true)

    private fun labels(failures: List<WearServerClipboardEntryFailure>): String? =
        failures.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.label }
}
