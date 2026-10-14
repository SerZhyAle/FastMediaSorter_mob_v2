package com.sza.fastmediasorter.wear.ui.common

import androidx.annotation.StringRes
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearResourceSendFailure

/**
 * S4230: the one failure-to-text map for a send to a resource of the watch. A table rather than a `when`, in the
 * shape of the clipboard's: one row per failure, and an unmapped one falls back to the generic "not sent" line.
 */
@StringRes
fun WearResourceSendFailure.messageRes(): Int = RESOURCE_SEND_MESSAGES[this] ?: R.string.wear_resource_send_not_sent

private val RESOURCE_SEND_MESSAGES: Map<WearResourceSendFailure, Int> = mapOf(
    WearResourceSendFailure.NO_NETWORK to R.string.wear_resource_send_no_network,
    WearResourceSendFailure.UNREACHABLE to R.string.wear_resource_send_unreachable,
    WearResourceSendFailure.REFUSED to R.string.wear_resource_send_refused,
    WearResourceSendFailure.NO_SPACE to R.string.wear_resource_send_no_space,
    WearResourceSendFailure.SOURCE_UNREADABLE to R.string.wear_resource_send_unreadable,
    WearResourceSendFailure.PIN_NEEDED to R.string.wear_resource_send_pin_on_phone,
    WearResourceSendFailure.NOT_CONNECTED to R.string.wear_resource_send_not_connected,
    WearResourceSendFailure.BROKEN to R.string.wear_resource_send_broken,
)
