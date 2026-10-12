package com.sza.fastmediasorter.ui.exchange.helpers

import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.DeviceExchangeRefusal

/**
 * The user-facing text of a contract DEVICE-EXCHANGE 7.10 refusal reason. A reason this build does not know
 * shows the generic refusal (strategic 3.3, communication policy), never the raw token.
 */
object ExchangeRefusalText {

    @StringRes
    fun of(reason: String): Int = when (DeviceExchangeRefusal.of(reason)) {
        DeviceExchangeRefusal.BAD_CREDENTIALS -> R.string.exchange_devices_reason_bad_credentials
        DeviceExchangeRefusal.DEVICE_REVOKED -> R.string.exchange_devices_reason_device_revoked
        DeviceExchangeRefusal.VERSION_UNSUPPORTED -> R.string.exchange_devices_reason_version
        DeviceExchangeRefusal.CAPACITY -> R.string.exchange_devices_reason_capacity
        DeviceExchangeRefusal.RATE_LIMITED -> R.string.exchange_devices_reason_rate_limited
        DeviceExchangeRefusal.UNSUPPORTED -> R.string.exchange_devices_reason_unsupported
        DeviceExchangeRefusal.TOKEN_NOT_STORED -> R.string.exchange_devices_reason_token_not_stored
        DeviceExchangeRefusal.OTHER -> R.string.exchange_devices_reason_generic
    }
}
