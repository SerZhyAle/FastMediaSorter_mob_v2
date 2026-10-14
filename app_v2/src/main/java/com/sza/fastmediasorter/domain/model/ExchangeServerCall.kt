package com.sza.fastmediasorter.domain.model

/** How one request to an entry's server ended, for the "My devices" screen. */
sealed interface ExchangeServerCall<out T> {

    data class Done<T>(val value: T) : ExchangeServerCall<T>

    /** [reason] is a contract 7.10 reason, or `not-enrolled` when the entry holds no token. */
    data class Refused(val reason: String) : ExchangeServerCall<Nothing>

    data object Unreachable : ExchangeServerCall<Nothing>
}
