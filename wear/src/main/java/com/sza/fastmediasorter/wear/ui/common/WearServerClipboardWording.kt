package com.sza.fastmediasorter.wear.ui.common

import androidx.annotation.StringRes
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearExchangeMessage
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardFailure

/**
 * S4227: the one failure-to-text map for the clipboard on the server. A server's refusal shows exactly the words
 * of contract DEVICE-EXCHANGE 25 item CZ, so two programs show the same failure identically; the code itself is
 * never the text (item DA).
 */
@StringRes
fun WearServerClipboardFailure.messageRes(): Int = MESSAGES[this] ?: WearExchangeMessage.REFUSED.messageRes()

// A table rather than a when: one row per failure, and an unmapped one falls back to the generic refusal (7.10).
private val MESSAGES: Map<WearServerClipboardFailure, Int> = mapOf(
    WearServerClipboardFailure.NOT_PERMITTED to WearExchangeMessage.NOT_PERMITTED.messageRes(),
    WearServerClipboardFailure.LIMIT_REACHED to WearExchangeMessage.LIMIT_REACHED_CLIPBOARD.messageRes(),
    WearServerClipboardFailure.CAPACITY to WearExchangeMessage.CAPACITY.messageRes(),
    WearServerClipboardFailure.UNAVAILABLE to WearExchangeMessage.UNAVAILABLE.messageRes(),
    WearServerClipboardFailure.DEVICE_REVOKED to WearExchangeMessage.DEVICE_REVOKED.messageRes(),
    WearServerClipboardFailure.SIGNED_OUT to WearExchangeMessage.SIGNED_OUT.messageRes(),
    WearServerClipboardFailure.BAD_CREDENTIALS to WearExchangeMessage.BAD_CREDENTIALS.messageRes(),
    WearServerClipboardFailure.VERSION_UNSUPPORTED to WearExchangeMessage.VERSION_UNSUPPORTED.messageRes(),
    WearServerClipboardFailure.UNREACHABLE to WearExchangeMessage.UNREACHABLE_SERVER.messageRes(),
    WearServerClipboardFailure.UNANSWERED to WearExchangeMessage.UNANSWERED.messageRes(),
    WearServerClipboardFailure.TLS_FAILED to WearExchangeMessage.TLS_FAILED.messageRes(),
    WearServerClipboardFailure.CERTIFICATE_CHANGED to WearExchangeMessage.CERTIFICATE_CHANGED.messageRes(),
    WearServerClipboardFailure.NOT_ENROLLED to R.string.wear_server_clipboard_not_enrolled,
    WearServerClipboardFailure.NO_CLIPBOARD_FEATURE to R.string.wear_server_clipboard_no_feature,
    WearServerClipboardFailure.TRANSFER_BROKEN to R.string.wear_server_clipboard_broken,
    WearServerClipboardFailure.HASH_MISMATCH to R.string.wear_server_clipboard_broken,
    WearServerClipboardFailure.SOURCE_UNREADABLE to R.string.wear_server_clipboard_unreadable,
    WearServerClipboardFailure.SAVE_FAILED to R.string.wear_server_clipboard_save_failed,
    WearServerClipboardFailure.NO_NETWORK to R.string.wear_server_clipboard_no_network,
    WearServerClipboardFailure.NO_ENTRY to R.string.wear_server_clipboard_no_feature,
    WearServerClipboardFailure.NO_SEALED_FEATURE to WearExchangeMessage.SERVER_NO_E2E.messageRes(),
    WearServerClipboardFailure.E2E_MISSING to WearExchangeMessage.E2E_MISSING.messageRes(),
    WearServerClipboardFailure.SEALED_FAILED to WearExchangeMessage.SEALED_FAILED.messageRes(),
    WearServerClipboardFailure.OTHER_REFUSAL to WearExchangeMessage.REFUSED.messageRes(),
)

/** Contract 25 item DA: the watch shows what happened and this one fixed line; the advice is on the phone. */
@StringRes
val SERVER_CLIPBOARD_DETAILS_RES: Int = EXCHANGE_DETAILS_ON_PHONE_RES
