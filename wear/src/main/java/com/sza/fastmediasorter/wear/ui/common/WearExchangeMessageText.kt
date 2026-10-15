package com.sza.fastmediasorter.wear.ui.common

import androidx.annotation.StringRes
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearExchangeMessage

/**
 * The words of one row of contract DEVICE-EXCHANGE 25 item CZ, exactly as the table has them in the language
 * shown. Every watch surface that shows a failed exchange connection takes its words from here, so two surfaces
 * showing one failure show the same text.
 */
@StringRes
fun WearExchangeMessage.messageRes(): Int = checkNotNull(MESSAGE_RES[this]) { "no words for $this" }

// A table rather than a when: one row per message, and a unit test holds it to every entry of the enum.
private val MESSAGE_RES: Map<WearExchangeMessage, Int> = mapOf(
    WearExchangeMessage.BAD_CREDENTIALS to R.string.wear_exchange_reason_bad_credentials,
    WearExchangeMessage.DEVICE_REVOKED to R.string.wear_exchange_reason_device_revoked,
    WearExchangeMessage.SIGNED_OUT to R.string.wear_exchange_reason_signed_out,
    WearExchangeMessage.VERSION_UNSUPPORTED to R.string.wear_exchange_reason_version_unsupported,
    WearExchangeMessage.TLS_REQUIRED to R.string.wear_exchange_reason_tls_required,
    WearExchangeMessage.ID_COLLISION to R.string.wear_exchange_reason_id_collision,
    WearExchangeMessage.CAPACITY to R.string.wear_exchange_reason_capacity,
    WearExchangeMessage.PORT_UNAVAILABLE to R.string.wear_exchange_reason_port_unavailable,
    WearExchangeMessage.UNAVAILABLE to R.string.wear_exchange_reason_unavailable,
    WearExchangeMessage.RATE_LIMITED to R.string.wear_exchange_reason_rate_limited,
    WearExchangeMessage.PROTECTION_UNSUPPORTED to R.string.wear_exchange_reason_protection_unsupported,
    WearExchangeMessage.LIMIT_REACHED to R.string.wear_exchange_reason_limit_reached,
    WearExchangeMessage.LIMIT_REACHED_CLIPBOARD to R.string.wear_exchange_reason_limit_clipboard,
    WearExchangeMessage.NOT_PERMITTED to R.string.wear_exchange_reason_not_permitted,
    WearExchangeMessage.UNREACHABLE_SERVER to R.string.wear_exchange_reason_unreachable,
    WearExchangeMessage.UNREACHABLE_DEVICE to R.string.wear_exchange_reason_unreachable_device,
    WearExchangeMessage.UNANSWERED to R.string.wear_exchange_reason_unanswered,
    WearExchangeMessage.TLS_FAILED to R.string.wear_exchange_reason_tls_failed,
    WearExchangeMessage.CERTIFICATE_CHANGED to R.string.wear_exchange_reason_certificate_changed,
    WearExchangeMessage.SERVER_OFFLINE to R.string.wear_exchange_reason_server_offline,
    WearExchangeMessage.RESOURCE_LOCKED to R.string.wear_exchange_reason_resource_locked,
    WearExchangeMessage.E2E_MISSING to R.string.wear_exchange_reason_e2e_missing,
    WearExchangeMessage.SERVER_NO_E2E to R.string.wear_exchange_reason_server_no_e2e,
    WearExchangeMessage.SEALED_FAILED to R.string.wear_exchange_reason_sealed_failed,
    WearExchangeMessage.REFUSED to R.string.wear_exchange_reason_refused,
)

/** Contract 25 item DA: the watch shows what happened and this one fixed line; the advice is on the phone. */
@StringRes
val EXCHANGE_DETAILS_ON_PHONE_RES: Int = R.string.wear_exchange_details_on_phone
