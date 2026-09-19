package com.sza.fastmediasorter.wear.ui.apps.clipboard

import androidx.annotation.StringRes

/**
 * What the clipboard screen draws.
 *
 * [preview] is the shortened clipboard, never the whole of it: a watch screen cannot show a long text
 * and the wearer only needs enough to recognise what is about to be sent.
 *
 * [outcomeRes] survives the send it describes and is cleared only by the next send (S3109): the watch
 * has no Snackbar host, so the answer stays on screen as a line of text until it is replaced.
 * [outcomeArg] carries the phone's refusal reason for the one wording that names it.
 */
data class ClipboardUiState(
    val preview: String = "",
    val hasText: Boolean = false,
    val sending: Boolean = false,
    @StringRes val outcomeRes: Int? = null,
    val outcomeArg: String? = null
)
