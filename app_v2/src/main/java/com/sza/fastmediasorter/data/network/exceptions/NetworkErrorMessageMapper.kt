package com.sza.fastmediasorter.data.network.exceptions

import androidx.annotation.StringRes
import com.sza.fastmediasorter.R

/**
 * Maps [NetworkException] subtypes to user-facing string resource IDs.
 * Use from Fragment/Activity via context.getString(NetworkErrorMessageMapper.toMessageRes(e)).
 */
object NetworkErrorMessageMapper {

    @StringRes
    fun toMessageRes(exception: NetworkException): Int = when (exception) {
        is NetworkRateLimitException -> R.string.error_network_rate_limit
        is NetworkServerErrorException -> R.string.error_network_server_error
        is NetworkTimeoutException -> R.string.error_network_timeout
        is NetworkAccessDeniedException -> R.string.error_network_access_denied
        is NetworkFileNotFoundException -> R.string.error_network_not_found
        is NetworkConnectionLostException -> R.string.error_network_connection_lost
        is NetworkUnsupportedOperationException -> R.string.error_network_unsupported
    }

    @StringRes
    fun toMessageRes(throwable: Throwable): Int =
        toMessageRes(NetworkErrorClassifier.classify(throwable))
}
