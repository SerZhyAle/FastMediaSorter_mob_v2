package com.sza.fastmediasorter.wear.ui.apps.clipboard

import androidx.annotation.StringRes
import com.sza.fastmediasorter.wear.ui.common.OpenInUiState

/**
 * What the clipboard screen draws.
 *
 * [preview] is the shortened clipboard, never the whole of it: a watch screen cannot show a long text
 * and the wearer only needs enough to recognise what is about to be sent.
 *
 * [outcomeRes] survives the send it describes and is cleared only by the next send (S3109): the watch
 * has no Snackbar host, so the answer stays on screen as a line of text until it is replaced.
 * [outcomeArg] carries the phone's refusal reason for the one wording that names it.
 *
 * S4227: [server] is the second action of the screen - the clipboard on the exchange server - kept apart so
 * the two outcomes never overwrite each other.
 *
 * S4381: [openIn] is the "Open in.." dialog (null while closed); [openInFailure] names the application that could
 * not take the object and stays under the chip that started it until the next try.
 */
data class ClipboardUiState(
    val preview: String = "",
    val hasText: Boolean = false,
    val sending: Boolean = false,
    @StringRes val outcomeRes: Int? = null,
    val outcomeArg: String? = null,
    val server: ServerClipboardUiState = ServerClipboardUiState(),
    val openIn: OpenInUiState? = null,
    val openInFailure: OpenInFailure? = null,
)

/** S4381: the two objects "Open in.." can hand over, one per chip. */
enum class OpenInSource { CLIPBOARD, SERVER }

/** S4381: the application the wearer picked and the chip it was picked from. */
data class OpenInFailure(val source: OpenInSource, val appLabel: String)

/**
 * S4227: "get clipboard from server". [stateWhen] and [stateFrom] describe the value on the server (contract
 * DEVICE-EXCHANGE 22 item BY); [showDetailsLine] adds the fixed "details on the phone" line of 25 item DA under
 * a failure; [skipped] names the servers that did not answer (22 item CA).
 */
data class ServerClipboardUiState(
    val available: Boolean = false,
    val stateWhen: String? = null,
    val stateFrom: String? = null,
    val receiving: Boolean = false,
    val progressPercent: Int = 0,
    @StringRes val outcomeRes: Int? = null,
    val outcomeArg: String? = null,
    val showDetailsLine: Boolean = false,
    val skipped: String? = null,
    /** S4381: a value was received on this visit and can be handed to another application. */
    val canOpenIn: Boolean = false,
)
